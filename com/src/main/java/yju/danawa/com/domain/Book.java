package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "books", indexes = {
        @Index(name = "idx_books_title", columnList = "title"),
        @Index(name = "idx_books_author", columnList = "author"),
        @Index(name = "idx_books_title_norm_trgm", columnList = "title_norm")
})
public class Book {

    @Id
    @Column(length = 32, nullable = false)
    private String isbn; // PK (ISBN)

    @Column(nullable = false)
    private String title;

    /** 검색용 정규화 제목 (소문자, NFKC) */
    @Column(name = "title_norm", nullable = false, length = 500)
    private String titleNorm;

    private String author;

    private String publisher;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    private Double price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Book() {
    }

    public Book(String isbn, String title, String author, String publisher, String imageUrl, LocalDate publishedDate, Double price) {
        this.isbn = isbn;
        this.title = title;
        this.titleNorm = normalizeTitle(title);
        this.author = author;
        this.publisher = publisher;
        this.imageUrl = imageUrl;
        this.publishedDate = publishedDate;
        this.price = price;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String normalizeTitle(String t) {
        if (t == null) return "";
        String n = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFKC);
        return n.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titleNorm = normalizeTitle(title);
    }

    public String getTitleNorm() {
        return titleNorm;
    }

    public void setTitleNorm(String titleNorm) {
        this.titleNorm = titleNorm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
