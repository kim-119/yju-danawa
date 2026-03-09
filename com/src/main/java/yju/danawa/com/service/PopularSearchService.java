package yju.danawa.com.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class PopularSearchService {

    private static final Logger log = LoggerFactory.getLogger(PopularSearchService.class);

    /**
     * 모든 유저의 검색어를 단일 전역 키에 누적 집계.
     * Redis Sorted Set: member=검색어, score=누적 검색 횟수
     */
    static final String REDIS_KEY = "global:search:ranking";

    private final StringRedisTemplate redisTemplate;

    public PopularSearchService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 검색어를 전역 랭킹에 +1 반영한다.
     * 대소문자·공백 정규화 후 저장하여 동일 의미의 검색어가 분산되지 않도록 한다.
     */
    public void record(String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        String normalized = keyword.trim().toLowerCase();
        try {
            redisTemplate.opsForZSet().incrementScore(REDIS_KEY, normalized, 1);
        } catch (Exception e) {
            log.warn("Redis 검색어 기록 실패 (무시): {}", e.getMessage());
        }
    }

    /**
     * 전체 통합 인기 검색어 상위 10개를 순위·키워드·누적 횟수와 함께 반환한다.
     */
    public List<RankEntry> getTop10() {
        try {
            Set<ZSetOperations.TypedTuple<String>> result =
                    redisTemplate.opsForZSet().reverseRangeWithScores(REDIS_KEY, 0, 9);
            if (result == null || result.isEmpty()) return Collections.emptyList();

            List<RankEntry> list = new ArrayList<>();
            int rank = 1;
            for (ZSetOperations.TypedTuple<String> tuple : result) {
                long count = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;
                list.add(new RankEntry(rank++, tuple.getValue(), count));
            }
            return list;
        } catch (Exception e) {
            log.warn("Redis 인기 검색어 조회 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public record RankEntry(int rank, String keyword, long count) {}
}
