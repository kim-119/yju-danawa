package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_comment_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_comment_user", columnNames = {"comment_id", "user_id"})
})
public class BookCommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private BookComment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BookCommentLike() {}

    public Long getId() { return id; }
    public BookComment getComment() { return comment; }
    public void setComment(BookComment comment) { this.comment = comment; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
