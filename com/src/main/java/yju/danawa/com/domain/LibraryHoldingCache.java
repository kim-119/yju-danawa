package yju.danawa.com.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 도서관 소장 정보 캐시.
 * Playwright 크롤링 결과를 DB에 저장하여 동일 ISBN 재요청 시 즉시 응답.
 */
@Entity
@Table(name = "library_holding_cache")
public class LibraryHoldingCache {

    @Id
    @Column(length = 32, nullable = false)
    private String isbn;

    @Column(nullable = false)
    private boolean found;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "status_code", length = 50, nullable = false)
    private String statusCode = "UNKNOWN";

    @Column(name = "status_text", length = 100)
    private String statusText;

    @Column(length = 255)
    private String location;

    @Column(name = "call_number", length = 255)
    private String callNumber;

    @Column(name = "detail_url", length = 2048)
    private String detailUrl;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LibraryHoldingCache() {}

    public LibraryHoldingCache(String isbn, boolean found, boolean available,
                                String statusCode, String statusText,
                                String location, String callNumber, String detailUrl) {
        this.isbn = isbn;
        this.found = found;
        this.available = available;
        this.statusCode = statusCode != null ? statusCode : "UNKNOWN";
        this.statusText = statusText;
        this.location = location;
        this.callNumber = callNumber;
        this.detailUrl = detailUrl;
        Instant now = Instant.now();
        this.checkedAt = now;
        this.updatedAt = now;
    }

    // ── Getters & Setters ──
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCallNumber() { return callNumber; }
    public void setCallNumber(String callNumber) { this.callNumber = callNumber; }

    public String getDetailUrl() { return detailUrl; }
    public void setDetailUrl(String detailUrl) { this.detailUrl = detailUrl; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** 캐시가 유효한지 확인 (6시간 TTL) */
    public boolean isValid() {
        return checkedAt != null &&
               checkedAt.plusSeconds(6 * 3600).isAfter(Instant.now());
    }
}

