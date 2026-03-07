-- Verify index usage and similarity ordering for title_norm search.
ANALYZE books;

EXPLAIN (ANALYZE, BUFFERS)
SELECT isbn, title, title_norm
FROM books
WHERE title_norm ILIKE '%query%'
ORDER BY similarity(title_norm, 'query') DESC, isbn ASC
LIMIT 30;

-- Optional: refresh index stats if query plans look stale.
-- REINDEX INDEX idx_books_title_norm_trgm;
