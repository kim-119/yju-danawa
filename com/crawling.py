"""
Y-DANAWA 도서 크롤링 & DB Upsert 스크립트.

START.bat에서 서버 시작 전에 실행되며,
알라딘 API로 도서 정보를 크롤링하여 PostgreSQL에 저장합니다.

- ISBN 기준 UPSERT: 이미 존재하면 가격/정보 UPDATE, 없으면 INSERT
- PostgreSQL ON CONFLICT 구문 활용
- 가상 환경 없이 시스템 Python으로 실행
"""

import json
import os
import sys
import time
import urllib.request
import urllib.parse
import urllib.error
from datetime import datetime

# ─── 설정 ───────────────────────────────────────────────
# .env 파일에서 API 키 로드 (python-dotenv 없이 수동 파싱)
def load_env(env_path):
    if not os.path.exists(env_path):
        return
    content = None
    for enc in ("utf-8-sig", "utf-8", "cp949", "euc-kr", "latin-1"):
        try:
            with open(env_path, encoding=enc) as f:
                content = f.read()
            break
        except (UnicodeDecodeError, LookupError):
            continue
    if content is None:
        return
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = value


# 프로젝트 루트의 .env 로드
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
load_env(os.path.join(SCRIPT_DIR, ".env"))

# DB 연결 정보 (Docker Compose 기준 - 호스트에서 접근 시 localhost:5433)
DB_HOST = os.getenv("CRAWL_DB_HOST", "localhost")
DB_PORT = int(os.getenv("CRAWL_DB_PORT", "5433"))
DB_NAME = os.getenv("CRAWL_DB_NAME", "ydanawa_db")
DB_USER = os.getenv("CRAWL_DB_USER", "root")
DB_PASS = os.getenv("CRAWL_DB_PASS", "0910")

# 알라딘 API
ALADIN_TTB_KEY = os.getenv("ALADIN_TTB_KEY", "")

# 크롤링할 키워드 목록
SEED_KEYWORDS = [
    "자바", "파이썬", "자바스크립트", "C언어", "리액트", "스프링",
    "데이터베이스", "알고리즘", "운영체제", "네트워크", "코딩",
    "머신러닝", "딥러닝", "인공지능", "클라우드",
    "인간실격", "데미안", "어린왕자", "사피엔스",
    "경영학", "마케팅", "심리학", "경제학", "통계학",
    "미적분학", "물리학", "화학", "한국사", "세계사",
]

MAX_BOOKS = 300  # 최대 수집 권수


# ─── 알라딘 API 크롤링 ──────────────────────────────────
def search_aladin(keyword, max_results=20):
    """알라딘 상품 검색 API 호출."""
    if not ALADIN_TTB_KEY:
        print("[경고] ALADIN_TTB_KEY가 설정되지 않았습니다. .env 파일을 확인하세요.")
        return []

    params = urllib.parse.urlencode({
        "ttbkey": ALADIN_TTB_KEY,
        "Query": keyword,
        "QueryType": "Keyword",
        "MaxResults": max_results,
        "start": 1,
        "SearchTarget": "Book",
        "output": "js",
        "Version": "20131101",
        "Cover": "Big",
    })
    url = f"http://www.aladin.co.kr/ttb/api/ItemSearch.aspx?{params}"

    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Y-Danawa/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data.get("item", [])
    except Exception as e:
        print(f"  [경고] 알라딘 API 호출 실패 ({keyword}): {e}")
        return []


def normalize_isbn(raw):
    """ISBN에서 숫자만 추출하여 13자리 ISBN 반환."""
    if not raw:
        return None
    digits = "".join(c for c in str(raw) if c.isdigit())
    if len(digits) == 13 and (digits.startswith("978") or digits.startswith("979")):
        return digits
    return None


def normalize_title(title):
    """검색용 정규화: NFKC + 소문자 + 공백 정리."""
    import unicodedata
    if not title:
        return ""
    n = unicodedata.normalize("NFKC", title)
    return " ".join(n.lower().split())


def parse_date(date_str):
    """날짜 문자열을 YYYY-MM-DD 형식으로 변환."""
    if not date_str:
        return None
    for fmt in ("%Y-%m-%d", "%Y-%m-%dT%H:%M:%S", "%Y%m%d", "%Y-%m"):
        try:
            return datetime.strptime(date_str[:len(fmt.replace("%", "x"))], fmt).strftime("%Y-%m-%d")
        except (ValueError, IndexError):
            continue
    return None


