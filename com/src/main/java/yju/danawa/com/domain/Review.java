package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private String bookId; // ISBN13

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column
    private Integer rating; // 1~5점

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Review() {
    }

    public Review(String bookId, Long userId, String username, String content, Integer rating) {
        this.bookId = bookId;
        this.userId = userId;
        this.username = username;
        this.content = content;
        this.rating = (rating == null) ? 3 : rating;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public String getBookId() { return bookId; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getContent() { return content; }
    public Integer getRating() { return rating; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
