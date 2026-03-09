package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import yju.danawa.com.domain.ReviewLike;

import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    Optional<ReviewLike> findByReviewIdAndUserId(Long reviewId, Long userId);
    long countByReviewId(Long reviewId);
    boolean existsByReviewIdAndUserId(Long reviewId, Long userId);
}
