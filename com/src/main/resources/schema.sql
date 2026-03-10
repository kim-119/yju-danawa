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

-- =============================================
-- 중고 마켓 판매 게시글 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS used_books (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255),
    price_won       INTEGER,
    description     VARCHAR(1000),
    seller_username VARCHAR(255),
    seller_id       BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    isbn            VARCHAR(32),
    isbn13          VARCHAR(13),
    book_condition  VARCHAR(10),
    image_url       VARCHAR(2048),
    status          VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    department_id   BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 기존 used_books 테이블에 신규 컬럼이 없으면 추가 (마이그레이션 호환)
ALTER TABLE used_books ADD COLUMN IF NOT EXISTS seller_id   BIGINT REFERENCES users(user_id) ON DELETE SET NULL;
ALTER TABLE used_books ADD COLUMN IF NOT EXISTS status      VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE used_books ADD COLUMN IF NOT EXISTS isbn13      VARCHAR(13);
ALTER TABLE used_books ADD COLUMN IF NOT EXISTS book_condition VARCHAR(10);

CREATE INDEX IF NOT EXISTS idx_usedbook_dept    ON used_books(department_id);
CREATE INDEX IF NOT EXISTS idx_usedbook_created ON used_books(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_usedbook_seller  ON used_books(seller_id);
CREATE INDEX IF NOT EXISTS idx_usedbook_isbn13  ON used_books(isbn13);

-- =============================================
-- 중고 마켓 이미지 테이블 (파일은 로컬 /uploads/images에 저장)
-- =============================================
CREATE TABLE IF NOT EXISTS used_book_images (
    id           BIGSERIAL PRIMARY KEY,
    used_book_id BIGINT NOT NULL REFERENCES used_books(id) ON DELETE CASCADE,
    file_name    VARCHAR(512) NOT NULL,
    file_url     VARCHAR(1024) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ubi_used_book_id ON used_book_images(used_book_id);

-- =============================================
-- 리뷰(댓글 통합) 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL PRIMARY KEY,
    book_id     VARCHAR(32) NOT NULL REFERENCES books(isbn) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    username    VARCHAR(64) NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    rating      INTEGER DEFAULT 3, -- 1~5점 (난이도/평점 통합용)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reviews_book_id_created ON reviews(book_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON reviews(user_id);

-- 리뷰 좋아요 테이블
CREATE TABLE IF NOT EXISTS review_likes (
    id          BIGSERIAL PRIMARY KEY,
    review_id   BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_like_review_user UNIQUE (review_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_review_likes_review_id ON review_likes(review_id);
CREATE INDEX IF NOT EXISTS idx_review_likes_user_id ON review_likes(user_id);
