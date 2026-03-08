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
    image_url       VARCHAR(2048),
    status          VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    department_id   BIGINT REFERENCES departments(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 기존 used_books 테이블에 신규 컬럼이 없으면 추가 (마이그레이션 호환)
ALTER TABLE used_books ADD COLUMN IF NOT EXISTS seller_id   BIGINT REFERENCES users(user_id) ON DELETE SET NULL;
ALTER TABLE used_books ADD COLUMN IF NOT EXISTS status      VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE';

CREATE INDEX IF NOT EXISTS idx_usedbook_dept    ON used_books(department_id);
CREATE INDEX IF NOT EXISTS idx_usedbook_created ON used_books(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_usedbook_seller  ON used_books(seller_id);

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
-- 도서 상세 댓글/좋아요 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS book_comments (
    id          BIGSERIAL PRIMARY KEY,
    isbn13      VARCHAR(13) NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    username    VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_book_comments_isbn13_created
    ON book_comments(isbn13, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_book_comments_user_id ON book_comments(user_id);

CREATE TABLE IF NOT EXISTS book_comment_likes (
    id          BIGSERIAL PRIMARY KEY,
    comment_id  BIGINT NOT NULL REFERENCES book_comments(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_book_comment_like_comment_user UNIQUE (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_book_comment_likes_comment_id
    ON book_comment_likes(comment_id);
CREATE INDEX IF NOT EXISTS idx_book_comment_likes_user_id
    ON book_comment_likes(user_id);

-- =============================================
-- 장바구니 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS cart_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    book_id     VARCHAR(32) NOT NULL REFERENCES books(isbn) ON DELETE CASCADE,
    quantity    INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items_user_book UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_user_id ON cart_items(user_id);

-- =============================================
-- 리뷰 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL PRIMARY KEY,
    book_id     VARCHAR(32) NOT NULL REFERENCES books(isbn) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    content     VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reviews_book_id ON reviews(book_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON reviews(user_id);
