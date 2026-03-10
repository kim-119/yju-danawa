package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_comments", indexes = {
        @Index(name = "idx_bookcomment_isbn13", columnList = "isbn13"),
        @Index(name = "idx_bookcomment_created", columnList = "created_at")
})
public class BookComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isbn13", nullable = false, length = 13)
    private String isbn13;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 비정규화: 목록 조회 성능용 */
    @Column(nullable = false, length = 64)
    private String username;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "completion_rate")
    private Integer completionRate; // 25, 50, 75, 100

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BookComment() {}

    public Long getId() { return id; }
    public String getIsbn13() { return isbn13; }
    public void setIsbn13(String isbn13) { this.isbn13 = isbn13; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Integer getCompletionRate() { return completionRate; }
    public void setCompletionRate(Integer completionRate) { this.completionRate = completionRate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
