package yju.danawa.com.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import yju.danawa.com.domain.CampusNotice;
import yju.danawa.com.repository.CampusNoticeRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CampusNoticeService {

    private static final Logger log = LoggerFactory.getLogger(CampusNoticeService.class);
    private static final String SCRAPER_BANNERS_URL = "http://library-scraper:8090/notices/banners";

    private final CampusNoticeRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CampusNoticeService(CampusNoticeRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 활성 배너 공지사항을 최대 limit 건 반환.
     */
    public List<CampusNotice> getActiveBanners(int limit) {
        return repository.findByActiveTrueOrderByCrawledAtDesc()
                .stream()
                .limit(limit)
                .toList();
    }

    /**
     * library-scraper에서 크롤링 결과를 가져와 DB에 UPSERT.
     * 5시간마다 자동 실행.
     */
    @Scheduled(fixedDelay = 5 * 60 * 60 * 1000, initialDelay = 60_000)
    public void syncFromScraper() {
        log.info("공지사항 배너 동기화 시작...");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SCRAPER_BANNERS_URL))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("공지사항 배너 API 응답 오류: HTTP {}", response.statusCode());
                return;
            }

            List<Map<String, Object>> items = objectMapper.readValue(
                    response.body(),
                    new TypeReference<>() {}
            );

            int upserted = 0;
            for (Map<String, Object> item : items) {
                String boardId = String.valueOf(item.get("board_id"));
                String title = String.valueOf(item.getOrDefault("title", ""));
                String imageUrl = String.valueOf(item.getOrDefault("image_url", ""));
                String linkUrl = String.valueOf(item.getOrDefault("link_url", ""));
                String postedDate = item.get("posted_date") != null ? String.valueOf(item.get("posted_date")) : null;

                if (boardId.isEmpty() || imageUrl.isEmpty()) {
                    continue;
                }

                CampusNotice notice = repository.findByBoardId(boardId)
                        .orElse(new CampusNotice(boardId, title, imageUrl, linkUrl, postedDate));

                notice.setTitle(title);
                notice.setImageUrl(imageUrl);
                notice.setLinkUrl(linkUrl);
                notice.setPostedDate(postedDate);
                notice.setCrawledAt(Instant.now());

                repository.save(notice);
                upserted++;
            }

            log.info("공지사항 배너 동기화 완료: {} 건 UPSERT", upserted);
        } catch (Exception e) {
            log.error("공지사항 배너 동기화 실패: {}", e.getMessage());
        }
    }
}

