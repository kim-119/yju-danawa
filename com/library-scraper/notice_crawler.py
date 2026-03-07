"""
영진전문대 도서관 공지사항 게시판 이미지 크롤러.

https://lib.yju.ac.kr/Cheetah/Board/List?LOC=YJCL&CATEGORYID=1
게시판 목록을 순회하며 본문 이미지를 수집한다.
"""
from __future__ import annotations

import asyncio
import logging
import re
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from typing import List, Optional
from urllib.parse import urljoin, urlparse, parse_qs

from playwright.async_api import async_playwright, Browser, Page

logger = logging.getLogger(__name__)

BOARD_LIST_URL = "https://lib.yju.ac.kr/Cheetah/Board/List?LOC=YJCL&CATEGORYID=1"
BASE_URL = "https://lib.yju.ac.kr"


@dataclass
class NoticeImage:
    """크롤링된 공지사항 이미지 한 건."""
    board_id: str
    title: str
    image_url: str
    link_url: str
    posted_date: Optional[str] = None
    crawled_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


def _extract_board_id(href: str) -> Optional[str]:
    """URL에서 BOARDID 파라미터를 추출한다."""
    try:
        parsed = urlparse(href)
        params = parse_qs(parsed.query)
        bid = params.get("BOARDID") or params.get("boardid")
        if bid:
            return bid[0]
        match = re.search(r"/Board/(?:Detail|View)/(\d+)", href, re.IGNORECASE)
        if match:
            return match.group(1)
    except Exception:
        pass
    return None


class NoticeCrawler:
    """Playwright 기반 도서관 공지사항 크롤러."""

    def __init__(self, headless: bool = True, max_pages: int = 2):
        self.headless = headless
        self.max_pages = max_pages
        self._browser: Optional[Browser] = None
        self._pw = None

    async def _ensure_browser(self):
        if self._browser is None or not self._browser.is_connected():
            self._pw = await async_playwright().start()
            self._browser = await self._pw.chromium.launch(
                headless=self.headless,
                args=["--no-sandbox", "--disable-setuid-sandbox"],
            )

    async def close(self):
        if self._browser:
            try:
                await self._browser.close()
            except Exception:
                pass
        if self._pw:
            try:
                await self._pw.stop()
            except Exception:
                pass

    async def crawl(self) -> List[NoticeImage]:
        """게시판에서 이미지가 포함된 공지사항을 수집한다."""
        await self._ensure_browser()
        context = await self._browser.new_context(
            user_agent=(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0 Safari/537.36"
            ),
            viewport={"width": 1280, "height": 900},
        )
        page = await context.new_page()
        results: List[NoticeImage] = []
        seen_ids: set[str] = set()

        try:
            logger.info("공지사항 목록 페이지 로드: %s", BOARD_LIST_URL)
            await page.goto(BOARD_LIST_URL, wait_until="networkidle", timeout=30000)
            await page.wait_for_timeout(3000)

            # ── 게시글 링크 추출: Board/View URL 패턴 ──
            post_links = await page.eval_on_selector_all(
                "a[href*='Board/View']",
                """els => els.map(e => ({
                    href: e.href,
                    text: e.textContent.trim()
                }))"""
            )
            logger.info("추출된 게시글 링크 수: %d", len(post_links))

            # 중복 제거 및 정리
            unique_posts = []
            for link in post_links:
                href = link.get("href", "")
                board_id = _extract_board_id(href)
                if not board_id or board_id in seen_ids:
                    continue
                seen_ids.add(board_id)

                raw_text = link.get("text", "").strip()
                # 텍스트에서 날짜와 제목 분리
                lines = [l.strip() for l in raw_text.split("\n") if l.strip()]
                title = lines[0] if lines else "제목 없음"
                # 첫 줄이 카테고리 태그(1~3글자)면 다음 줄을 제목으로 사용
                if len(title) <= 3 and len(lines) > 1:
                    title = lines[1]
                posted_date = None
                for line in lines:
                    if re.match(r"\d{4}-\d{2}(?:-\d{2})?", line):
                        posted_date = line
                        break

                unique_posts.append({
                    "href": href,
                    "board_id": board_id,
                    "title": title,
                    "posted_date": posted_date,
                })

            logger.info("고유 게시글 수: %d", len(unique_posts))

            # ── 각 상세 페이지에서 이미지 추출 ──
            for post in unique_posts:
                try:
                    images = await self._extract_detail_images(page, post["href"])
                except Exception as e:
                    logger.warning("상세 페이지 크롤링 실패: board_id=%s, %s", post["board_id"], e)
                    continue

                if not images:
                    continue

                # 첫 번째 이미지만 배너용으로 사용
                for img_url in images[:1]:
                    abs_img = img_url if img_url.startswith("http") else urljoin(BASE_URL, img_url)
                    results.append(NoticeImage(
                        board_id=post["board_id"],
                        title=post["title"],
                        image_url=abs_img,
                        link_url=post["href"],
                        posted_date=post["posted_date"],
                    ))

            logger.info("이미지가 포함된 공지사항 %d건 수집 완료", len(results))
        except Exception as e:
            logger.error("공지사항 크롤링 중 오류: %s", e)
        finally:
            await context.close()

        return results

    @staticmethod
    def _is_layout_image(src: str) -> bool:
        """네비게이션/레이아웃 이미지인지 판별."""
        lower = src.lower()
        layout_keywords = [
            "agency", "logo", "icon", "banner_bg",
            "gnb", "footer", "header", "nav", "btn_",
            "arrow", "close", "menu", "top_", "bg_",
            "2025/", "Content/images/",
        ]
        return any(kw in lower for kw in layout_keywords)

    async def _extract_detail_images(self, page: Page, url: str) -> List[str]:
        """상세 페이지에서 본문 이미지를 추출한다."""
        await page.goto(url, wait_until="domcontentloaded", timeout=20000)
        await page.wait_for_timeout(2000)

        # 본문 영역 셀렉터 후보
        content_selectors = [
            ".board_view_content",
            ".board_content",
            ".view_content",
            ".view-content",
            ".boardView .content",
            ".board_view .content",
            "#boardContent",
            ".ql-editor",
            ".board-view-body",
            ".content-body",
            "article",
            ".notice-view",
        ]

        images = []
        for sel in content_selectors:
            content_el = await page.query_selector(sel)
            if content_el:
                img_tags = await content_el.query_selector_all("img[src]")
                for img in img_tags:
                    src = await img.get_attribute("src")
                    if src and not src.startswith("data:") and "spacer" not in src.lower() and not self._is_layout_image(src):
                        images.append(src)
                if images:
                    break

        # 본문 셀렉터가 안 먹히면 전체 페이지에서 업로드 이미지만 탐색
        if not images:
            all_imgs = await page.query_selector_all("img[src]")
            for img in all_imgs:
                src = await img.get_attribute("src") or ""
                if (
                    src
                    and not src.startswith("data:")
                    and "logo" not in src.lower()
                    and "icon" not in src.lower()
                    and "spacer" not in src.lower()
                    and "button" not in src.lower()
                    and "favicon" not in src.lower()
                    and "gnb" not in src.lower()
                    and "footer" not in src.lower()
                    and ("upload" in src.lower() or "board" in src.lower()
                         or "attach" in src.lower() or "file" in src.lower()
                         or "image" in src.lower() or "img" in src.lower())
                    and not self._is_layout_image(src)
                ):
                    images.append(src)

        return images


def notice_to_dict(notice: NoticeImage) -> dict:
    return asdict(notice)

