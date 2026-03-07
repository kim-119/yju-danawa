from __future__ import annotations

import asyncio
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional
from urllib.parse import quote, quote_from_bytes

from dotenv import load_dotenv
from playwright.async_api import Browser, BrowserContext, Error, Page, Playwright, async_playwright

TIMEOUT_MS = 7000
ROW_SELECTORS = ["li.mb-4", ".bookinfo", "div.bookinfo", "table tbody tr", "ul > li"]
TITLE_SELECTORS = [".title", ".tit", "h3", "h4", "a[title]", "a", "strong"]
RESULT_CONTAINER_SELECTORS = [
    "ul.search-list",
    "ul.resultList",
    "ul.bookList",
    "div.searchResult",
    "div.result_list",
    "#search_result",
    "ul",
    "div",
]
SCOPED_ROW_SELECTORS = ["li", ".bookinfo", "div.bookinfo", "tr", "[role='row']"]

TXT_MONOGRAPH = "\uB2E8\uD589\uBCF8"  # 단행본
TXT_EBOOK = "\uC804\uC790\uCC45"        # 전자책
TXT_HOLDING = "\uBCF4\uC720"
TXT_AVAILABLE = "\uB300\uCD9C\uAC00\uB2A5"
TXT_ON_LOAN = "\uB300\uCD9C\uC911"
TXT_OWNED = "\uC18C\uC7A5"
TXT_MONOGRAPH_OWNED = "\uC720(\uC18C\uC7A5)"
TXT_NOT_OWNED = "\uBBF8\uC18C\uC7A5"
TXT_FALLBACK = "\uC815\uBCF4 \uC5C6\uC74C/\uBBF8\uC18C\uC7A5"

def load_library_env() -> None:
    base_dir = Path(__file__).resolve().parent
    for env_path, override in [(base_dir.parent / ".env", False), (base_dir / ".env", True)]:
        if not env_path.exists():
            continue
        for enc in ("utf-8-sig", "cp949"):
            try:
                load_dotenv(env_path, encoding=enc, override=override)
                break
            except UnicodeDecodeError:
                continue

def clean_title(raw: str) -> str:
    text = re.sub(r"\s+", " ", (raw or "").strip())
    if not text:
        return ""
    text = re.sub(r"\s*[-:(].*$", "", text).strip()
    text = re.sub(r"[^0-9A-Za-z\u3131-\u318E\uAC00-\uD7A3\s]", " ", text)
    return re.sub(r"\s+", " ", text).strip()

def normalize_text(raw: str) -> str:
    return re.sub(r"\s+", " ", (raw or "").strip()).lower()

def is_korean_char(ch: str) -> bool:
    return bool(re.match(r"[\u3131-\u318E\uAC00-\uD7A3]", ch))

def hybrid_encode_query(query: str) -> str:
    # lib.yju.ac.kr Knockout.js SPA router는 UTF-8 percent-encoding만 파싱한다.
    # CP949 인코딩(%C0%CC...)은 router가 인식 못해 결과가 렌더링되지 않는다.
    out: list[str] = []
    for ch in query:
        if ch == " ":
            out.append("%20")
            continue
        if ch.isascii() and (ch.isalnum() or ch in "-_.~"):
            out.append(ch)
            continue
        out.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
    return "".join(out)

def is_monograph_text(row_text: str) -> bool:
    compact = re.sub(r"\s+", "", row_text or "")
    return TXT_MONOGRAPH in compact

def is_ebook_text(row_text: str) -> bool:
    compact = re.sub(r"\s+", "", row_text or "")
    return TXT_EBOOK in compact

def parse_ebook_holding_count(row_text: str) -> int:
    patterns = [
        r"\ubcf4\uc720\s*[:\s]*(\d+)",
        r"\ubcf4\uc720\s*\(\s*(\d+)\s*\)",
        r"\ubcf4\uc720\s*(\d+)",
    ]
    raw = row_text or ""
    for p in patterns:
        m = re.search(p, raw)
        if m:
            return int(m.group(1))
    return 0

@dataclass
class SearchResult:
    query_title_raw: str
    query_title_clean: str
    matched_title: str
    material_type: str
    status: str
    holding_count: int
    reason: str
    error_type: str

    def to_dict(self) -> dict:
        return {
            "query_title_raw": self.query_title_raw,
            "query_title_clean": self.query_title_clean,
            "matched_title": self.matched_title,
            "material_type": self.material_type,
            "status": self.status,
            "holding_count": self.holding_count,
            "reason": self.reason,
            "error_type": self.error_type,
        }

