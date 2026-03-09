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

        // YES24 가격이 unavailable이면 알라딘 가격을 폴백으로 사용
        BookPriceDto aladinPrice = prices.stream()
                .filter(p -> "aladin".equals(p.store()) && p.available())
                .findFirst().orElse(null);
        if (aladinPrice != null) {
            for (int i = 0; i < prices.size(); i++) {
                BookPriceDto p = prices.get(i);
                if ("yes24".equals(p.store()) && !p.available()) {
                    String yes24Url = buildYes24Url(searchKey);
                    prices.set(i, new BookPriceDto("yes24", "YES24",
                            aladinPrice.price(), yes24Url, "무료배송", true));
                    log.info("YES24 가격 폴백: 알라딘 가격 {}원 사용", aladinPrice.price());
                }
            }
        }

        log.info("가격 조회 완료: {} 개 서점", prices.size());
        return prices;
    }

    private BookPriceDto fetchYes24Price(String query) {
        String searchUrl = buildYes24Url(query);
        try {
            log.debug("YES24 가격 조회: {}", query);

            Document searchDoc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_SECONDS * 1000)
                .referrer("https://www.yes24.com")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .get();

            // ── Step 1: hidden input(ORD_GOODS_OPT)의 JSON에서 salePrice 추출 (가장 정확) ──
            // YES24 검색 결과는 각 상품에 hidden input으로 정가/판매가 JSON을 포함
            // 예: {"shopPrice":35000.00,"salePrice":31500.00,"discountShopPrice":3500.00}
            Element goodsOpt = searchDoc.selectFirst("input[name=ORD_GOODS_OPT]");
            if (goodsOpt != null) {
                String jsonValue = goodsOpt.attr("value");
                Matcher salePriceMatcher = Pattern.compile("\"salePrice\"\\s*:\\s*(\\d+(?:\\.\\d+)?)").matcher(jsonValue);
                if (salePriceMatcher.find()) {
                    int price = (int) Double.parseDouble(salePriceMatcher.group(1));
                    if (price >= 1000 && price <= 500000) {
                        // 상품 URL 추출 (소문자 /product/goods/)
                        String goodsUrl = extractYes24GoodsUrl(searchDoc, searchUrl);
                        log.info("YES24 가격 발견 (salePrice JSON): {}원", price);
                        return new BookPriceDto("yes24", "YES24", price, goodsUrl, "무료배송", true);
                    }
                }
            }

            // ── Step 2: 검색 결과 목록의 info_price 영역에서 판매가 추출 ──
            // YES24 검색 결과: <strong class="txt_num"><em class="yes_b">31,500</em>원</strong>
            Element salePriceEl = searchDoc.selectFirst("div.info_price strong.txt_num em.yes_b");
            if (salePriceEl != null) {
                String priceText = salePriceEl.text().replaceAll("[^0-9]", "");
                if (!priceText.isEmpty()) {
                    int price = Integer.parseInt(priceText);
                    if (price >= 1000 && price <= 500000) {
                        String goodsUrl = extractYes24GoodsUrl(searchDoc, searchUrl);
                        log.info("YES24 가격 발견 (info_price): {}원", price);
                        return new BookPriceDto("yes24", "YES24", price, goodsUrl, "무료배송", true);
                    }
                }
            }

            // ── Step 3: Fallback - 첫 번째 상품 아이템에서 가격 추출 ──
            for (Element item : searchDoc.select("li[data-goods-no]")) {
                Element priceBlock = item.selectFirst("div.info_price");
                if (priceBlock == null) continue;

                // strong.txt_num 내의 em.yes_b가 판매가 (할인가)
                Element salePrice = priceBlock.selectFirst("strong.txt_num em.yes_b");
                if (salePrice != null) {
                    String priceText = salePrice.text().replaceAll("[^0-9]", "");
                    if (!priceText.isEmpty()) {
                        int price = Integer.parseInt(priceText);
                        if (price >= 1000 && price <= 500000) {
                            String goodsUrl = searchUrl;
                            Element link = item.selectFirst("a[href*=/product/goods/], a[href*=/Product/Goods/]");
                            if (link != null) {
                                String href = link.attr("href");
                                goodsUrl = href.startsWith("http") ? href : "https://www.yes24.com" + href;
                            }
                            log.info("YES24 가격 발견 (item fallback): {}원", price);
                            return new BookPriceDto("yes24", "YES24", price, goodsUrl, "무료배송", true);
                        }
                    }
                }
            }

            log.warn("YES24 가격 요소를 찾을 수 없음. query={}", query);
        } catch (Exception e) {
            log.warn("YES24 가격 조회 실패: {}", e.getMessage());
        }
        return BookPriceDto.unavailable("yes24", "YES24", searchUrl);
    }

    /** YES24 검색 결과에서 첫 번째 상품 상세 URL 추출 (소문자/대문자 모두 대응) */
    private String extractYes24GoodsUrl(Document doc, String fallbackUrl) {
        Element link = doc.selectFirst("a[href*=/product/goods/], a[href*=/Product/Goods/]");
        if (link != null) {
            String href = link.attr("href");
            return href.startsWith("http") ? href : "https://www.yes24.com" + href;
        }
        return fallbackUrl;
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