def crawl_books():
    """알라딘 API로 도서 정보 크롤링."""
    books = {}
    total = 0

    for keyword in SEED_KEYWORDS:
        if total >= MAX_BOOKS:
            break

        print(f"  '{keyword}' 검색 중...")
        items = search_aladin(keyword)

        for item in items:
            isbn = normalize_isbn(item.get("isbn13") or item.get("isbn"))
            if not isbn or isbn in books:
                continue

            title = item.get("title", "").strip()
            if not title:
                continue

            books[isbn] = {
                "isbn": isbn,
                "title": title,
                "title_norm": normalize_title(title),
                "author": (item.get("author") or "").strip(),
                "publisher": (item.get("publisher") or "").strip(),
                "image_url": (item.get("cover") or "").strip(),
                "published_date": parse_date(item.get("pubDate")),
                "price": item.get("priceStandard") or item.get("priceSales"),
            }
            total += 1

            if total >= MAX_BOOKS:
                break

        time.sleep(0.5)  # API 호출 간격

    print(f"  총 {len(books)}권 크롤링 완료")
    return list(books.values())


# ─── DB Upsert ──────────────────────────────────────────
def upsert_books(books):
    """PostgreSQL ON CONFLICT를 사용한 Upsert."""
    try:
        import psycopg2
    except ImportError:
        print("[오류] psycopg2가 설치되지 않았습니다.")
        print("       pip install psycopg2-binary 를 실행해주세요.")
        sys.exit(1)

    conn = None
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASS,
            connect_timeout=10,
        )
        conn.autocommit = False
        cur = conn.cursor()

        upsert_sql = """
            INSERT INTO books (isbn, title, title_norm, author, publisher, image_url, published_date, price, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, now(), now())
            ON CONFLICT (isbn) DO UPDATE SET
                title          = EXCLUDED.title,
                title_norm     = EXCLUDED.title_norm,
                author         = EXCLUDED.author,
                publisher      = EXCLUDED.publisher,
                image_url      = COALESCE(EXCLUDED.image_url, books.image_url),
                published_date = COALESCE(EXCLUDED.published_date, books.published_date),
                price          = COALESCE(EXCLUDED.price, books.price),
                updated_at     = now()
        """

        inserted = 0
        updated = 0

        for book in books:
            # 기존 레코드 존재 여부 확인 (통계용)
            cur.execute("SELECT 1 FROM books WHERE isbn = %s", (book["isbn"],))
            exists = cur.fetchone() is not None

            cur.execute(upsert_sql, (
                book["isbn"],
                book["title"],
                book["title_norm"],
                book["author"],
                book["publisher"],
                book["image_url"] or None,
                book["published_date"],
                book["price"],
            ))

            if exists:
                updated += 1
            else:
                inserted += 1

        conn.commit()
        print(f"  DB Upsert 완료: {inserted}건 INSERT, {updated}건 UPDATE")

        # 최종 DB 도서 수 확인
        cur.execute("SELECT COUNT(*) FROM books")
        total = cur.fetchone()[0]
        print(f"  현재 DB 총 도서 수: {total}권")

        cur.close()

    except Exception as e:
        if conn:
            conn.rollback()
        print(f"[오류] DB 작업 실패: {e}")
        sys.exit(1)
    finally:
        if conn:
            conn.close()


