CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================
-- 통합 도서 테이블 (구 books + book 통합)
-- =============================================
CREATE TABLE IF NOT EXISTS books (
    isbn            VARCHAR(32) PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    title_norm      VARCHAR(500) NOT NULL DEFAULT '',
    author          VARCHAR(255),
    publisher       VARCHAR(255),
    image_url       VARCHAR(2048),
    published_date  DATE,
    price           DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 기존 books 테이블에 신규 컬럼이 없으면 추가 (마이그레이션 호환)
ALTER TABLE books ADD COLUMN IF NOT EXISTS title_norm  VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE books ADD COLUMN IF NOT EXISTS created_at  TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE books ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();

-- title_norm 인덱스
CREATE INDEX IF NOT EXISTS idx_books_title        ON books(title);
CREATE INDEX IF NOT EXISTS idx_books_author       ON books(author);
CREATE INDEX IF NOT EXISTS idx_books_title_norm_trgm
    ON books USING gin (title_norm gin_trgm_ops);

-- 공백 제거 + 소문자 기반 검색 가속 인덱스
CREATE INDEX IF NOT EXISTS idx_books_title_nospace
    ON books USING gin (REPLACE(LOWER(title), ' ', '') gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_books_author_nospace
    ON books USING gin (REPLACE(LOWER(author), ' ', '') gin_trgm_ops);

-- =============================================
-- 도서 이미지 테이블 (book_image.book_isbn -> books.isbn)
-- =============================================
CREATE TABLE IF NOT EXISTS book_image (
    id           BIGSERIAL PRIMARY KEY,
    book_isbn    VARCHAR(32) NOT NULL REFERENCES books(isbn) ON DELETE CASCADE,
    kind         VARCHAR(50) NOT NULL,
    source       VARCHAR(50) NOT NULL,
    content_type VARCHAR(100),
    width        INTEGER,
    height       INTEGER,
    sha256       VARCHAR(64) NOT NULL,
    bytes        BYTEA NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (book_isbn, sha256)
);

CREATE INDEX IF NOT EXISTS idx_book_image_isbn ON book_image(book_isbn);

-- =============================================
-- 도서관 소장 정보 캐시 테이블
-- Playwright 크롤링 결과를 DB에 저장하여 재호출 방지
-- =============================================
CREATE TABLE IF NOT EXISTS library_holding_cache (
    isbn            VARCHAR(32) PRIMARY KEY,
    found           BOOLEAN NOT NULL DEFAULT FALSE,
    available       BOOLEAN NOT NULL DEFAULT FALSE,
    status_code     VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    status_text     VARCHAR(100),
    location        VARCHAR(255),
    call_number     VARCHAR(255),
    detail_url      VARCHAR(2048),
    checked_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lhc_checked_at ON library_holding_cache(checked_at);

-- =============================================
-- 독서 일지 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS reading_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    book_title  VARCHAR(255) NOT NULL,
    isbn        VARCHAR(32),
    pages_read  INTEGER,
    memo        TEXT,
    log_date    DATE         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reading_logs_user_date ON reading_logs(user_id, log_date);

