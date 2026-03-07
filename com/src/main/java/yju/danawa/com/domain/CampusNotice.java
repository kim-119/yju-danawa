package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "campus_notices", indexes = {
        @Index(name = "idx_campus_notices_active_date", columnList = "is_active, posted_date DESC")
})
public class CampusNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 게시글 원본 ID (BOARDID). UNIQUE 제약 → 중복 방지 */
    @Column(name = "board_id", nullable = false, unique = true, length = 64)
    private String boardId;

    @Column(nullable = false, length = 500)
    private String title;

    /** 공지사항 대표 이미지 URL */
    @Column(name = "image_url", nullable = false, length = 2000)
    private String imageUrl;

    /** 게시글 원문 링크 */
    @Column(name = "link_url", length = 2000)
    private String linkUrl;

    /** 게시일 */
    @Column(name = "posted_date", length = 32)
    private String postedDate;

    /** 배너 노출 여부 */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt = Instant.now();

    protected CampusNotice() {}

    public CampusNotice(String boardId, String title, String imageUrl, String linkUrl, String postedDate) {
        this.boardId = boardId;
        this.title = title;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.postedDate = postedDate;
        this.active = true;
        this.crawledAt = Instant.now();
    }

    // ── Getters & Setters ──

    public Long getId() { return id; }

    public String getBoardId() { return boardId; }
    public void setBoardId(String boardId) { this.boardId = boardId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public String getPostedDate() { return postedDate; }
    public void setPostedDate(String postedDate) { this.postedDate = postedDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCrawledAt() { return crawledAt; }
    public void setCrawledAt(Instant crawledAt) { this.crawledAt = crawledAt; }
}

