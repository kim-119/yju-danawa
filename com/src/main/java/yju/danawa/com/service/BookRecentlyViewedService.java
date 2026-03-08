package yju.danawa.com.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import yju.danawa.com.dto.BookDto;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookRecentlyViewedService {

    private static final String KEY_PREFIX = "user:%d:recent_books";
    private static final int MAX_RECENT_BOOKS = 30;

    private final StringRedisTemplate redisTemplate;
    private final BookService bookService;

    public BookRecentlyViewedService(StringRedisTemplate redisTemplate, BookService bookService) {
        this.redisTemplate = redisTemplate;
        this.bookService = bookService;
    }

    public void addRecentBook(Long userId, String isbn13) {
        if (userId == null || isbn13 == null) return;

        String key = String.format(KEY_PREFIX, userId);
        double score = System.currentTimeMillis();

        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();
        zset.add(key, isbn13, score);

        // 최신 30개만 유지 (나머지 삭제)
        // 0부터 -31까지는 30개를 제외한 나머지 (오래된 것들)
        long size = Objects.requireNonNullElse(zset.size(key), 0L);
        if (size > MAX_RECENT_BOOKS) {
            zset.removeRange(key, 0, size - MAX_RECENT_BOOKS - 1);
        }
    }

    public List<BookDto> getRecentBooks(Long userId) {
        if (userId == null) return Collections.emptyList();

        String key = String.format(KEY_PREFIX, userId);
        // 역순으로 가져오기 (최신순)
        Set<String> isbns = redisTemplate.opsForZSet().reverseRange(key, 0, MAX_RECENT_BOOKS - 1);

        if (isbns == null || isbns.isEmpty()) {
            return Collections.emptyList();
        }

        return isbns.stream()
                .map(isbn -> bookService.findByIsbn13(isbn).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
