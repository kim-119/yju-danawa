package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "used_book_images", indexes = {
        @Index(name = "idx_ubi_used_book_id", columnList = "used_book_id")
})
public class UsedBookImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_book_id", nullable = false)
    private UsedBook usedBook;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 1024)
    private String fileUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UsedBookImage() {}

    public UsedBookImage(UsedBook usedBook, String fileName, String fileUrl) {
        this.usedBook = usedBook;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UsedBook getUsedBook() { return usedBook; }
    public void setUsedBook(UsedBook usedBook) { this.usedBook = usedBook; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
