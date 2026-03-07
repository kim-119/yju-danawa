"""
도서관 + 전자책 크롤러 종합 테스트 v3
"""
import asyncio
import json
import sys
from datetime import datetime

sys.path.insert(0, r'C:\yjudanawa-damo\yjudanawa-damo\com\library-scraper')

# lru_cache 클리어 - 다른 모듈 import 전에 먼저 실행
import importlib
import config as _cfg_mod
_cfg_mod.get_settings.cache_clear()

from config import get_settings
from scraper_service import LibraryScraper
from ebook_scraper import EbookScraper


LIBRARY_CASES = [
    ("이펙티브 자바 [ISBN]",  "9788966262281", None,           None),
    ("이펙티브 자바 [제목]",  None,            "이펙티브 자바", None),
    ("클린 코드 [제목]",      None,            "클린 코드",     None),
    ("자바의 정석 [제목]",    None,            "자바의 정석",   None),
    ("없는 책 [제목]",        None,            "없는책xyz123",  None),
]

EBOOK_CASES = [
    ("이펙티브 자바 [전자책]", "이펙티브 자바",  "", ""),
    ("클린 코드 [전자책]",     "클린 코드",      "", ""),
    ("자바의 정석 [전자책]",   "자바의 정석",    "", ""),
    ("없는 책 [전자책]",       "없는책xyz123",   "", ""),
]


def short(d, keys):
    ev = d.get("evidence", {})
    return {k: d.get(k, ev.get(k, "-")) for k in keys}


async def run():
    settings = get_settings()
    # 인스턴스 1개 재사용 (브라우저 1개만 띄움)
    lib = LibraryScraper(settings)
    ebook = EbookScraper(settings)
    lines = []
    lines.append(f"실행시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"ebook_total_timeout_sec={settings.ebook_total_timeout_sec}")

    lines.append("\n" + "=" * 60)
    lines.append("  도서관 실물 도서 크롤러")
    lines.append("=" * 60)
    for label, isbn, title, author in LIBRARY_CASES:
        lines.append(f"\n▶ {label}")
        try:
            r = await lib.check_library(isbn=isbn, title=title, author=author)
            s = short(r, ["found", "available", "status_text", "overall_status",
                          "matched_title", "row_count", "error_message"])
            lines.append("  " + json.dumps(s, ensure_ascii=False))
        except Exception as e:
            lines.append(f"  EXCEPTION: {e}")
        finally:
            # 각 케이스 사이에 캐시 무효화 (캐시 TTL 0으로 강제)
            lib._cache.clear()

    lines.append("\n" + "=" * 60)
    lines.append("  전자책 크롤러  (ebook.yjc.ac.kr)")
    lines.append("=" * 60)
    lines.append(f"  ebook_base_url = {settings.ebook_base_url}")
    for label, title, author, pub in EBOOK_CASES:
        lines.append(f"\n▶ {label}")
        try:
            r = await ebook.check_ebook_by_title(title=title, author=author, publisher=pub)
            s = short(r, ["found", "total_holdings", "available_holdings",
                          "status_text", "error_message"])
            lines.append("  " + json.dumps(s, ensure_ascii=False))
        except Exception as e:
            lines.append(f"  EXCEPTION: {e}")

    out = r'C:\yjudanawa-damo\yjudanawa-damo\com\library-scraper\test_all_out.txt'
    with open(out, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    print(f"done -> {out}")


asyncio.run(run())
