"""
도서관 SPA - 제목 URL 인코딩 방식 비교 (공백 처리)
"""
import asyncio, re, sys
sys.path.insert(0, r'C:\yjudanawa-damo\yjudanawa-damo\com\library-scraper')

from config import get_settings
from scraper_service import LibraryScraper
from urllib.parse import quote_from_bytes

lines = []

def encode_cp949_variants(text: str):
    """공백 처리 방식별 CP949 인코딩 URL 생성"""
    prefix = "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/"
    results = {}
    # 방식1: %20 (현재)
    p1 = []
    for ch in text:
        if ch == " ": p1.append("%20")
        elif ch.isascii() and (ch.isalnum() or ch in {"-","_","."}): p1.append(ch)
        elif re.match(r"[\uAC00-\uD7A3\u3131-\u318E]", ch): p1.append(quote_from_bytes(ch.encode("cp949"), safe=""))
        else: p1.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
    results["현재(%20)"] = prefix + "".join(p1)

    # 방식2: 공백 제거
    p2 = []
    for ch in text:
        if ch == " ": continue
        elif ch.isascii() and (ch.isalnum() or ch in {"-","_","."}): p2.append(ch)
        elif re.match(r"[\uAC00-\uD7A3\u3131-\u318E]", ch): p2.append(quote_from_bytes(ch.encode("cp949"), safe=""))
        else: p2.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
    results["공백제거"] = prefix + "".join(p2)

    # 방식3: + 사용
    p3 = []
    for ch in text:
        if ch == " ": p3.append("+")
        elif ch.isascii() and (ch.isalnum() or ch in {"-","_","."}): p3.append(ch)
        elif re.match(r"[\uAC00-\uD7A3\u3131-\u318E]", ch): p3.append(quote_from_bytes(ch.encode("cp949"), safe=""))
        else: p3.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
    results["+(plus)"] = prefix + "".join(p3)

    # 방식4: UTF-8
    p4 = []
    for ch in text:
        if ch == " ": p4.append("%20")
        elif ch.isascii() and (ch.isalnum() or ch in {"-","_","."}): p4.append(ch)
        else: p4.append(quote_from_bytes(ch.encode("utf-8"), safe=""))
    results["UTF-8(%20)"] = prefix + "".join(p4)

    return results

async def test_url(ctx, label, url):
    base = url.split("#")[0]
    hash_part = url.split("#", 1)[1] if "#" in url else ""
    page = await ctx.new_page()
    page.set_default_timeout(20000)
    await page.goto(base, wait_until="domcontentloaded", timeout=20000)
    try: await page.wait_for_load_state("networkidle", timeout=5000)
    except: pass
    if hash_part:
        await page.evaluate(f"window.location.hash = '{hash_part}'")
        try: await page.wait_for_load_state("networkidle", timeout=5000)
        except: pass
    await asyncio.sleep(2)
    cnt = await page.locator(".bookinfo").count()
    await page.close()
    return cnt

async def run():
    settings = get_settings()
    sc = LibraryScraper(settings)
    await sc._ensure_browser()
    await sc._ensure_login(await sc._context.new_page())

    title = "이펙티브 자바"
    variants = encode_cp949_variants(title)
    lines.append(f"제목: {title!r}")
    for label, url in variants.items():
        lines.append(f"\n[{label}]  url={url}")
        cnt = await test_url(sc._context, label, url)
        lines.append(f"  bookinfo={cnt}  {'✅ 동작!' if cnt > 0 else '❌ 실패'}")

    out = r'C:\yjudanawa-damo\yjudanawa-damo\com\library-scraper\debug_out.txt'
    with open(out, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    print(f"done -> {out}")

asyncio.run(run())
