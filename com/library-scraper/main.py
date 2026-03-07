from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import Optional, List

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app_service import LibraryBackendService
from config import get_settings, validate_runtime_settings
from library_search_engine import LibrarySessionCrawler
from notice_crawler import NoticeCrawler, notice_to_dict


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

settings = get_settings()
service = LibraryBackendService(settings)
crawler = LibrarySessionCrawler()

# 공지사항 크롤러 + 캐시
_notice_crawler = NoticeCrawler(headless=True, max_pages=2)
_notice_cache: List[dict] = []
_notice_lock = asyncio.Lock()


async def _refresh_notices():
    """공지사항 크롤링 후 캐시 갱신."""
    global _notice_cache
    try:
        logger.info("공지사항 배너 크롤링 시작...")
        items = await _notice_crawler.crawl()
        _notice_cache = [notice_to_dict(n) for n in items]
        logger.info("공지사항 배너 크롤링 완료: %d건", len(_notice_cache))
    except Exception as e:
        logger.error("공지사항 배너 크롤링 실패: %s", e)


async def _notice_scheduler():
    """4시간 간격으로 공지사항 크롤링."""
    while True:
        async with _notice_lock:
            await _refresh_notices()
        await asyncio.sleep(4 * 3600)  # 4시간


@asynccontextmanager
async def lifespan(app: FastAPI):
    await crawler.start()
    app.state.library_crawler = crawler
    # 공지사항 크롤러 백그라운드 태스크 시작
    notice_task = asyncio.create_task(_notice_scheduler())
    yield
    notice_task.cancel()
    await _notice_crawler.close()
    await crawler.close()

app = FastAPI(title="Y-Danawa Library Scraper API", version="2.0.0", lifespan=lifespan)


class LibraryCheckRequest(BaseModel):
    isbn: Optional[str] = None
    title: Optional[str] = None
    author: Optional[str] = None


class ImageResolveRequest(BaseModel):
    isbn: Optional[str] = None
    title: Optional[str] = None
    author: Optional[str] = None

class BookInfoRequest(BaseModel):
    isbn13: str

class EbookRequest(BaseModel):
    title: str
    author: Optional[str] = None
    publisher: Optional[str] = None


@app.get("/")
async def root():
    return {"status": "ok", "service": "library-scraper", "version": "2.0.0"}


@app.get("/health")
async def health():
    return {"status": "healthy", "grpc_port": settings.grpc_port}


@app.post("/check-library")
async def check_library(request: LibraryCheckRequest):
    if not request.isbn and not request.title:
        raise HTTPException(status_code=400, detail="isbn or title is required")

    result = await service.check_library(isbn=request.isbn, title=request.title, author=request.author)
    return result


@app.post("/resolve-image")
async def resolve_image(request: ImageResolveRequest):
    if not request.isbn and not request.title:
        raise HTTPException(status_code=400, detail="isbn or title is required")

    return await service.resolve_book_image(isbn=request.isbn, title=request.title, author=request.author)


@app.get("/books/search")
async def search_books(query: str, page: int = 1, size: int = 20):
    return {"books": await service.search_books(query=query, page=page, size=size)}

@app.post("/book-info")
async def book_info(request: BookInfoRequest):
    return await service.get_book_info_by_isbn13(request.isbn13)

@app.get("/book-info")
async def book_info_query(isbn13: str):
    return await service.get_book_info_by_isbn13(isbn13)

@app.get("/books/info")
async def book_info_legacy(isbn13: str):
    return await service.get_book_info_by_isbn13(isbn13)

@app.post("/check-ebook")
async def check_ebook(request: EbookRequest):
    if not request.title or not request.title.strip():
        raise HTTPException(status_code=400, detail="title is required")
    return await service.check_ebook(request.title, request.author, request.publisher)


@app.get("/library/search")
async def search_library(title: str):
    if not title or not title.strip():
        raise HTTPException(status_code=400, detail="title is required")
    try:
        return await asyncio.wait_for(app.state.library_crawler.search(title), timeout=12)
    except asyncio.TimeoutError:
        return {
            "query_title_raw": title,
            "query_title_clean": "",
            "matched_title": "",
            "material_type": "unknown",
            "status": "\uc815\ubcf4 \uc5c6\uc74c/\ubbf8\uc18c\uc7a5",
            "holding_count": 0,
            "reason": "search_timeout_12s",
            "error_type": "timeout",
        }
    except Exception:
        return {
            "query_title_raw": title,
            "query_title_clean": "",
            "matched_title": "",
            "material_type": "unknown",
            "status": "\uc815\ubcf4 \uc5c6\uc74c/\ubbf8\uc18c\uc7a5",
            "holding_count": 0,
            "reason": "unexpected_error",
            "error_type": "error",
        }


@app.get("/proxy/image")
async def proxy_image(url: str):
    """외부 이미지 URL을 프록시하여 CORS 이슈를 우회한다."""
    import httpx
    if not url:
        raise HTTPException(status_code=400, detail="url is required")
    try:
        async with httpx.AsyncClient(timeout=10, follow_redirects=True) as client:
            resp = await client.get(url)
            from fastapi.responses import Response
            return Response(
                content=resp.content,
                media_type=resp.headers.get("content-type", "image/jpeg"),
                headers={"Cache-Control": "public, max-age=86400"},
            )
    except Exception:
        raise HTTPException(status_code=502, detail="image proxy failed")


# ── 공지사항 배너 API ──────────────────────────────────────
@app.get("/notices/banners")
async def get_notice_banners():
    """활성 공지사항 배너 목록 반환 (최대 10건)."""
    return _notice_cache[:10]


@app.post("/notices/refresh")
async def refresh_notices():
    """수동 공지사항 크롤링 트리거."""
    async with _notice_lock:
        await _refresh_notices()
    return {"status": "ok", "count": len(_notice_cache)}


if __name__ == "__main__":
    validate_runtime_settings(settings)
    uvicorn.run(app, host=settings.http_host, port=settings.http_port, log_level="info")
