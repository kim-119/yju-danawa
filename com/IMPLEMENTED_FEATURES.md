# YJU Danawa - 구현된 주요 기능 명세서

이 문서는 프로젝트 개발 과정에서 구현된 주요 백엔드 및 프론트엔드 기능들의 기술적 상세와 코드 구조를 정리한 것입니다.

---

## 1. 최근 본 도서 (Recently Viewed Books)
사용자가 도서 상세 페이지를 방문할 때마다 해당 이력을 저장하고, 마이페이지에서 최신순으로 조회할 수 있는 기능입니다.

### 기술 스택
- **Storage**: Redis (ZSet)
- **Backend**: Spring Boot
- **Frontend**: Vue.js 3

### 주요 로직
- **저장**: Redis의 `ZSet`을 활용하여 사용자별로 도서 ISBN을 저장합니다. Score로 현재 타임스탬프를 사용하여 정렬 순서를 유지합니다.
- **최적화**: 리스트가 무한정 길어지지 않도록 최신 30개만 유지하고 나머지는 `ZREMRANGEBYRANK` 명령어로 삭제합니다.
- **조회**: `reverseRange`를 통해 가장 최근에 본 도서부터 가져옵니다.

### 주요 코드 (Java)
```java
// BookRecentlyViewedService.java
public void addRecentBook(Long userId, String isbn13) {
    String key = String.format("user:%d:recent_books", userId);
    double score = System.currentTimeMillis();
    ZSetOperations<String, String> zset = redisTemplate.opsForZSet();
    zset.add(key, isbn13, score);

    // 최신 30개 유지 최적화
    long size = Objects.requireNonNullElse(zset.size(key), 0L);
    if (size > 30) {
        zset.removeRange(key, 0, size - 30 - 1);
    }
}
```

---

## 2. 장바구니 (Shopping Cart)
사용자가 관심 있는 도서를 담아두고 관리할 수 있는 기능입니다.

### 기술 스택
- **Database**: PostgreSQL (`cart_items` 테이블)
- **Backend**: Spring Boot (JPA)
- **Frontend**: Vue.js 3 (Axios)

### 주요 특징
- **실시간 갱신**: 사용자가 장바구니에 아이템을 추가하거나 삭제할 때, API 응답으로 '현재 사용자의 전체 장바구니 목록'을 즉시 반환하여 프론트엔드 UI가 새로고침 없이 즉시 동기화되도록 설계했습니다.
- **데이터 무결성**: `(user_id, book_id)` 유니크 제약 조건을 통해 중복 담기를 방지하고, 기존에 담긴 책일 경우 수량(quantity)만 증가시킵니다.

### 주요 코드 (Java)
```java
// CartController.java
@PostMapping
public List<CartItemDto> addToCart(@RequestBody CartAddRequest request) {
    Long userId = securityUtil.getCurrentUserId().orElseThrow();
    cartItemService.addToCart(userId, request.bookId(), request.quantity());
    
    // 추가 후 즉시 최신 리스트 반환 (실시간 갱신 느낌)
    return cartItemService.getCartItems(userId);
}
```

---

## 3. 통합 리뷰 및 평점 시스템 (Unified Review & Rating)
기존의 단순 댓글 기능과 도서 리뷰/평점 기능을 하나로 통합한 시스템입니다.

### 주요 기능
- **통합 저장**: 댓글 내용과 1~5점 사이의 평점(Rating)을 한 테이블(`reviews`)에서 관리합니다.
- **리뷰 좋아요**: 다른 사용자의 리뷰에 좋아요를 누를 수 있으며, 중복 좋아요 방지 로직이 포함되어 있습니다.
- **난이도 자동 분석**: 사용자가 평점을 직접 선택하지 않아도, 리뷰 텍스트 내의 키워드(예: '어려움', '입문', '기초')를 분석하여 체감 난이도를 자동으로 수치화합니다.
- **히트맵 데이터**: 도서 상세 페이지에서 평점별 분포를 시각적으로 보여주기 위한 통계 API를 제공합니다.

### 주요 코드 (Java - 분석 로직)
```java
private int analyzeDifficulty(String content) {
    if (content == null) return 3;
    int score = 3;
    if (content.contains("어려움") || content.contains("복잡함")) score += 1;
    if (content.contains("쉬움") || content.contains("입문")) score -= 1;
    return Math.max(1, Math.min(5, score));
}
```

---

## 4. Python 기반 난이도 분석 서비스 (FastAPI Backbone)
텍스트 마이닝을 통한 정밀한 난이도 분석을 위해 별도로 구축된 서비스 레이어입니다.

### 기술 스택
- **Framework**: FastAPI
- **Language**: Python 3.12+

### 기능 요약
- 사용자가 작성한 리뷰 텍스트를 POST로 받아서 사전 정의된 긍정/부정(난이도 기준) 키워드 세트와 매칭합니다.
- 점수화된 데이터를 기반으로 `매우 쉬움`부터 `매우 어려움`까지의 라벨을 반환합니다.

### 주요 코드 (Python)
```python
@app.post("/analyze-difficulty")
async def analyze_difficulty(review: ReviewContent):
    content = review.content
    hard_count = sum(1 for kw in DIFFICULTY_KEYWORDS["hard"] if kw in content)
    easy_count = sum(1 for kw in DIFFICULTY_KEYWORDS["easy"] if kw in content)
    
    score = 3 + min(2, hard_count) - min(2, easy_count)
    score = max(1, min(5, score))
    
    return {"difficulty_score": score, "level": levels.get(score)}
```

---

## 5. 프론트엔드 연동 (API & UI)
모든 백엔드 기능은 Vue.js와 통신하여 사용자에게 시각적으로 제공됩니다.

- **`api/index.js`**: Axios 인터셉터를 사용하여 모든 요청에 JWT 토큰을 자동으로 포함하며, 401 에러 발생 시 자동 로그아웃 처리를 수행합니다.
- **`BookDetailView.vue`**: 도서관 소장 정보, 전자책 정보, 실시간 가격 비교, 통합 리뷰 리스트, 난이도 히트맵 차트를 한 화면에 보여줍니다.
- **`ProfileView.vue`**: 사용자 프로필 정보와 함께 Redis에서 가져온 '최근 본 도서' 목록 및 '장바구니' 목록을 실시간으로 관리할 수 있는 기능을 제공합니다.

---

### 데이터베이스 스키마 요약 (`schema.sql`)
```sql
-- 장바구니 테이블
CREATE TABLE IF NOT EXISTS cart_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(user_id),
    book_id     VARCHAR(32) NOT NULL REFERENCES books(isbn),
    quantity    INTEGER DEFAULT 1,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- 통합 리뷰 테이블
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL PRIMARY KEY,
    book_id     VARCHAR(32) NOT NULL REFERENCES books(isbn),
    user_id     BIGINT NOT NULL REFERENCES users(user_id),
    username    VARCHAR(64) NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    rating      INTEGER DEFAULT 3,
    created_at  TIMESTAMPTZ DEFAULT now()
);
```