# ─── 도서관 소장 정보 사전 캐싱 ─────────────────────────
def precache_library_holdings():
    """DB에 있는 도서들의 소장 여부를 library-scraper를 통해 일괄 크롤링하여 DB에 캐시."""
    import psycopg2

    SCRAPER_URL = os.getenv("SCRAPER_URL", "http://localhost:8090")

    conn = None
    try:
        conn = psycopg2.connect(
            host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
            user=DB_USER, password=DB_PASS, connect_timeout=10,
        )
        cur = conn.cursor()

        # library_holding_cache 테이블 생성 (없으면)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS library_holding_cache (
                isbn         VARCHAR(32) PRIMARY KEY,
                found        BOOLEAN NOT NULL DEFAULT FALSE,
                available    BOOLEAN NOT NULL DEFAULT FALSE,
                status_code  VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
                status_text  VARCHAR(100),
                location     VARCHAR(255),
                call_number  VARCHAR(255),
                detail_url   VARCHAR(2048),
                checked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """)
        conn.commit()

        # 캐시가 없거나 6시간 이상 된 ISBN 목록 조회
        cur.execute("""
            SELECT b.isbn FROM books b
            LEFT JOIN library_holding_cache c ON b.isbn = c.isbn
            WHERE c.isbn IS NULL
               OR c.checked_at < now() - interval '6 hours'
            ORDER BY c.checked_at ASC NULLS FIRST
            LIMIT 50
        """)
        isbns = [row[0] for row in cur.fetchall()]

        if not isbns:
            print("  모든 도서의 소장 정보가 최신 상태입니다.")
            cur.close()
            return

        print(f"  {len(isbns)}권의 소장 정보를 크롤링합니다...")
        cached = 0
        failed = 0

        for i, isbn in enumerate(isbns, 1):
            try:
                payload = json.dumps({"isbn": isbn}).encode("utf-8")
                req = urllib.request.Request(
                    f"{SCRAPER_URL}/check-library",
                    data=payload,
                    headers={"Content-Type": "application/json", "User-Agent": "Y-Danawa-Crawler/1.0"},
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=35) as resp:
                    data = json.loads(resp.read().decode("utf-8"))

                found = data.get("found", False)
                available_flag = data.get("available", False)
                status_code = data.get("status_text", data.get("loan_status_text", "UNKNOWN"))
                location_val = data.get("location", "")
                call_number_val = data.get("call_number", "")
                detail_url = data.get("detail_url", "")

                cur.execute("""
                    INSERT INTO library_holding_cache (isbn, found, available, status_code, status_text, location, call_number, detail_url, checked_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, now(), now())
                    ON CONFLICT (isbn) DO UPDATE SET
                        found = EXCLUDED.found,
                        available = EXCLUDED.available,
                        status_code = EXCLUDED.status_code,
                        status_text = EXCLUDED.status_text,
                        location = EXCLUDED.location,
                        call_number = EXCLUDED.call_number,
                        detail_url = EXCLUDED.detail_url,
                        checked_at = now(),
                        updated_at = now()
                """, (isbn, found, available_flag, status_code, status_code, location_val, call_number_val, detail_url))
                conn.commit()
                cached += 1

                status_symbol = "O" if found else "X"
                print(f"  [{i}/{len(isbns)}] {status_symbol} {isbn} -> {status_code}")

            except Exception as e:
                failed += 1
                print(f"  [{i}/{len(isbns)}] X {isbn} -> failed: {e}")

            time.sleep(1)  # Playwright 과부하 방지

        print(f"  소장 정보 캐싱 완료: {cached}건 성공, {failed}건 실패")
        cur.close()

    except Exception as e:
        print(f"  [경고] 소장 정보 캐싱 중 오류: {e}")
    finally:
        if conn:
            conn.close()


# ─── 메인 ──────────────────────────────────────────────
def main():
    print()
    print("========================================")
    print("  Y-DANAWA 도서 크롤링 시작")
    print("========================================")
    print()

    # 1. DB 연결 확인
    print("[1/4] DB 연결 확인 중...")
    try:
        import psycopg2
        conn = psycopg2.connect(
            host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
            user=DB_USER, password=DB_PASS, connect_timeout=10,
        )
        conn.close()
        print(f"  DB 연결 성공: {DB_HOST}:{DB_PORT}/{DB_NAME}")
    except ImportError:
        print("[오류] psycopg2가 설치되지 않았습니다.")
        print("       pip install psycopg2-binary")
        sys.exit(1)
    except Exception as e:
        print(f"[오류] DB 연결 실패: {e}")
        print(f"       Docker DB가 실행 중인지 확인하세요 (docker compose up -d db)")
        sys.exit(1)
    print()

    # 2. 알라딘 API 크롤링
    print("[2/4] 알라딘 API에서 도서 크롤링 중...")
    if not ALADIN_TTB_KEY:
        print("[경고] ALADIN_TTB_KEY가 없습니다. 크롤링을 건너뜁니다.")
        print("       .env 파일에 ALADIN_TTB_KEY=... 을 추가하세요.")
        print()
        print("[완료] 크롤링 건너뜀 (API 키 없음)")
        return

    books = crawl_books()
    if not books:
        print("[경고] 크롤링된 도서가 없습니다.")
        return
    print()

    # 3. DB Upsert
    print("[3/4] DB에 Upsert 중...")
    upsert_books(books)
    print()

    # 4. 도서관 소장 정보 사전 캐싱 (선택)
    print("[4/4] 도서관 소장 정보 사전 캐싱 중...")
    try:
        # library-scraper가 실행 중인지 확인
        req = urllib.request.Request(
            os.getenv("SCRAPER_URL", "http://localhost:8090") + "/health",
            headers={"User-Agent": "Y-Danawa-Crawler/1.0"},
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            health = json.loads(resp.read().decode("utf-8"))
            if health.get("status") == "healthy":
                precache_library_holdings()
            else:
                print("  [안내] library-scraper가 정상 상태가 아닙니다. 건너뜁니다.")
    except Exception:
        print("  [안내] library-scraper에 연결할 수 없습니다. 소장 정보 캐싱을 건너뜁니다.")
        print("         docker compose up -d 로 서비스를 먼저 시작하세요.")
    print()

    print("========================================")
    print("  크롤링 완료!")
    print("========================================")
    print()


if __name__ == "__main__":
    main()

