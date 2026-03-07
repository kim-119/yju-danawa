from __future__ import annotations

import asyncio
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from config import Settings
from ebook_scraper import EbookScraper
from image_service import BookImageService
from scraper_service import LibraryScraper


class LibraryBackendService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.scraper = LibraryScraper(settings)
        self.ebook_scraper = EbookScraper(settings)
        self.image_service = BookImageService(settings)

    async def check_library(self, isbn: Optional[str], title: Optional[str], author: Optional[str]) -> Dict[str, Any]:
        try:
            return await self.scraper.check_library(isbn=isbn, title=title, author=author)
        except Exception:
            # scraper 내부에서 처리되지 않은 예외가 발생해도 서버가 죽지 않도록 기본값 반환
            return {
                "found": False,
                "available": False,
                "location": "",
                "call_number": "",
                "detail_url": "",
                "error_message": "scraper_unexpected_error",
                "loan_status_text": "UNKNOWN",
                "detail_verified": False,
                "checked_at": datetime.now(timezone.utc).isoformat(),
                "is_owned": False,
                "status_text": "UNKNOWN",
                "status_text_ko": "정보 없음",
                "overall_status": "정보없음",
                "record_type_picked": None,
                "matched_title": "",
                "retry_required": True,
            }

    async def resolve_book_image(self, isbn: Optional[str], title: Optional[str], author: Optional[str]) -> Dict[str, str]:
        image_url, source = await self.image_service.resolve_image_url(isbn=isbn, title=title, author=author)
        return {
            "image_url": image_url,
            "image_source": source,
        }

    async def check_ebook(self, title: Optional[str], author: Optional[str], publisher: Optional[str]) -> Dict[str, Any]:
        safe_title = title or ""
        normalized_title = self.ebook_scraper.normalize_search_title(safe_title)
        timeout_sec = max(60, self.settings.ebook_total_timeout_sec)
        task = asyncio.create_task(
            self.ebook_scraper.check_ebook_by_title(safe_title, author or "", publisher or "")
        )
        done, _pending = await asyncio.wait({task}, timeout=timeout_sec)
        if task in done:
            try:
                return task.result()
            except Exception:
                pass
        task.cancel()
        try:
            await asyncio.sleep(0)
        except Exception:
            pass
        return {
            "title": normalized_title or safe_title,
            "found": False,
            "total_holdings": 0,
            "available_holdings": 0,
            "deep_link_url": self.ebook_scraper.build_search_deep_link(safe_title),
            "status_text": "미소장",
            "error_message": "ebook_total_timeout",
            "checked_at": datetime.now(timezone.utc).isoformat(),
        }

    async def get_book_info_by_isbn13(self, isbn13: str) -> Dict[str, Any]:
        normalized = self._normalize_isbn13(isbn13)
        if not normalized:
            return {
                "isbn13": "",
                "status": "UNAVAILABLE",
                "image_url": self.settings.placeholder_image_url,
                "image_source": "PLACEHOLDER",
                "error_message": "isbn13 must be exactly 13 digits",
                "checked_at": "",
            }

        holding_task = self.check_library(isbn=normalized, title=None, author=None)
        image_task = self.resolve_book_image(isbn=normalized, title=None, author=None)
        holding, image = await asyncio.gather(holding_task, image_task)

        status = "AVAILABLE" if bool(holding.get("available")) else "UNAVAILABLE"
        return {
            "isbn13": normalized,
            "status": status,
            "image_url": image["image_url"],
            "image_source": image["image_source"],
            "error_message": holding.get("error_message", ""),
            "checked_at": holding.get("checked_at", ""),
        }

    async def search_books(self, query: str, page: int, size: int) -> List[Dict[str, Any]]:
        if not query or not query.strip():
            return []

        query = query.strip()
        isbn13 = self._normalize_isbn13(query)

        if isbn13:
            holding_task = self.check_library(isbn=isbn13, title=None, author=None)
            image_task = self.resolve_book_image(isbn=isbn13, title=None, author=None)
            holding, image = await asyncio.gather(holding_task, image_task)
        else:
            holding = {
                "detail_verified": False,
                "found": False,
                "available": False,
                "location": "",
                "call_number": "",
                "detail_url": "",
                "status_text": "UNKNOWN",
                "checked_at": "",
            }
            image = {
                "image_url": self.settings.placeholder_image_url,
                "image_source": "PLACEHOLDER",
            }

        # Minimal interoperable payload for current Spring Boot BookResponse mapping.
        return [
            {
                "title": query,
                "author": "",
                "isbn": isbn13 or "",
                "price": 0,
                "kakao_thumbnail_url": image["image_url"] if image["image_source"] == "KAKAO" else "",
                "image_url": image["image_url"],
                "image_proxy_url": image["image_url"],
                "holding": {
                    "checked": holding["detail_verified"],
                    "found": holding["found"],
                    "available": holding["available"],
                    "location": holding["location"],
                    "call_number": holding["call_number"],
                    "detail_url": holding["detail_url"],
                    "status_text": holding["status_text"],
                    "verification_source": "PLAYWRIGHT",
                    "checked_at": holding["checked_at"],
                },
            }
        ][: max(1, min(size, 50))]

    @staticmethod
    def _extract_isbn(text: str) -> Optional[str]:
        normalized = re.sub(r"[^0-9Xx]", "", text)
        if len(normalized) in (10, 13):
            return normalized.upper()
        return None

    @staticmethod
    def _normalize_isbn13(text: Optional[str]) -> str:
        if not text:
            return ""
        normalized = re.sub(r"[^0-9]", "", text)
        if len(normalized) == 13 and normalized.startswith(("978", "979")):
            return normalized
        return ""


