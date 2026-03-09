package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "used_books", indexes = {
        @Index(name = "idx_usedbook_dept", columnList = "department_id"),
        @Index(name = "idx_usedbook_created", columnList = "created_at"),
        @Index(name = "idx_usedbook_seller", columnList = "seller_id")
})
public class UsedBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String author;

    @Column(name = "price_won")
    private Integer priceWon;

    @Column(length = 1000)
    private String description;

    /** 판매자 username 비정규화 필드 (목록 조회 성능용) */
    @Column(name = "seller_username")
    private String sellerUsername;

    /** 판매자 FK — 게시글 작성자 식별 및 권한 검증에 사용 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    private String isbn;

    /** 목록 뷰용 대표 이미지 URL (첫 번째 업로드 이미지) */
    @Column(name = "image_url")
    private String imageUrl;

    /** 판매 상태: AVAILABLE(판매중), RESERVED(예약중), SOLD(판매완료) */
    @Column(length = 50)
    private String status = "AVAILABLE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UsedBook() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Integer getPriceWon() { return priceWon; }
    public void setPriceWon(Integer priceWon) { this.priceWon = priceWon; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }
    public User getSeller() { return seller; }
    public void setSeller(User seller) { this.seller = seller; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
