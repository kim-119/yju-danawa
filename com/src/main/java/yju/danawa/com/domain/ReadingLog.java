package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reading_logs", indexes = {
        @Index(name = "idx_reading_logs_user_date", columnList = "user_id, log_date")
})
public class ReadingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "user_id")
    private User user;

    @Column(name = "book_title", nullable = false, length = 255)
    private String bookTitle;

    @Column(length = 32)
    private String isbn;

    @Column(name = "pages_read")
    private Integer pagesRead;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime createdAt;

    public ReadingLog() {}

    public ReadingLog(User user, String bookTitle, String isbn,
                      Integer pagesRead, String memo,
                      LocalDate logDate, LocalDateTime createdAt) {
        this.user      = user;
        this.bookTitle = bookTitle;
        this.isbn      = isbn;
        this.pagesRead = pagesRead;
        this.memo      = memo;
        this.logDate   = logDate;
        this.createdAt = createdAt;
    }

    public Long getId()               { return id; }
    public User getUser()             { return user; }
    public String getBookTitle()      { return bookTitle; }
    public String getIsbn()           { return isbn; }
    public Integer getPagesRead()     { return pagesRead; }
    public String getMemo()           { return memo; }
    public LocalDate getLogDate()     { return logDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUser(User user)          { this.user = user; }
    public void setBookTitle(String t)      { this.bookTitle = t; }
    public void setIsbn(String isbn)        { this.isbn = isbn; }
    public void setPagesRead(Integer p)     { this.pagesRead = p; }
    public void setMemo(String memo)        { this.memo = memo; }
    public void setLogDate(LocalDate d)     { this.logDate = d; }
    public void setCreatedAt(LocalDateTime c) { this.createdAt = c; }
}
