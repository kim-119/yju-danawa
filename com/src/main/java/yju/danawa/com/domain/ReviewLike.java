package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_likes")
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ReviewLike() {}

    public ReviewLike(Long reviewId, Long userId) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getReviewId() { return reviewId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
