package yju.danawa.com.service;

import org.springframework.stereotype.Service;
import yju.danawa.com.domain.Review;
import yju.danawa.com.repository.ReviewRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public Map<Integer, Long> getDifficultyHeatmap(String bookId) {
        List<Review> reviews = reviewRepository.findByBookId(bookId);
        
        // 난이도별(1~5점) 개수 통계
        Map<Integer, Long> stats = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            stats.put(i, 0L);
        }

        for (Review review : reviews) {
            int difficulty = analyzeDifficulty(review.getContent());
            stats.put(difficulty, stats.get(difficulty) + 1);
        }

        return stats;
    }

    /**
     * 간단한 키워드 매칭 기반 난이도 분석 (1~5점)
     */
    private int analyzeDifficulty(String content) {
        if (content == null) return 3;

        int score = 3; // 기본 보통

        // 점수를 높이는 키워드 (어려움)
        if (content.contains("어려움") || content.contains("복잡함") || content.contains("전공자")) {
            score += 1;
        }
        if (content.contains("매우 어려움") || content.contains("심화")) {
            score += 1;
        }

        // 점수를 낮추는 키워드 (쉬움)
        if (content.contains("쉬움") || content.contains("입문") || content.contains("기초")) {
            score -= 1;
        }
        if (content.contains("매우 쉬움") || content.contains("초보자")) {
            score -= 1;
        }

        return Math.max(1, Math.min(5, score));
    }
}
