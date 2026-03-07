from __future__ import annotations

import asyncio
import logging
import sys
from concurrent import futures

import grpc

import library_pb2
import library_pb2_grpc
from app_service import LibraryBackendService
from config import get_settings, validate_runtime_settings


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# uvloop은 Linux/macOS 전용 (Windows 미지원).
# Docker(Linux) 컨테이너 환경에서는 uvloop을 설치해 asyncio child_watcher
# NotImplementedError를 우회한다. Windows 개발 환경에서는 건너뜀.
if sys.platform != "win32":
    try:
        import uvloop
        uvloop.install()
        logger.info("uvloop installed as event loop policy")
    except ImportError:
        logger.info("uvloop not available, using default asyncio event loop")

settings = get_settings()
_loop = asyncio.new_event_loop()
asyncio.set_event_loop(_loop)
service = LibraryBackendService(settings)


def _run(coro, timeout_override=None):
    """Schedule a coroutine on the persistent event loop and wait for result."""
    future = asyncio.run_coroutine_threadsafe(coro, _loop)
    if timeout_override is not None:
        hard_timeout_sec = timeout_override
    else:
        hard_timeout_sec = max(6, int(settings.playwright_hard_timeout_ms / 1000) + 1)
    return future.result(timeout=hard_timeout_sec)


class LibraryServicer(library_pb2_grpc.LibraryServiceServicer):
    def CheckLibrary(self, request, context):
        return self._check_library(request)

    def GetLibraryStatus(self, request, context):
        return self._check_library(request)

    def GetBookInfo(self, request, context):
        result = _run(service.get_book_info_by_isbn13(request.isbn13))
        return library_pb2.BookInfoResponse(
            isbn13=result["isbn13"],
            availability_status=result["status"],
            image_url=result["image_url"],
            image_source=result["image_source"],
            error_message=result["error_message"],
            checked_at=result["checked_at"],
        )

    def GetEbookStatus(self, request, context):
        ebook_timeout = max(50, settings.ebook_total_timeout_sec + 10)
        result = _run(service.check_ebook(request.title, request.author, request.publisher), timeout_override=ebook_timeout)
        return library_pb2.EbookResponse(
            title=result["title"],
            found=result["found"],
            total_holdings=result["total_holdings"],
            available_holdings=result["available_holdings"],
            deep_link_url=result["deep_link_url"],
            status_text=result["status_text"],
            error_message=result["error_message"],
            checked_at=result["checked_at"],
        )

    def HealthCheck(self, request, context):
        return library_pb2.HealthCheckResponse(status="healthy", version="2.0.0")

    def SearchBooks(self, request, context):
        books = _run(service.search_books(query=request.query, page=request.page or 1, size=request.size or 20))

        return library_pb2.BookResponse(
            books=[self._to_book_item(book) for book in books],
            error_message="",
        )

    def _check_library(self, request):
        try:
            result = _run(service.check_library(isbn=request.isbn or None, title=request.title or None, author=request.author or None))
        except Exception as e:
            logger.exception("grpc_check_library_error isbn=%s title=%s", request.isbn, request.title)
            return library_pb2.LibraryResponse(
                found=False,
                available=False,
                location="",
                call_number="",
                detail_url="",
                error_message=f"server_error: {type(e).__name__}",
                loan_status_text="UNKNOWN",
                detail_verified=False,
                checked_at="",
                is_owned=False,
                status_text="UNKNOWN",
                retry_required=True,
                overall_status="\uc815\ubcf4\uc5c6\uc74c",
                record_type_picked="",
                matched_title="",
                debug_reason="grpc_exception",
            )
        return library_pb2.LibraryResponse(
            found=result["found"],
            available=result["available"],
            location=result["location"],
            call_number=result["call_number"],
            detail_url=result["detail_url"],
            error_message=result["error_message"],
            loan_status_text=result["loan_status_text"],
            detail_verified=result["detail_verified"],
            checked_at=result["checked_at"],
            is_owned=result["is_owned"],
            status_text=result["status_text"],
            retry_required=result["retry_required"],
            overall_status=result.get("overall_status", ""),
            record_type_picked=result.get("record_type_picked") or "",
            matched_title=result.get("matched_title", ""),
            debug_reason=result.get("evidence", {}).get("aggregate_reason", ""),
        )

    @staticmethod
    def _to_book_item(book):
        holding = book["holding"]
        return library_pb2.BookItem(
            title=book["title"],
            author=book["author"],
            isbn=book["isbn"],
            price=book["price"],
            kakao_thumbnail_url=book["kakao_thumbnail_url"],
            image_url=book["image_url"],
            image_proxy_url=book["image_proxy_url"],
            holding=library_pb2.PreciseHoldingStatus(
                checked=holding["checked"],
                found=holding["found"],
                available=holding["available"],
                location=holding["location"],
                call_number=holding["call_number"],
                detail_url=holding["detail_url"],
                status_text=holding["status_text"],
                verification_source=holding["verification_source"],
                checked_at=holding["checked_at"],
            ),
        )


def serve() -> None:
    validate_runtime_settings(settings)

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    library_pb2_grpc.add_LibraryServiceServicer_to_server(LibraryServicer(), server)
    server.add_insecure_port(f"{settings.grpc_host}:{settings.grpc_port}")

    logger.info("gRPC server started on %s:%s", settings.grpc_host, settings.grpc_port)
    server.start()

    try:
        _loop.run_forever()
    except KeyboardInterrupt:
        logger.info("gRPC server stopping...")
    finally:
        server.stop(grace=3)
        _loop.stop()


if __name__ == "__main__":
    serve()
