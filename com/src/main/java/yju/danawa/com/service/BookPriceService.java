package yju.danawa.com.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import yju.danawa.com.dto.BookPriceDto;

import jakarta.annotation.PreDestroy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BookPriceService {

    private static final Logger log = LoggerFactory.getLogger(BookPriceService.class);
    private static final int TIMEOUT_SECONDS = 10;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final WebClient webClient;

    public BookPriceService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 여러 온라인 서점의 가격 정보를 조회
     * 실제 웹 페이지에서 가격을 크롤링
     */
    public List<BookPriceDto> getPrices(String isbn, String title) {
        log.info("도서 가격 조회 시작: ISBN={}, title={}", isbn, title);

        List<BookPriceDto> prices = new ArrayList<>();

        // 검색 키워드: ISBN이 있으면 우선, 없으면 제목 사용
        String searchKey = (isbn != null && !isbn.isBlank()) ? isbn : title;

        if (searchKey == null || searchKey.isBlank()) {
            log.warn("ISBN과 제목이 모두 없어서 가격 조회 불가");
            return prices;
        }

        // 병렬로 각 서점에서 가격 조회 (알라딘, YES24, 교보문고)
        List<CompletableFuture<BookPriceDto>> futures = new ArrayList<>();

        futures.add(CompletableFuture.supplyAsync(() -> fetchAladinPrice(searchKey), executorService));
        futures.add(CompletableFuture.supplyAsync(() -> fetchYes24Price(searchKey), executorService));
        futures.add(CompletableFuture.supplyAsync(() -> fetchKyoboPrice(searchKey), executorService));

        // 모든 결과 수집 (최대 15초 대기)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(15, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                log.warn("일부 가격 조회 시간 초과: {}", ex.getMessage());
                return null;
            })
            .join();

        for (CompletableFuture<BookPriceDto> future : futures) {
            try {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    prices.add(future.get());
                }
            } catch (Exception e) {
                log.warn("가격 정보 수집 중 오류: {}", e.getMessage());
            }
        }

        log.info("가격 조회 완료: {} 개 서점", prices.size());
        return prices;
    }

    private BookPriceDto fetchYes24Price(String query) {
        String url = buildYes24Url(query);
        try {
            log.debug("YES24 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .referrer("https://www.yes24.com")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .get();

            // YES24: 여러 선택자를 순서대로 시도 (사이트 구조 변경 대응)
            String[] selectors = {
                "span.opts_price em.yes_b",    // 최신 검색 결과 가격
                "em.yes_b",                     // 가격 강조 텍스트
                "span.price em",                // 일반 가격 구조
                "strong.txt_num",               // 구 구조
                "em.yes_m",                     // 구 구조 (할인가)
                "span.txt_num em",              // 대체 가격 구조
                "div.itemUnit span.price",      // 아이템 단위 가격
                "li.clearfix span.price em"     // 리스트 아이템 가격
            };

            for (String selector : selectors) {
                Element priceElement = doc.selectFirst(selector);
                if (priceElement != null) {
                    String priceText = priceElement.text().replaceAll("[^0-9]", "");
                    if (!priceText.isEmpty()) {
                        int price = Integer.parseInt(priceText);
                        if (price > 0) {
                            log.info("YES24 가격 발견 (selector={}): {}원", selector, price);
                            return new BookPriceDto("yes24", "YES24", price, url, "무료배송", true);
                        }
                    }
                }
            }

            // Fallback: 정규식으로 가격 패턴 검색 (HTML 내에서 숫자,숫자원 패턴)
            String html = doc.html();
            Pattern pricePattern = Pattern.compile("(\\d{1,3}(?:,\\d{3})+)\\s*원");
            Matcher matcher = pricePattern.matcher(html);
            // 검색 결과 영역에서 첫 번째 가격 패턴 매칭
            if (matcher.find()) {
                String priceText = matcher.group(1).replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    if (price >= 1000 && price <= 500000) {  // 합리적인 가격 범위
                        log.info("YES24 가격 발견 (regex fallback): {}원", price);
                        return new BookPriceDto("yes24", "YES24", price, url, "무료배송", true);
                    }
                }
            }

            log.warn("YES24 가격 요소를 찾을 수 없음. query={}", query);
        } catch (Exception e) {
            log.warn("YES24 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("yes24", "YES24", url);
    }

    private BookPriceDto fetchAladinPrice(String query) {
        String url = buildAladinUrl(query);
        try {
            log.debug("알라딘 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .get();

            // 알라딘: ss_p2 클래스의 가격 추출
            Element priceElement = doc.selectFirst("span.ss_p2 b, span.ss_p2");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    log.info("알라딘 가격 발견: {}원", price);
                    return new BookPriceDto("aladin", "알라딘", price, url, "무료배송", true);
                }
            }
        } catch (Exception e) {
            log.warn("알라딘 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("aladin", "알라딘", url);
    }

    private BookPriceDto fetchKyoboPrice(String query) {
        String url = buildKyoboUrl(query);
        try {
            log.debug("교보문고 가격 조회: {}", query);
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .get();

            // 교보문고: 할인가 추출
            Element priceElement = doc.selectFirst("span.val, span.price_val");
            if (priceElement != null) {
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    log.info("교보문고 가격 발견: {}원", price);
                    return new BookPriceDto("kyobo", "교보문고", price, url, "무료배송", true);
                }
            }
        } catch (Exception e) {
            log.warn("교보문고 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("kyobo", "교보문고", url);
    }

    private String buildYes24Url(String query) {
        return "https://www.yes24.com/Product/Search?domain=BOOK&query=" + encodeQuery(query);
    }

    private String buildAladinUrl(String query) {
        return "https://www.aladin.co.kr/search/wsearchresult.aspx?SearchTarget=Book&SearchWord=" + encodeQuery(query);
    }

    private String buildKyoboUrl(String query) {
        return "https://search.kyobobook.co.kr/search?keyword=" + encodeQuery(query);
    }


    private static String encodeQuery(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}