class LibrarySessionCrawler:
    def __init__(self) -> None:
        load_library_env()
        self.library_id = os.getenv("LIBRARY_ID", "").strip()
        self.library_pw = os.getenv("LIBRARY_PW", "").strip()
        self.login_url = os.getenv("LIB_LOGIN_URL", "https://lib.yju.ac.kr/Cheetah/Login/Login").strip()
        self.search_prefix = os.getenv(
            "LIB_SEARCH_URL_PREFIX",
            "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/",
        ).strip()
        self.storage_state_path = os.getenv("LIB_STORAGE_STATE_PATH", "library_state.json").strip()

        self._pw: Optional[Playwright] = None
        self._browser: Optional[Browser] = None
        self._context: Optional[BrowserContext] = None
        self._lock = asyncio.Lock()
        self._login_lock = asyncio.Lock()
        self._logged_in = False

    async def start(self) -> None:
        async with self._lock:
            if self._context is not None:
                return
            self._pw = await async_playwright().start()
            self._browser = await self._pw.chromium.launch(headless=True, args=["--no-sandbox", "--disable-setuid-sandbox"])
            storage_state = self.storage_state_path if Path(self.storage_state_path).exists() else None
            self._context = await self._browser.new_context(storage_state=storage_state)
            self._logged_in = False

    async def close(self) -> None:
        async with self._lock:
            if self._context is not None:
                await self._context.close()
                self._context = None
            if self._browser is not None:
                await self._browser.close()
                self._browser = None
            if self._pw is not None:
                await self._pw.stop()
                self._pw = None
            self._logged_in = False

    async def _new_page(self) -> Page:
        if self._context is None:
            await self.start()
        if self._context is None:
            raise RuntimeError("browser_context_unavailable")
        page = await self._context.new_page()
        page.set_default_timeout(TIMEOUT_MS)
        page.set_default_navigation_timeout(TIMEOUT_MS)
        return page

    async def _find_first(self, page: Page, selectors_csv: str):
        selectors = [s.strip() for s in selectors_csv.split(",") if s.strip()]
        for sel in selectors:
            try:
                await page.wait_for_selector(sel, timeout=TIMEOUT_MS, state="attached")
                loc = page.locator(sel).first
                if await loc.count() > 0:
                    return loc
            except Error:
                continue
        return None

    async def ensure_login(self) -> tuple[bool, str]:
        if self._logged_in:
            return True, "already_logged_in"
        if not self.library_id or not self.library_pw:
            return False, "missing_library_credentials"

        async with self._login_lock:
            if self._logged_in:
                return True, "already_logged_in"

            page = await self._new_page()
            try:
                await page.goto(self.login_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
                id_input = await self._find_first(
                    page,
                    "#formText input[name='loginId'], #formText input[id='loginId'], input[name='id'], input[name='userId']",
                )
                pw_input = await self._find_first(
                    page,
                    "#formText input[name='loginpwd'], #formText input[id='loginpwd'], input[name='password'], input[type='password']",
                )
                submit_btn = await self._find_first(
                    page,
                    "#formText button[type='submit'], button[type='submit'], input[type='submit']",
                )
                if id_input is None or pw_input is None or submit_btn is None:
                    return False, "login_form_not_found"

                await id_input.fill(self.library_id, timeout=TIMEOUT_MS)
                await pw_input.fill(self.library_pw, timeout=TIMEOUT_MS)
                await submit_btn.click(timeout=TIMEOUT_MS)
                try:
                    await page.wait_for_selector(
                        "a[href*='Logout'], a[href*='logout'], a[href*='MyPage'], .myPage, .user, .log-out",
                        timeout=TIMEOUT_MS,
                        state="attached",
                    )
                except Error:
                    return False, "login_verify_failed"

                if self._context is not None:
                    await self._context.storage_state(path=self.storage_state_path)
                self._logged_in = True
                return True, "login_success"
            except Error:
                return False, "login_timeout_or_error"
            finally:
                await page.close()

    async def _extract_row_title(self, row) -> str:
        for sel in TITLE_SELECTORS:
            try:
                loc = row.locator(sel).first
                if await loc.count() <= 0:
                    continue
                text = re.sub(r"\s+", " ", (await loc.inner_text(timeout=TIMEOUT_MS)).strip())
                if text:
                    return text
            except Error:
                continue
        try:
            full = re.sub(r"\s+", " ", (await row.inner_text(timeout=TIMEOUT_MS)).strip())
            if not full:
                return ""
            return full.split("  ")[0].strip()
        except Error:
            return ""

    async def _collect_candidate_rows(self, page: Page) -> list:
        rows = []
        for container_sel in RESULT_CONTAINER_SELECTORS:
            container_loc = page.locator(container_sel)
            try:
                if await container_loc.count() <= 0:
                    continue
                containers = await container_loc.all()
            except Error:
                continue

            for container in containers[:10]:
                for row_sel in SCOPED_ROW_SELECTORS:
                    try:
                        found = await container.locator(row_sel).all()
                    except Error:
                        continue
                    if not found:
                        continue
                    rows.extend(found)
                    if len(rows) >= 80:
                        return rows[:80]
            if rows:
                return rows[:80]

        for sel in ROW_SELECTORS:
            try:
                found = await page.locator(sel).all()
            except Error:
                continue
            if found:
                rows.extend(found)
                return rows[:80]
        return rows

    async def _has_row_marker(self, row, keyword: str, icon_selectors: list[str]) -> bool:
        try:
            if await row.locator(f"text={keyword}").count() > 0:
                return True
        except Error:
            pass
        for sel in icon_selectors:
            try:
                if await row.locator(sel).count() > 0:
                    return True
            except Error:
                continue
        return False

    async def _wait_for_spa_results(self, page: Page, timeout_ms: int = 8000) -> bool:
        try:
            await page.wait_for_load_state("networkidle", timeout=min(5000, timeout_ms))
        except Exception:
            pass

        for sel in ROW_SELECTORS:
            try:
                await page.wait_for_selector(sel, state="attached", timeout=3000)
                if await page.locator(sel).count() > 0:
                    return True
            except Exception:
                continue

        import time as _time
        deadline = _time.monotonic() + timeout_ms / 1000
        while _time.monotonic() < deadline:
            for sel in ROW_SELECTORS:
                try:
                    if await page.locator(sel).count() > 0:
                        return True
                except Exception:
                    pass
            try:
                body = await page.evaluate("() => document.body.textContent")
                if re.search(r"소장자료\s*0", body) or re.search(r"전체\s*0\s*건", body):
                    return False
            except Exception:
                pass
            await asyncio.sleep(0.5)
        return False

    async def _wait_for_cno_status(self, page: Page, timeout_ms: int = 7000) -> bool:
        import time as _time
        deadline = _time.monotonic() + timeout_ms / 1000
        while _time.monotonic() < deadline:
            try:
                cno_count = await page.locator('[id^="cnoStatus-"]').count()
                if cno_count > 0:
                    first = page.locator('[id^="cnoStatus-"]').first
                    text = re.sub(r"\s+", "", await first.inner_text())
                    if text:
                        return True
            except Exception:
                pass
            await asyncio.sleep(0.3)
        return False

    async def _scan_rows_for_title(self, page: Page, norm_query: str) -> tuple[str, str, int, str]:
        rows_appeared = await self._wait_for_spa_results(page, timeout_ms=8000)
        if not rows_appeared:
            return "", "", 0, "no_rows"

        await self._wait_for_cno_status(page, timeout_ms=7000)

        candidates = await self._collect_candidate_rows(page)
        if not candidates:
            return "", "", 0, "no_rows"

        monograph_status = ""
        ebook_status = ""
        ebook_holding = 0
        matched_title = ""

        for row in candidates:
            try:
                row_title = await self._extract_row_title(row)
                row_clean = clean_title(row_title)

                # [수정된 부분] 100% 일치가 아닌 부분 일치(포함) 여부로 확인
                # '이펙티브 자바 :프로그래밍인사이트' 처럼 부제가 붙어도 통과합니다.
                if norm_query not in normalize_text(row_clean) and normalize_text(row_clean) not in norm_query:
                    continue

                row_text = re.sub(r"\s+", " ", (await row.inner_text(timeout=TIMEOUT_MS)).strip())
                if not row_text:
                    continue
                if not matched_title:
                    matched_title = row_title

                if not re.search(r"(대출가능|대출중|대출불가|예약)", row_text):
                    try:
                        cno_loc = row.locator('[id^="cnoStatus-"]')
                        cno_count = await cno_loc.count()
                        if cno_count > 0:
                            cno_text = re.sub(r"\s+", " ", (await cno_loc.first.inner_text()).strip())
                            if cno_text:
                                row_text = row_text + " " + cno_text
                    except Exception:
                        pass

                if not re.search(r"(대출가능|대출중|대출불가|예약)", row_text):
                    try:
                        avail_btn = await row.locator("text=대출가능").count()
                        if avail_btn > 0:
                            row_text = row_text + " 대출가능"
                    except Exception:
                        pass

                has_monograph = is_monograph_text(row_text) or await self._has_row_marker(
                    row,
                    TXT_MONOGRAPH,
                    [f"[alt*='{TXT_MONOGRAPH}']", f"[title*='{TXT_MONOGRAPH}']", ".ico-book", ".badge-book"],
                )
                if has_monograph:
                    has_available = False
                    try:
                        has_available = await row.locator(r"text=/\uB300\uCD9C\s*\uAC00\uB2A5/").count() > 0
                        if not has_available:
                            has_available = (
                                    await row.locator(
                                        f"button:has-text('{TXT_AVAILABLE}'), a:has-text('{TXT_AVAILABLE}'), [title*='{TXT_AVAILABLE}']"
                                    ).count()
                                    > 0
                            )
                    except Error:
                        has_available = False
                    if not has_available and re.search(r"\uB300\uCD9C\s*\uAC00\uB2A5", row_text):
                        has_available = True
                    # [수정된 부분] 찾으면 바로 덮어쓰지 않고 명확하게 할당
                    if not monograph_status:
                        monograph_status = TXT_MONOGRAPH_OWNED if has_available else TXT_ON_LOAN

                has_ebook = is_ebook_text(row_text) or await self._has_row_marker(
                    row,
                    TXT_EBOOK,
                    [f"[alt*='{TXT_EBOOK}']", f"[title*='{TXT_EBOOK}']", ".ico-ebook", ".badge-ebook"],
                )
                if has_ebook:
                    ebook_holding = parse_ebook_holding_count(row_text)
                    if not ebook_status:
                        ebook_status = TXT_OWNED if ebook_holding >= 1 else TXT_NOT_OWNED

            except Exception:
                continue

        # [수정된 부분] 단행본이 있으면 우선 반환하되, 전자책이 있다면 그 역시 우선순위에 따라 반환
        if monograph_status:
            return matched_title, TXT_MONOGRAPH, 0, monograph_status
        if ebook_status:
            return matched_title, TXT_EBOOK, ebook_holding, ebook_status

        return matched_title, "", 0, "type_row_not_found"

    async def search(self, title: str) -> dict:
        raw = title or ""
        query_clean = clean_title(raw)
        if not query_clean:
            return SearchResult(
                query_title_raw=raw,
                query_title_clean="",
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason="empty_title",
                error_type="validation",
            ).to_dict()

        ok, reason = await self.ensure_login()
        if not ok:
            return SearchResult(
                query_title_raw=raw,
                query_title_clean=query_clean,
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason=reason,
                error_type="login",
            ).to_dict()

        encoded = hybrid_encode_query(query_clean)
        search_url = f"{self.search_prefix}{encoded}"

        page = await self._new_page()
        try:
            base_url = search_url.split("#")[0]
            hash_part = search_url.split("#", 1)[1] if "#" in search_url else ""

            try:
                await page.goto(base_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
            except Exception:
                pass

            try:
                await page.wait_for_load_state("networkidle", timeout=5000)
            except Exception:
                pass

            if hash_part:
                try:
                    await page.evaluate(f"window.location.hash = '{hash_part}'")
                except Exception:
                    try:
                        await page.goto(search_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
                    except Exception:
                        pass
                try:
                    await page.wait_for_load_state("networkidle", timeout=5000)
                except Exception:
                    pass

            norm_query = normalize_text(query_clean)
            matched_title, material_type, holding_count, status = await self._scan_rows_for_title(page, norm_query)

            if not material_type:
                if hash_part:
                    try:
                        await page.evaluate(f"window.location.hash = '{hash_part}'")
                        await asyncio.sleep(1.0)
                    except Exception:
                        pass
                matched_title, material_type, holding_count, status = await self._scan_rows_for_title(page, norm_query)

            if material_type:
                return SearchResult(
                    query_title_raw=raw,
                    query_title_clean=query_clean,
                    matched_title=matched_title,
                    material_type=material_type,
                    status=status,
                    holding_count=holding_count,
                    reason="row_loop_scanned",
                    error_type="",
                ).to_dict()

            return SearchResult(
                query_title_raw=raw,
                query_title_clean=query_clean,
                matched_title=matched_title,
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason=status or "row_loop_empty_after_rescan",
                error_type="not_found",
            ).to_dict()
        except (Error, Exception) as e:
            return SearchResult(
                query_title_raw=raw,
                query_title_clean=query_clean,
                matched_title="",
                material_type="unknown",
                status=TXT_FALLBACK,
                holding_count=0,
                reason=f"playwright_error: {type(e).__name__}",
                error_type="timeout",
            ).to_dict()
        finally:
            try:
                await page.close()
            except Exception:
                pass