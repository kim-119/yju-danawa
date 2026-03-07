package yju.danawa.com.service;

import yju.danawa.com.domain.Book;
import yju.danawa.com.dto.BookSearchItemDto;
import yju.danawa.com.repository.BookImageRepository;
import yju.danawa.com.repository.BookRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BookImageSearchService {

    private final BookRepository bookRepository;
    private final BookImageRepository bookImageRepository;
    private final String placeholderImageUrl;

    public BookImageSearchService(
            BookRepository bookRepository,
            BookImageRepository bookImageRepository,
            @Value("${app.placeholder-image-url:https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80+%EC%97%86%EC%9D%8C}") String placeholderImageUrl
    ) {
        this.bookRepository = bookRepository;
        this.bookImageRepository = bookImageRepository;
        this.placeholderImageUrl = placeholderImageUrl;
    }

    public Page<BookSearchItemDto> search(String query, Pageable pageable) {
        String normalized = normalizeQuery(query);
        if (normalized == null || normalized.isBlank()) {
            return Page.empty(pageable);
        }

        Page<Book> page = bookRepository.searchByTitleNorm(normalized, pageable);
        List<String> isbns = page.getContent().stream().map(Book::getIsbn).toList();
        Map<String, List<String>> imageUrlsByIsbn = loadImageUrls(isbns);

        return page.map(book -> new BookSearchItemDto(
                book.getIsbn(),
                book.getTitle(),
                book.getTitleNorm(),
                book.getAuthor(),
                book.getPublisher(),
                book.getPublishedDate(),
                imageUrlsByIsbn.getOrDefault(book.getIsbn(), List.of())));
    }

    private Map<String, List<String>> loadImageUrls(List<String> isbns) {
        Map<String, List<String>> result = new HashMap<>();
        if (isbns.isEmpty()) {
            return result;
        }

        // book_image 테이블에서 isbn 기반으로 이미지 조회
        List<BookImageRepository.BookImageIsbnRow> rows = bookImageRepository.findImageIdsByIsbns(isbns);
        for (BookImageRepository.BookImageIsbnRow row : rows) {
            result.computeIfAbsent(row.getIsbn(), key -> new ArrayList<>())
                    .add("/api/images/" + row.getId());
        }

        // book_image가 없는 경우 books.image_url 사용
        for (String isbn : isbns) {
            if (!result.containsKey(isbn)) {
                bookRepository.findByIsbn(isbn).ifPresent(book -> {
                    String url = normalizeStoredImageUrl(book.getImageUrl());
                    if (url != null && !url.isBlank()) {
                        result.put(isbn, List.of(url));
                    }
                });
            }
        }

        // 이미지가 없는 경우 placeholder 사용
        for (String isbn : isbns) {
            if (!result.containsKey(isbn) || result.get(isbn).isEmpty()) {
                result.put(isbn, List.of(placeholderImageUrl));
            }
        }
        return result;
    }

    private String normalizeStoredImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/api/") || url.startsWith("/images/")) {
            return url;
        }
        if (!url.contains("/") && url.contains(".")) {
            return "/api/images/by-name/" + url;
        }
        return url;
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }
}
