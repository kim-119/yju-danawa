package yju.danawa.com.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yju.danawa.com.domain.Review;
import yju.danawa.com.domain.ReviewLike;
import yju.danawa.com.dto.ReviewDto;
import yju.danawa.com.repository.ReviewLikeRepository;
import yju.danawa.com.repository.ReviewRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;

    public ReviewService(ReviewRepository reviewRepository, ReviewLikeRepository reviewLikeRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewLikeRepository = reviewLikeRepository;
    }

    public List<ReviewDto> getReviews(String bookId, Long currentUserId) {
        return reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .map(r -> toDto(r, currentUserId))
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewDto createReview(String bookId, Long userId, String username, String content, Integer rating) {
        // 만약 rating이 명시되지 않았다면 텍스트 분석
        int finalRating = (rating == null || rating == 0) ? analyzeDifficulty(content) : rating;
        
        Review review = new Review(bookId, userId, username, content, finalRating);
        Review saved = reviewRepository.save(review);
        return toDto(saved, userId);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        if (!review.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to delete this review");
        }
        reviewRepository.delete(review);
    }

    @Transactional
    public Map<String, Object> toggleLike(Long reviewId, Long userId) {
        Optional<ReviewLike> existing = reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId);
        boolean liked;
        if (existing.isPresent()) {
            reviewLikeRepository.delete(existing.get());
            liked = false;
        } else {
            reviewLikeRepository.save(new ReviewLike(reviewId, userId));
            liked = true;
        }
        
        long count = reviewLikeRepository.countByReviewId(reviewId);
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", count);
        return result;
    }

    public Map<Integer, Long> getDifficultyHeatmap(String bookId) {
        List<Review> reviews = reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId);
        Map<Integer, Long> stats = new HashMap<>();
        for (int i = 1; i <= 5; i++) stats.put(i, 0L);

        for (Review review : reviews) {
            int rating = review.getRating();
            if (rating >= 1 && rating <= 5) {
                stats.put(rating, stats.get(rating) + 1);
            }
        }
        return stats;
    }

    private ReviewDto toDto(Review r, Long currentUserId) {
        long likeCount = reviewLikeRepository.countByReviewId(r.getId());
        boolean likedByMe = currentUserId != null && reviewLikeRepository.existsByReviewIdAndUserId(r.getId(), currentUserId);
        boolean ownedByMe = currentUserId != null && r.getUserId().equals(currentUserId);
        
        return new ReviewDto(
                r.getId(), r.getBookId(), r.getUserId(), r.getUsername(),
                r.getContent(), r.getRating(), r.getCreatedAt(),
                likeCount, likedByMe, ownedByMe
        );
    }

    /**
     * 간단한 키워드 매칭 기반 난이도 분석 (사용자가 평점을 직접 입력하지 않은 경우 대비)
     */
    private int analyzeDifficulty(String content) {
        if (content == null) return 3;
        int score = 3;
        if (content.contains("어려움") || content.contains("복잡함") || content.contains("전공자")) score += 1;
        if (content.contains("매우 어려움") || content.contains("심화")) score += 1;
        if (content.contains("쉬움") || content.contains("입문") || content.contains("기초")) score -= 1;
        if (content.contains("매우 쉬움") || content.contains("초보자")) score -= 1;
        return Math.max(1, Math.min(5, score));
    }
}
