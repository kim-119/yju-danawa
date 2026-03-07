package yju.danawa.com.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.dto.BookPriceDto;
import yju.danawa.com.dto.BookSearchItemDto;
import yju.danawa.com.dto.InfiniteScrollResponse;
import yju.danawa.com.service.BookImageSearchService;
import yju.danawa.com.service.BookPriceService;
import yju.danawa.com.service.BookService;
import yju.danawa.com.service.EbookLibraryService;
import yju.danawa.com.service.ExternalBookService;
import yju.danawa.com.service.LibraryGrpcClient;
import yju.danawa.com.service.LibraryRateLimiter;
import yju.danawa.com.service.LibraryStatusMapper;
import yju.danawa.com.service.YjuLibraryService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);
    private static final String PLACEHOLDER_COVER = "https://placehold.co/300x440?text=No+Cover";

    private final BookService bookService;
    private final ExternalBookService externalBookService;
    private final YjuLibraryService yjuLibraryService;
    private final BookPriceService bookPriceService;
    private final BookImageSearchService bookImageSearchService;
    private final EbookLibraryService ebookLibraryService;
    private final LibraryGrpcClient libraryGrpcClient;
    private final LibraryStatusMapper libraryStatusMapper;
    private final LibraryRateLimiter libraryRateLimiter;

    public BookController(
            BookService bookService,
            ExternalBookService externalBookService,
            YjuLibraryService yjuLibraryService,
            BookPriceService bookPriceService,
            BookImageSearchService bookImageSearchService,
            EbookLibraryService ebookLibraryService,
            LibraryGrpcClient libraryGrpcClient,
            LibraryStatusMapper libraryStatusMapper,
            LibraryRateLimiter libraryRateLimiter
    ) {
        this.bookService = bookService;
        this.externalBookService = externalBookService;
        this.yjuLibraryService = yjuLibraryService;
        this.bookPriceService = bookPriceService;
        this.bookImageSearchService = bookImageSearchService;
        this.ebookLibraryService = ebookLibraryService;
        this.libraryGrpcClient = libraryGrpcClient;
        this.libraryStatusMapper = libraryStatusMapper;
        this.libraryRateLimiter = libraryRateLimiter;
    }

    // Lightweight search endpoint (Danawa-style list page).
    @GetMapping(value = "/search", params = {"q", "!page", "!size"})
    public Map<String, Object> searchBooks(@RequestParam("q") String keyword) {
        // 1. 로컬 DB 검색 (Fallback 여부 포함)
        BookService.SearchResult localResult = bookService.searchWithFallback(keyword);
        List<BookDto> localRows = localResult.books();

        // 2. 외부 API 검색 (알라딘·카카오·구글 — 소설, 시, 인문 등 전 분야)
        List<BookDto> externalRows;
        try {
            externalRows = externalBookService.search(keyword, "auto");
        } catch (Exception e) {
            log.warn("외부 도서 검색 실패 (로컬 결과만 반환): {}", e.getMessage());
            externalRows = Collections.emptyList();
        }

        // Fallback이면 외부 검색 결과를 메인으로 사용
        boolean isFallback = localResult.isFallback() && !externalRows.isEmpty();

        // 3. 병합
        List<BookDto> allRows = new ArrayList<>();
        if (!localResult.isFallback()) {
            allRows.addAll(localRows);
        }
        allRows.addAll(externalRows);
        if (localResult.isFallback() && allRows.isEmpty()) {
            allRows.addAll(localRows);  // 외부도 없으면 인기도서라도 표시
            isFallback = true;
        }

        Map<String, SearchListItemResponse> deduped = new LinkedHashMap<>();
        for (BookDto row : allRows) {
            String isbn13 = normalizeIsbn13(row.isbn());
            // ISBN-13이 없는 항목은 상세 페이지로 이동할 수 없으므로 제외
            if (isbn13 == null) {
                continue;
            }
            String key = "isbn13:" + isbn13;

            SearchListItemResponse candidate = new SearchListItemResponse(
                    isbn13,
                    safe(row.title()),
                    safe(row.author()),
                    safe(row.publisher()),
                    normalizeThumbUrl(row.imageUrl())
            );

            SearchListItemResponse prev = deduped.get(key);
            if (prev == null || score(candidate) > score(prev)) {
                deduped.put(key, candidate);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.copyOf(deduped.values()));
        response.put("fallback", isFallback);
        if (isFallback) {
            response.put("fallbackMessage", "'" + keyword + "'에 대한 검색 결과가 없습니다. 이런 책은 어떠세요?");
        }
        return response;
    }

    @GetMapping("/{isbn13}")
    public BookDetailResponse getBookDetail(@PathVariable("isbn13") String isbn13, HttpServletRequest request) {
        String normalized = normalizeIsbn13(isbn13);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 must be exactly 13 digits");
        }

        String ip = request != null ? request.getRemoteAddr() : "unknown";
        String rateKey = ip + ":book-detail:" + normalized;
        if (!libraryRateLimiter.allow(rateKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "book-detail rate limited");
        }

        // 로컬 DB에 없으면 외부 API(알라딘·카카오·구글)에서 ISBN으로 조회
        BookDto book = bookService.findByIsbn13(normalized).orElseGet(() -> {
            try {
                List<BookDto> external = externalBookService.search(normalized, "auto");
                return external.stream()
                        .filter(b -> normalized.equals(normalizeIsbn13(b.isbn())))
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                log.warn("외부 API 도서 조회 실패: isbn13={}, {}", normalized, e.getMessage());
                return null;
            }
        });

        // 로컬 DB + 외부 API 모두 실패 시에도 ISBN 기반 최소 정보로 응답
        final BookDto resolvedBook = book != null ? book
                : new BookDto(normalized, "도서 정보 로딩 중", "", "", null, null, 0.0);

        // 도서관 소장 조회 (DB 캐시 → 메모리 캐시 → Playwright 순서)
        // 전자책 조회는 프론트엔드에서 별도 /ebook API로 비동기 호출하므로 여기서 제외
        final String finalNormalized = normalized;

        YjuLibraryService.LibraryAvailability availability;
        try {
            availability = yjuLibraryService.checkAvailability(finalNormalized, null);
        } catch (Exception e) {
            log.warn("도서관 조회 실패: {}", e.getMessage());
            availability = YjuLibraryService.LibraryAvailability.notFound(
                    "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/" + finalNormalized);
        }

        // 전자책 정보는 기본값 (프론트엔드가 별도 API로 비동기 로드)
        EbookLibraryService.EbookStatus ebookStatus = EbookLibraryService.EbookStatus.empty();

        LibraryStatusMapper.NormalizedStatus normalizedStatus = libraryStatusMapper.normalize(
                availability.isFound(),
                availability.isAvailable(),
                availability.getStatusCode(),
                availability.getErrorMessage()
        );

        return new BookDetailResponse(
                normalized,
                safe(resolvedBook.title()),
                safe(resolvedBook.author()),
                safe(resolvedBook.publisher()),
                normalizeCoverUrl(resolvedBook.imageUrl()),
                buildVendorLinks(normalized),
                buildEbookInfo(ebookStatus),
                new LibraryDetailResponse(
                        normalizedStatus.holding(),
                        availability.getOverallStatus() != null ? availability.getOverallStatus() : mapOverallStatus(normalizedStatus.statusCode()),
                        normalizedStatus.statusCode(),
                        normalizedStatus.statusText(),
                        availability.getRecordTypePicked() != null ? availability.getRecordTypePicked() : (normalizedStatus.holding() ? "단행본" : null),
                        availability.getMatchedTitle() != null ? availability.getMatchedTitle() : (normalizedStatus.holding() ? safe(resolvedBook.title()) : null),
                        safe(availability.getLocation()),
                        safe(availability.getCallNumber()),
                        safe(availability.getDetailUrl()).isBlank()
                                ? "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/" + normalized
                                : safe(availability.getDetailUrl()),
                        availability.getDebugReason() != null ? availability.getDebugReason() : "status_code=" + safe(normalizedStatus.statusCode())
                )
        );
    }

    @GetMapping(value = "/search", params = {"q", "page", "size"})
    public BookSearchPageResponse getBooksWithImages(
            @RequestParam("q") String keyword,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<BookSearchItemDto> result = bookImageSearchService.search(keyword, PageRequest.of(page, safeSize));
        return new BookSearchPageResponse(result.getContent(), result.getTotalElements(), page, safeSize);
    }

    @GetMapping("/{isbn13}/ebook")
    public EbookInfo getEbookInfo(@PathVariable("isbn13") String isbn13) {
        String normalized = normalizeIsbn13(isbn13);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 must be exactly 13 digits");
        }
        // 로컬 DB에서 제목/저자/출판사 가져오기
        BookDto book = bookService.findByIsbn13(normalized).orElse(null);
        String title = book != null ? book.title() : "";
        String author = book != null ? book.author() : "";
        String publisher = book != null ? book.publisher() : "";

        if (title.isBlank()) {
            // 외부 API에서 조회
            try {
                List<BookDto> external = externalBookService.search(normalized, "auto");
                BookDto found = external.stream()
                        .filter(b -> normalized.equals(normalizeIsbn13(b.isbn())))
                        .findFirst().orElse(null);
                if (found != null) {
                    title = found.title();
                    author = found.author();
                    publisher = found.publisher();
                }
            } catch (Exception e) {
                log.warn("전자책 조회 위한 도서 정보 실패: {}", e.getMessage());
            }
        }

        EbookLibraryService.EbookStatus ebookStatus = ebookLibraryService.fetchByTitle(title, author, publisher);
        return buildEbookInfo(ebookStatus);
    }

    @GetMapping("/library-check")
    public LibraryCheckResponse checkLibrary(
            @RequestParam(value = "isbn", required = false) String isbn,
            HttpServletRequest request
    ) {
        String isbn13 = normalizeIsbn13(isbn);
        if (isbn13 == null) {
            return new LibraryCheckResponse(false, false, null, null, "#", null, false, false, "UNKNOWN", "확인 중...");
        }

        String ip = request != null ? request.getRemoteAddr() : "unknown";
        String rateKey = ip + ":library-check:" + isbn13;
        if (!libraryRateLimiter.allow(rateKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "library-check rate limited");
        }

        YjuLibraryService.LibraryAvailability availability = yjuLibraryService.checkAvailability(isbn13, null);
        LibraryStatusMapper.NormalizedStatus normalized = libraryStatusMapper.normalize(
                availability.isFound(),
                availability.isAvailable(),
                availability.getStatusCode(),
                availability.getErrorMessage()
        );

        return new LibraryCheckResponse(
                availability.isFound(),
                availability.isAvailable(),
                availability.getLocation(),
                availability.getCallNumber(),
                availability.getDetailUrl(),
                availability.getErrorMessage(),
                normalized.holding(),
                normalized.loanable(),
                normalized.statusCode(),
                normalized.statusText()
        );
    }

    @GetMapping("/prices")
    public List<BookPriceDto> getBookPrices(
            @RequestParam(value = "isbn", required = false) String isbn,
            @RequestParam(value = "title", required = false) String title
    ) {
        return bookPriceService.getPrices(isbn, title);
    }

    @GetMapping({"/info", "/info/", "/book-info", "/book-info/"})
    public GrpcBookInfoResponse getBookInfoByIsbn13(@RequestParam("isbn13") String isbn13) {
        String cleanIsbn = isbn13.replaceAll("[^0-9]", "");
        LibraryGrpcClient.BookInfoResult result = libraryGrpcClient.getBookInfo(cleanIsbn);
        return new GrpcBookInfoResponse(
                result.isbn13(),
                result.availabilityStatus(),
                result.imageUrl(),
                result.imageSource(),
                result.errorMessage(),
                result.checkedAt()
        );
    }

    @GetMapping("/infinite")
    public ResponseEntity<InfiniteScrollResponse<BookDto>> getBooksInfiniteScroll(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit
    ) {
        if (limit > 100) {
            limit = 100;
        }
        InfiniteScrollResponse<BookDto> response = bookService.getBooksWithCursor(cursor, limit);
        return ResponseEntity.ok(response);
    }

    public record SearchListItemResponse(
            String isbn13,
            String title,
            String author,
            String publisher,
            String thumbUrl
    ) {
    }

    public record BookDetailResponse(
            String isbn13,
            String title,
            String author,
            String publisher,
            String coverUrl,
            VendorLinks vendors,
            EbookInfo ebook,
            LibraryDetailResponse library
    ) {
    }

    public record VendorLinks(String aladin, String kyobo, String yes24) {
    }

    public record EbookInfo(
            String title,
            boolean found,
            int totalHoldings,
            int availableHoldings,
            String statusText,
            String deepLinkUrl
    ) {
    }

    public record LibraryDetailResponse(
            boolean holding,
            String overallStatus,
            String status,
            String statusText,
            String recordTypePicked,
            String matchedTitle,
            String location,
            String callNo,
            String detailUrl,
            String debugReason
    ) {
    }

    public record BookSearchPageResponse(List<BookSearchItemDto> items, long total, int page, int size) {
    }

    public record GrpcBookInfoResponse(
            String isbn13,
            String availabilityStatus,
            String imageUrl,
            String imageSource,
            String errorMessage,
            String checkedAt
    ) {
    }

    public record LibraryCheckResponse(
            boolean found,
            boolean available,
            String location,
            String callNumber,
            String detailUrl,
            String errorMessage,
            boolean holding,
            boolean loanable,
            String statusCode,
            String statusText
    ) {
    }

    private int score(SearchListItemResponse item) {
        int score = 0;
        if (item.isbn13() != null && !item.isbn13().isBlank()) score += 4;
        if (item.thumbUrl() != null && !item.thumbUrl().isBlank()) score += 3;
        if (item.author() != null && !item.author().isBlank()) score += 2;
        if (item.publisher() != null && !item.publisher().isBlank()) score += 1;
        return score;
    }

    private VendorLinks buildVendorLinks(String isbn13) {
        String q = URLEncoder.encode(isbn13, StandardCharsets.UTF_8);
        return new VendorLinks(
                "https://www.aladin.co.kr/search/wsearchresult.aspx?SearchTarget=Book&SearchWord=" + q,
                "https://search.kyobobook.co.kr/search?keyword=" + q,
                "https://www.yes24.com/Product/Search?domain=BOOK&query=" + q
        );
    }

    private EbookInfo buildEbookInfo(EbookLibraryService.EbookStatus ebookStatus) {
        return new EbookInfo(
                safe(ebookStatus.title()),
                ebookStatus.found(),
                ebookStatus.totalHoldings(),
                ebookStatus.availableHoldings(),
                safe(ebookStatus.statusText()),
                safe(ebookStatus.deepLinkUrl())
        );
    }

    private String normalizeThumbUrl(String raw) {
        String v = safe(raw);
        if (v.isBlank()) {
            return PLACEHOLDER_COVER;
        }
        if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("/")) {
            return v;
        }
        return "/api/images/by-name/" + URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private String normalizeCoverUrl(String raw) {
        return normalizeThumbUrl(raw);
    }

    private String normalizeIsbn13(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 13 && (digits.startsWith("978") || digits.startsWith("979"))) {
            return digits;
        }
        return null;
    }

    private String normalizeText(String v) {
        return safe(v).trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private String mapOverallStatus(String code) {
        String c = safe(code).toUpperCase();
        if ("AVAILABLE".equals(c)) return "대출가능";
        if ("ON_LOAN".equals(c) || "RESERVED".equals(c)) return "대출중";
        if ("UNAVAILABLE".equals(c)) return "이용불가";
        return "정보없음";
    }
}

