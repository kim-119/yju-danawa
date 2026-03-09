package yju.danawa.com.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "book_comment_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_book_comment_like_comment_user", columnNames = {"comment_id", "user_id"})
}, indexes = {
        @Index(name = "idx_book_comment_likes_comment_id", columnList = "comment_id"),
        @Index(name = "idx_book_comment_likes_user_id", columnList = "user_id")
})
public class BookCommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private BookComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public BookComment getComment() {
        return comment;
    }

    public void setComment(BookComment comment) {
        this.comment = comment;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
