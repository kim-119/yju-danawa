# Y-Danawa 프로젝트 코드 학습 가이드

> 팀원 학습용 자료 — 전체 코드를 레이어별로 한 줄씩 해설합니다.

---

## 목차

1. [프로젝트 전체 구조](#1-프로젝트-전체-구조)
2. [데이터베이스 (schema.sql / data.sql)](#2-데이터베이스)
3. [설정 파일 (application.yml)](#3-설정-파일-applicationyml)
4. [진입점 (ComApplication.java)](#4-진입점-comapplicationjava)
5. [도메인 엔티티 레이어](#5-도메인-엔티티-레이어)
6. [DTO 레이어](#6-dto-레이어)
7. [Repository 레이어](#7-repository-레이어)
8. [Service 레이어](#8-service-레이어)
9. [Controller(Web) 레이어](#9-controllerweb-레이어)
10. [Security 레이어](#10-security-레이어)
11. [Config 레이어](#11-config-레이어)
12. [전체 흐름 요약](#12-전체-흐름-요약)
13. [Python / FastAPI — library-scraper 서비스](#13-python--fastapi--library-scraper-서비스)
14. [Vue.js 프론트엔드](#14-vuejs-프론트엔드)
15. [전체 시스템 흐름 (3개 레이어 통합)](#15-전체-시스템-흐름-3개-레이어-통합)

---

## 1. 프로젝트 전체 구조

```
com/
└── src/main/java/yju/danawa/com/
    ├── ComApplication.java          ← Spring Boot 시작점
    ├── domain/                      ← DB 테이블과 1:1 대응하는 엔티티 클래스
    ├── dto/                         ← 데이터 전달 객체 (계층 간 데이터 이동용)
    ├── repository/                  ← DB 접근 인터페이스 (JPA)
    ├── service/                     ← 비즈니스 로직
    ├── web/                         ← HTTP 요청/응답 처리 (Controller)
    ├── security/                    ← JWT 인증·인가 처리
    └── config/                      ← 각종 Bean 설정
```

### 계층(Layer) 이해

```
[클라이언트 요청]
       ↓
  Controller (web/)       ← URL 매핑, 요청·응답 변환
       ↓
   Service (service/)     ← 실제 업무 로직
       ↓
  Repository (repository/)← DB 쿼리
       ↓
   Database (PostgreSQL)
```

---

## 2. 데이터베이스

### 2-1. schema.sql

```sql
-- pg_trgm 확장 설치 (trigram 검색을 위해 필요)
-- trigram: 문자열을 3글자씩 잘라 유사도 검색에 사용하는 알고리즘
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

---

#### books 테이블 (도서 정보)

```sql
CREATE TABLE IF NOT EXISTS books (
    isbn            VARCHAR(32) PRIMARY KEY,   -- 도서 고유번호(ISBN), PK
    title           VARCHAR(255) NOT NULL,      -- 원본 제목
    title_norm      VARCHAR(500) NOT NULL DEFAULT '',  -- 검색용 정규화 제목 (소문자+NFKC)
    author          VARCHAR(255),               -- 저자
    publisher       VARCHAR(255),               -- 출판사
    image_url       VARCHAR(2048),              -- 표지 이미지 URL
    published_date  DATE,                       -- 출판일
    price           DOUBLE PRECISION,           -- 정가
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 생성 시각 (타임존 포함)
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()   -- 수정 시각
);
```

> **TIMESTAMPTZ**: 타임존 정보가 포함된 시각. `now()`는 DB 서버의 현재 시각을 자동 삽입.

```sql
-- 기존 테이블에 새 컬럼이 없으면 추가 (무중단 마이그레이션용)
ALTER TABLE books ADD COLUMN IF NOT EXISTS title_norm  VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE books ADD COLUMN IF NOT EXISTS created_at  TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE books ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();
```

> **IF NOT EXISTS**: 이미 컬럼이 있으면 에러 없이 건너뜀. 서버를 재시작해도 안전.

```sql
-- 일반 B-tree 인덱스 (등호/범위 검색 가속)
CREATE INDEX IF NOT EXISTS idx_books_title        ON books(title);
CREATE INDEX IF NOT EXISTS idx_books_author       ON books(author);

-- GIN + trigram 인덱스 (LIKE '%검색어%' 성능 대폭 향상)
CREATE INDEX IF NOT EXISTS idx_books_title_norm_trgm
    ON books USING gin (title_norm gin_trgm_ops);

-- 공백 제거 + 소문자로 변환한 값에 GIN 인덱스 (띄어쓰기 무시 검색)
CREATE INDEX IF NOT EXISTS idx_books_title_nospace
    ON books USING gin (REPLACE(LOWER(title), ' ', '') gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_books_author_nospace
    ON books USING gin (REPLACE(LOWER(author), ' ', '') gin_trgm_ops);
```

> **GIN(Generalized Inverted Index)**: 배열, 텍스트 검색에 특화된 인덱스 타입.
> **gin_trgm_ops**: trigram 방식으로 GIN 인덱스를 구성하는 연산자 클래스.

---

#### book_image 테이블 (도서 이미지 바이너리)

```sql
CREATE TABLE IF NOT EXISTS book_image (
    id           BIGSERIAL PRIMARY KEY,        -- 자동 증가 PK
    book_isbn    VARCHAR(32) NOT NULL REFERENCES books(isbn) ON DELETE CASCADE,
    --           ↑ books.isbn을 FK로 참조, 도서 삭제 시 이미지도 자동 삭제
    kind         VARCHAR(50)  NOT NULL,         -- 이미지 종류 (예: "cover")
    source       VARCHAR(50)  NOT NULL,         -- 출처 (예: "aladin", "kakao")
    content_type VARCHAR(100),                  -- MIME 타입 (예: "image/jpeg")
    width        INTEGER,                       -- 가로 픽셀
    height       INTEGER,                       -- 세로 픽셀
    sha256       VARCHAR(64)  NOT NULL,         -- 이미지 내용의 SHA-256 해시 (중복 방지)
    bytes        BYTEA        NOT NULL,         -- 실제 이미지 바이너리 데이터
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (book_isbn, sha256)                  -- 같은 책에 같은 이미지 중복 저장 방지
);
```

> **BYTEA**: PostgreSQL의 바이너리 데이터 타입. 이미지를 DB에 직접 저장할 때 사용.
> **UNIQUE 제약**: (book_isbn, sha256) 조합이 겹치면 INSERT 거부.

---

#### library_holding_cache 테이블 (도서관 소장 정보 캐시)

```sql
CREATE TABLE IF NOT EXISTS library_holding_cache (
    isbn            VARCHAR(32) PRIMARY KEY,    -- 조회한 ISBN (PK)
    found           BOOLEAN NOT NULL DEFAULT FALSE,   -- 소장 여부
    available       BOOLEAN NOT NULL DEFAULT FALSE,   -- 대출 가능 여부
    status_code     VARCHAR(50)  NOT NULL DEFAULT 'UNKNOWN', -- 상태 코드
    status_text     VARCHAR(100),               -- 사람이 읽는 상태 문자열
    location        VARCHAR(255),               -- 소장 위치 (예: "제1자료실")
    call_number     VARCHAR(255),               -- 청구기호
    detail_url      VARCHAR(2048),              -- 도서관 상세 페이지 URL
    checked_at      TIMESTAMPTZ NOT NULL DEFAULT now(), -- 마지막 조회 시각
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

> Playwright로 도서관 웹사이트를 크롤링한 결과를 여기 저장해 두면, 같은 ISBN 재요청 시
> 크롤링 없이 DB에서 즉시 응답 가능 (6시간 TTL).

---

### 2-2. data.sql

```sql
-- 실제 Mock 데이터는 없음. 외부 API(알라딘, 카카오)에서 앱 시작 시 자동 로드
-- BookDataLoaderService가 처리

-- Spring이 빈 SQL 파일을 에러로 처리하므로 최소 1개의 SQL 문 필요
SELECT 1;
```

---

### 2-3. search_tuning.sql (성능 튜닝 참고용)

```sql
-- 테이블 통계 갱신 (쿼리 플래너가 최신 통계로 최적화 계획 수립)
ANALYZE books;

-- 실행 계획 확인 (인덱스가 실제로 사용되는지 확인)
EXPLAIN (ANALYZE, BUFFERS)
SELECT isbn, title, title_norm
FROM books
WHERE title_norm ILIKE '%query%'
ORDER BY similarity(title_norm, 'query') DESC, isbn ASC
LIMIT 30;

-- 인덱스 통계가 낡았을 때 수동으로 재구성
-- REINDEX INDEX idx_books_title_norm_trgm;
```

> `EXPLAIN ANALYZE`는 쿼리가 실제로 얼마나 걸리는지 측정하는 명령어.
> 개발 중 느린 쿼리를 분석할 때 필수 도구.

---

## 3. 설정 파일 (application.yml)

```yaml
spring:
  application:
    name: com        # Spring 앱의 논리적 이름
```

```yaml
  datasource:
    # ${환경변수:기본값} 문법 — 환경변수가 있으면 그 값, 없으면 기본값 사용
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://${SPRING_DATASOURCE_HOST:localhost}:${SPRING_DATASOURCE_PORT:5433}/${SPRING_DATASOURCE_DB:ydanawa_db}}
    username: ${SPRING_DATASOURCE_USERNAME:root}
    password: ${SPRING_DATASOURCE_PASSWORD:0910}
    driver-class-name: org.postgresql.Driver   # PostgreSQL JDBC 드라이버

    hikari:                              # HikariCP 커넥션 풀 설정
      connection-timeout: 60000          # 커넥션 획득 최대 대기 시간 (ms)
      validation-timeout: 5000           # 커넥션 유효성 검사 시간 (ms)
      initialization-fail-timeout: -1    # -1 = 시작 시 DB 연결 실패해도 앱 계속 기동
```

> **HikariCP**: Spring Boot 기본 커넥션 풀. DB 커넥션을 미리 여러 개 만들어 두고 재사용.

```yaml
  jpa:
    hibernate:
      ddl-auto: update     # 앱 시작 시 엔티티에 맞게 테이블 자동 변경 (컬럼 추가만 함, 삭제 안 함)
    properties:
      hibernate:
        format_sql: true   # 로그에 SQL을 보기 좋게 들여쓰기해서 출력
    defer-datasource-initialization: true  # JPA 초기화 후 schema.sql/data.sql 실행

  sql:
    init:
      mode: always         # 매번 schema.sql과 data.sql 실행
```

> **ddl-auto 옵션**:
> - `none`: 아무것도 안 함
> - `validate`: 엔티티와 테이블이 일치하는지만 검사
> - `update`: 차이가 있으면 ALTER TABLE로 맞춤 (운영 환경에서 주로 사용)
> - `create`: 매번 DROP 후 CREATE (개발 초기에만 사용)
> - `create-drop`: create + 앱 종료 시 DROP

```yaml
app:
  api-key: ${APP_API_KEY:}                    # API 키 (빈 문자열이면 인증 생략)
  grpc:
    host: ${APP_GRPC_HOST:localhost}           # gRPC 서버 주소
    port: ${APP_GRPC_PORT:9090}               # gRPC 포트
  images:
    directory: ${CRAWLER_OUT:c:/yjudanawa-damo/com/image-crawler}  # 이미지 파일 저장 경로
  external:
    kakao-rest-api-key: ${KAKAO_REST_API_KEY:}   # 카카오 API 키
    google-api-key: ${GOOGLE_API_KEY:}           # 구글 Books API 키
    aladin-ttb-key: ${ALADIN_TTB_KEY:}           # 알라딘 TTB API 키
  jwt:
    secret: ${APP_JWT_SECRET:dev-secret-key-change-me-32bytes!!}  # JWT 서명 비밀키
    expiration-minutes: ${APP_JWT_EXP_MINUTES:60}  # 토큰 만료 시간 (분)
```

```yaml
# --- Docker 프로필 오버라이드 ---
# spring.profiles.active=docker 로 실행 시 아래 값으로 덮어씀
---
spring:
  config:
    activate:
      on-profile: docker   # 이 블록은 docker 프로필일 때만 적용

  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://db:5432/ydanawa_db}
    #  ↑ 컨테이너 내부에서는 localhost 대신 Docker 서비스명 "db" 사용

app:
  grpc:
    host: ${APP_GRPC_HOST:library-scraper}  # 다른 컨테이너의 서비스명으로 통신
  images:
    directory: ${CRAWLER_OUT:/app/image-crawler}  # 리눅스 경로
```

---

## 4. 진입점 (ComApplication.java)

```java
package yju.danawa.com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication   // 세 가지 어노테이션의 합성:
// @Configuration       ← 이 클래스를 설정 클래스로 등록
// @EnableAutoConfiguration ← Spring Boot 자동 설정 활성화 (DB, Security 등)
// @ComponentScan       ← 하위 패키지의 @Component, @Service, @Repository 등 자동 등록
@EnableScheduling        // @Scheduled 어노테이션 사용 허용 (주기적 작업)
public class ComApplication {

    public static void main(String[] args) {
        // Spring IoC 컨테이너(ApplicationContext)를 생성하고 앱 실행
        SpringApplication.run(ComApplication.class, args);
    }
}
```

---

## 5. 도메인 엔티티 레이어

> **엔티티(Entity)**: DB 테이블과 1:1로 매핑되는 Java 클래스. JPA가 객체 ↔ 테이블 변환을 담당.

### 5-1. Book.java

```java
package yju.danawa.com.domain;

import jakarta.persistence.*;        // JPA 어노테이션 모음
import java.time.Instant;
import java.time.LocalDate;

@Entity                              // JPA 관리 엔티티로 선언
@Table(name = "books", indexes = {   // 매핑할 테이블명 + 인덱스 정보
        @Index(name = "idx_books_title",          columnList = "title"),
        @Index(name = "idx_books_author",         columnList = "author"),
        @Index(name = "idx_books_title_norm_trgm", columnList = "title_norm")
})
public class Book {

    @Id                              // PK 컬럼 선언
    @Column(length = 32, nullable = false)
    private String isbn;             // 예: "9788997170692"

    @Column(nullable = false)
    private String title;            // 원본 제목 (표시용)

    /** 검색용 정규화 제목 — 소문자 + NFKC 유니코드 정규화 */
    @Column(name = "title_norm", nullable = false, length = 500)
    private String titleNorm;        // Java 필드명(camelCase) → DB 컬럼명(snake_case) 지정

    private String author;
    private String publisher;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "published_date")
    private LocalDate publishedDate; // DB: DATE 타입

    private Double price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;       // DB: TIMESTAMPTZ — Instant는 타임존 없는 UTC 시각

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Book() {}                 // JPA가 객체 생성 시 기본 생성자 필수

    public Book(String isbn, String title, ...) {
        this.isbn = isbn;
        this.title = title;
        this.titleNorm = normalizeTitle(title);  // title 저장 시 자동으로 정규화
        ...
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 제목을 검색용으로 정규화하는 내부 메서드
    private static String normalizeTitle(String t) {
        if (t == null) return "";
        // NFKC: 유니코드 호환 분해+합성 — 전각문자·이형 글자를 표준형으로 통일
        String n = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFKC);
        return n.toLowerCase(java.util.Locale.ROOT)  // 언어에 무관한 소문자 변환
                .replaceAll("\\s+", " ")              // 연속 공백을 하나로
                .trim();
    }

    // setTitle 호출 시 titleNorm도 자동 갱신 — 직접 setTitleNorm 호출 불필요
    public void setTitle(String title) {
        this.title = title;
        this.titleNorm = normalizeTitle(title);
    }

    // ... 나머지 getter/setter 생략 (단순 필드 접근)
}
```

---

### 5-2. User.java

```java
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username")  // 로그인 조회 가속
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // DB AUTO_INCREMENT (BIGSERIAL)
    @Column(name = "user_id")
    private Long userId;

    // insertable = false, updatable = false: JPA가 이 컬럼을 INSERT/UPDATE에 포함시키지 않음
    // id 컬럼이 DB에 존재하지만 user_id로 대체됐을 때 읽기 전용으로 유지
    @Column(name = "id", insertable = false, updatable = false)
    private Long legacyId;

    @Column(nullable = false, unique = true, length = 64)
    private String username;                // 중복 불가 (UNIQUE 제약)

    @Column(name = "password", nullable = false, length = 255)
    private String password;               // BCrypt 해시값 저장 (평문 절대 저장 금지)

    @Column(length = 255)
    private String email;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 255)
    private String department;             // 소속 학과

    @Column(name = "student_id", length = 255)
    private String studentId;             // 학번

    @Column(length = 255)
    private String phone;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;            // false: 계정 비활성화

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked;             // true: 잠긴 계정 (로그인 불가)

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts;  // 로그인 실패 횟수

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime lastLoginAt;    // 마지막 로그인 시각

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime updatedAt;

    // User : UserRole = 1 : N 관계
    // mappedBy = "user": UserRole.user 필드가 FK를 관리함 (연관관계의 주인)
    // FetchType.LAZY: getRoles() 호출 전까지 DB 조회 안 함 (성능 최적화)
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserRole> roles = new ArrayList<>();
}
```

---

### 5-3. UserRole.java

```java
@Entity
@Table(name = "user_roles", indexes = {
        @Index(name = "idx_user_roles_user_id", columnList = "user_id")
})
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    // N:1 관계 — 여러 UserRole이 하나의 User에 속함
    // FetchType.LAZY: getUser() 호출 전까지 User 정보 로드 안 함
    // @JoinColumn: 실제 FK 컬럼명 지정 (user_roles.user_id → users.user_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "user_id")
    private User user;

    @Column(name = "role_name", nullable = false, length = 255)
    private String roleName;   // 예: "ROLE_ADMIN", "ROLE_USER"

    @Column(name = "created_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime createdAt;
}
```

---

### 5-4. ClickLog.java (클릭 로그)

```java
@Entity
@Table(name = "click_logs", indexes = {
        @Index(name = "idx_clicklog_isbn",           columnList = "isbn"),
        @Index(name = "idx_clicklog_target_channel", columnList = "target_channel")
})
public class ClickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "click_id")
    private Long clickId;

    @Column(length = 32)
    private String isbn;             // 클릭된 도서의 ISBN

    @Column(name = "target_channel")
    private String targetChannel;   // 클릭 대상 채널 (예: "aladin", "kyobo")

    @Column(name = "slider_value")
    private Double sliderValue;     // 슬라이더 값 (가격비교 UI 관련)

    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;  // 클릭 발생 시각
}
```

---

### 5-5. SearchLog.java (검색 로그)

```java
@Entity
@Table(name = "search_logs", indexes = {
        @Index(name = "idx_searchlog_user_dept",  columnList = "user_dept"),
        @Index(name = "idx_searchlog_search_time", columnList = "search_time")
})
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    private String keyword;          // 사용자가 입력한 검색어

    @Column(name = "user_dept")
    private String userDept;         // 검색한 사용자의 학과

    @Column(name = "search_time", columnDefinition = "TIMESTAMP")
    private LocalDateTime searchTime;
}
```

---

### 5-6. LibraryHoldingCache.java (도서관 소장 캐시)

```java
@Entity
@Table(name = "library_holding_cache")
public class LibraryHoldingCache {

    @Id
    @Column(length = 32, nullable = false)
    private String isbn;             // PK = ISBN (ISBN당 최신 소장 정보 1건)

    @Column(nullable = false)
    private boolean found;           // 소장 여부

    @Column(nullable = false)
    private boolean available;       // 대출 가능 여부

    @Column(name = "status_code", length = 50, nullable = false)
    private String statusCode = "UNKNOWN";  // "AVAILABLE", "ON_LOAN", "NOT_OWNED" 등

    @Column(name = "status_text", length = 100)
    private String statusText;       // 사람이 읽는 상태 (예: "대출가능")

    @Column(length = 255)
    private String location;         // 소장 위치 (예: "제1자료실")

    @Column(name = "call_number", length = 255)
    private String callNumber;       // 청구기호 (서가에서 위치 찾는 번호)

    @Column(name = "detail_url", length = 2048)
    private String detailUrl;        // 도서관 상세 페이지 링크

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;       // 마지막으로 크롤링한 시각

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 캐시가 아직 유효한지 확인 — 6시간(21600초) TTL */
    public boolean isValid() {
        return checkedAt != null &&
               checkedAt.plusSeconds(6 * 3600).isAfter(Instant.now());
        // checkedAt + 6시간이 현재 시각보다 미래이면 아직 유효
    }
}
```

---

### 5-7. BookImage.java (도서 표지 이미지)

```java
@Entity
@Table(name = "book_image", uniqueConstraints = {
        // 같은 책에 동일한 이미지(sha256 동일)가 중복 저장되지 않도록 보장
        @UniqueConstraint(name = "uk_book_image_book_sha256", columnNames = { "book_isbn", "sha256" })
})
public class BookImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_isbn", nullable = false, referencedColumnName = "isbn")
    private Book book;               // N:1 — 여러 이미지가 한 도서에 속할 수 있음

    @Column(nullable = false, length = 50)
    private String kind;             // 이미지 종류 (예: "cover")

    @Column(nullable = false, length = 50)
    private String source;           // 출처 (예: "aladin", "crawler")

    @Column(name = "content_type", length = 100)
    private String contentType;      // MIME 타입 (예: "image/jpeg")

    private Integer width;
    private Integer height;

    @Column(nullable = false, length = 64)
    private String sha256;           // 이미지 파일 해시 — 중복 감지에 사용

    // @Basic(fetch = FetchType.LAZY): 이 필드를 가져올 때까지 DB에서 읽지 않음
    // 이미지 바이너리는 크기가 크므로 실제 필요할 때만 로드
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "bytes", nullable = false, columnDefinition = "bytea")
    private byte[] bytes;            // 실제 이미지 바이트 배열

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

---

### 5-8. CampusNotice.java (캠퍼스 공지사항)

```java
@Entity
@Table(name = "campus_notices", indexes = {
        // is_active 와 posted_date DESC 복합 인덱스 — 활성 공지를 날짜 역순으로 조회
        @Index(name = "idx_campus_notices_active_date", columnList = "is_active, posted_date DESC")
})
public class CampusNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 게시글 원본 ID (학교 홈페이지의 BOARDID). UNIQUE → 같은 공지 중복 방지 */
    @Column(name = "board_id", nullable = false, unique = true, length = 64)
    private String boardId;

    @Column(nullable = false, length = 500)
    private String title;            // 공지 제목

    @Column(name = "image_url", nullable = false, length = 2000)
    private String imageUrl;         // 대표 이미지 URL

    @Column(name = "link_url", length = 2000)
    private String linkUrl;          // 원문 링크

    @Column(name = "posted_date", length = 32)
    private String postedDate;       // 게시일 (문자열로 저장)

    @Column(name = "is_active", nullable = false)
    private boolean active = true;   // false이면 배너에서 숨김

    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt = Instant.now();  // 크롤링한 시각

    // protected 접근자: 외부에서 직접 생성 불가 — 생성자 강제 사용
    protected CampusNotice() {}

    public CampusNotice(String boardId, String title, String imageUrl, String linkUrl, String postedDate) {
        this.boardId    = boardId;
        this.title      = title;
        this.imageUrl   = imageUrl;
        this.linkUrl    = linkUrl;
        this.postedDate = postedDate;
        this.active     = true;
        this.crawledAt  = Instant.now();
    }
}
```

---

## 6. DTO 레이어

> **DTO(Data Transfer Object)**: 레이어 간 데이터를 운반하는 단순 객체.
> DB 엔티티를 그대로 Controller에 노출하면 보안 및 구조 문제가 생기므로 DTO로 변환.

### 6-1. BookDto.java

```java
// record: Java 16+ 불변 데이터 클래스 — equals, hashCode, toString 자동 생성
// 생성자 파라미터가 곧 필드 (setter 없음, getter는 isbn(), title() 등으로 호출)
public record BookDto(
        String isbn,          // ISBN-13
        String title,         // 제목
        String author,        // 저자
        String publisher,     // 출판사
        String imageUrl,      // 표지 URL
        LocalDate publishedDate,  // 출판일
        Double price          // 정가
) {}
```

---

## 7. Repository 레이어

> **JpaRepository<T, ID>**: Spring Data JPA가 제공하는 기본 CRUD 인터페이스.
> 인터페이스만 선언하면 구현체를 Spring이 자동으로 주입.

### 7-1. BookRepository.java

```java
public interface BookRepository extends JpaRepository<Book, String> {
    // JpaRepository<엔티티, PK타입> 상속만으로 save, findById, findAll, delete 등 제공

    // 메서드 이름 규칙으로 쿼리 자동 생성:
    // findBy + 필드명 → WHERE isbn = ?
    Optional<Book> findByIsbn(String isbn);

    // ContainingIgnoreCase → LIKE '%?%' AND LOWER(title) = LOWER(?)
    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // @Query: 복잡한 쿼리는 직접 작성 (nativeQuery = true → 순수 SQL)
    @Query(value = "SELECT * FROM books b " +
            "WHERE REPLACE(LOWER(b.isbn), ' ', '')    ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%') " +
            // ↑ isbn에서 공백 제거 후 소문자로 바꾼 값이 검색어를 포함하는지 확인
            "OR    REPLACE(LOWER(b.title), ' ', '')   ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%') " +
            "OR    REPLACE(LOWER(b.author), ' ', '')  ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%') " +
            "OR    REPLACE(LOWER(b.publisher), ' ', '') ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%')",
            nativeQuery = true)
    List<Book> searchByKeyword(@Param("q") String q);
    // @Param("q"): :q 플레이스홀더에 바인딩할 파라미터 이름 지정

    /** 인기 도서 Fallback — 최신 등록 순으로 N권 */
    @Query(value = "SELECT * FROM books ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<Book> findPopularBooks(@Param("limit") int limit);

    /** trigram 유사도 점수 기반 정렬 (PostgreSQL similarity 함수 활용) */
    @Query(value = "SELECT * FROM books " +
            "WHERE title_norm ILIKE concat('%', :q, '%') " +
            "ORDER BY similarity(title_norm, :q) DESC, isbn ASC",
            countQuery = "SELECT COUNT(*) FROM books WHERE title_norm ILIKE concat('%', :q, '%')",
            nativeQuery = true)
    Page<Book> searchByTitleNorm(@Param("q") String q, Pageable pageable);

    /** 무한 스크롤 첫 페이지 — ISBN 오름차순 */
    @Query(value = "SELECT * FROM books ORDER BY isbn ASC LIMIT :limit", nativeQuery = true)
    List<Book> findTopBooks(@Param("limit") int limit);

    /** 무한 스크롤 다음 페이지 — 커서(마지막 ISBN) 이후 데이터 */
    @Query(value = "SELECT * FROM books WHERE isbn > :cursor ORDER BY isbn ASC LIMIT :limit", nativeQuery = true)
    List<Book> findBooksAfterCursor(@Param("cursor") String cursor, @Param("limit") int limit);
    // isbn > :cursor → PK가 문자열이므로 사전순 비교
}
```

---

### 7-2. ClickLogRepository.java

```java
public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {

    // JPQL (Java Persistence Query Language) — 엔티티/필드명으로 작성 (테이블명 아님)
    @Query("SELECT new yju.danawa.com.dto.ChannelSliderStatsDto(cl.targetChannel, AVG(cl.sliderValue), COUNT(cl)) " +
            // ↑ DTO 생성자를 직접 호출하는 "Projection" 패턴
            "FROM yju.danawa.com.domain.ClickLog cl " +
            "WHERE cl.createdAt BETWEEN :start AND :end " +
            "GROUP BY cl.targetChannel " +
            "ORDER BY AVG(cl.sliderValue) DESC")
    List<ChannelSliderStatsDto> findChannelSliderStatsBetween(
            @Param("start") LocalDateTime start,
            @Param("end")   LocalDateTime end);

    @Query("SELECT new yju.danawa.com.dto.BookClickCountDto(cl.isbn, COUNT(cl)) " +
            "FROM yju.danawa.com.domain.ClickLog cl " +
            "WHERE cl.createdAt BETWEEN :start AND :end " +
            "GROUP BY cl.isbn " +
            "ORDER BY COUNT(cl) DESC")
    List<BookClickCountDto> findTopBooksByClicksBetween(
            @Param("start") LocalDateTime start,
            @Param("end")   LocalDateTime end);
}
```

---

### 7-3. SearchLogRepository.java

```java
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    // 메서드 이름으로 쿼리 자동 생성
    // WHERE user_dept = ? AND search_time BETWEEN ? AND ?
    long countByUserDeptAndSearchTimeBetween(String userDept, LocalDateTime start, LocalDateTime end);

    // 학과별 검색 횟수 집계 — 통계 화면용
    @Query("SELECT new yju.danawa.com.dto.DeptSearchCountDto(sl.userDept, COUNT(sl)) " +
            "FROM yju.danawa.com.domain.SearchLog sl " +
            "WHERE sl.searchTime BETWEEN :start AND :end " +
            "GROUP BY sl.userDept " +
            "ORDER BY COUNT(sl) DESC")
    List<DeptSearchCountDto> findSearchCountsByDeptBetween(
            @Param("start") LocalDateTime start,
            @Param("end")   LocalDateTime end);
}
```

---

## 8. Service 레이어

### 8-1. JwtService.java (JWT 토큰 생성·검증)

> **JWT(JSON Web Token)**: 서버가 발급하는 자가 서명 토큰.
> 헤더.페이로드.서명 세 부분이 `.`으로 연결된 Base64 문자열.

```java
@Service  // Spring Bean 등록 — 싱글톤으로 관리됨
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    // @Value: application.yml의 값을 생성자 파라미터로 주입
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
        // HMAC-SHA 알고리즘용 키 생성 (secret 문자열 → SecretKey 객체)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    /** 로그인 성공 시 토큰 발급 */
    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)                              // 토큰 주체(누구의 토큰인지)
                .issuedAt(Date.from(now))                       // 발급 시각
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))  // 만료 시각
                .signWith(key)                                  // HMAC-SHA256으로 서명
                .compact();                                     // "header.payload.signature" 문자열 반환
    }

    /** 토큰에서 username(subject) 추출 */
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)          // 같은 키로 서명 검증
                .build()
                .parseSignedClaims(token) // 서명 검증 + 파싱 (유효하지 않으면 JwtException 발생)
                .getPayload()
                .getSubject();            // subject 필드 = username
    }

    /** 토큰이 유효한지 확인 (서명 검증 + 만료 체크) */
    public boolean isTokenValid(String token) {
        try {
            extractUsername(token);  // 파싱에 성공하면 유효한 토큰
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // 서명 불일치, 만료, null 등 → 유효하지 않음
            return false;
        }
    }
}
```

---

### 8-2. UserService.java

```java
@Service
public class UserService {

    private final UserRepository userRepository;

    // 생성자 주입(Constructor Injection): 의존성을 생성자로 받아 final 필드에 저장
    // @Autowired 없어도 파라미터가 1개이면 Spring이 자동 주입
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // @Cacheable: 캐시에 결과가 있으면 DB 조회 없이 반환
    // cacheNames = "users" → CacheConfig에서 정의한 캐시 이름
    // key = "#username == null ? '' : #username.toLowerCase()" → 캐시 키 표현식 (SpEL)
    @Cacheable(cacheNames = "users", key = "#username == null ? '' : #username.toLowerCase()")
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();  // null/빈 문자열이면 빈 Optional 반환
        }
        return userRepository.findByUsernameIgnoreCase(username.trim());
        // IgnoreCase: 대소문자 무시 — "Admin" == "admin" == "ADMIN"
    }

    // @CacheEvict: save 후 캐시에서 해당 항목 삭제 → 다음 조회 시 최신 DB 값 반영
    @CacheEvict(cacheNames = "users", key = "#user.username == null ? '' : #user.username.toLowerCase()")
    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) return false;
        return userRepository.existsByUsernameIgnoreCase(username.trim());
    }

    /** 비밀번호 중복 체크 — 다른 사용자와 같은 비밀번호 사용 금지 */
    public boolean isPasswordInUse(String rawPassword, PasswordEncoder passwordEncoder) {
        if (rawPassword == null || rawPassword.isBlank()) return false;
        // DB의 모든 해시를 가져와서 입력된 비밀번호와 일치하는 것이 있는지 검사
        return userRepository.findAllPasswordHashes().stream()
                .anyMatch(hash -> passwordEncoder.matches(rawPassword, hash));
        // BCrypt matches: 해시 → 원문 복호화는 불가능, 입력값을 같은 방식으로 해싱해서 비교
    }
}
```

---

### 8-3. BookService.java

```java
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookImageRepository bookImageRepository;

    // 검색 결과 + 폴백 여부를 함께 반환하는 레코드
    // record: 불변 데이터 홀더 — equals/hashCode/toString 자동 생성
    public record SearchResult(List<BookDto> books, boolean isFallback) {}

    @Cacheable(cacheNames = "books", key = "#keyword")
    public List<BookDto> search(String keyword) {
        return searchWithFallback(keyword).books();
    }

    /**
     * 검색 결과가 없으면 인기 도서 Fallback 반환.
     * isFallback == true → 프론트에서 "검색 결과 없음, 이런 책은 어떠세요?" 메시지 표시
     */
    public SearchResult searchWithFallback(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new SearchResult(Collections.emptyList(), false);
        }
        String normalized = keyword.trim();
        List<Book> books = bookRepository.searchByKeyword(normalized);

        if (!books.isEmpty()) {
            return new SearchResult(toDtoList(books), false);  // 정상 검색 결과
        }

        // 검색 결과 없음 → 인기 도서 5권으로 대체
        List<Book> popular = bookRepository.findPopularBooks(5);
        return new SearchResult(toDtoList(popular), true);  // isFallback = true
    }

    /** Entity → DTO 변환 (이미지 URL 처리 포함) */
    private List<BookDto> toDtoList(List<Book> books) {
        Map<String, String> imageUrlByIsbn = loadImageUrlByIsbn(books);
        return books.stream()
                .map(book -> new BookDto(
                        book.getIsbn(),
                        book.getTitle(),
                        book.getAuthor(),
                        book.getPublisher(),
                        resolveImageUrl(book, imageUrlByIsbn),  // URL 우선순위 결정
                        book.getPublishedDate(),
                        book.getPrice()))
                .collect(Collectors.toList());
    }

    /**
     * Cursor-based Pagination (커서 기반 페이지네이션)
     * 오프셋 방식(OFFSET 100 LIMIT 30)과 달리 커서(마지막 ISBN) 이후 데이터를 가져옴
     * → 데이터 추가/삭제 시 페이지 중복·누락 없음, 대용량에서 성능 우수
     */
    public InfiniteScrollResponse<BookDto> getBooksWithCursor(String cursor, int limit) {
        List<Book> books;
        if (cursor == null || cursor.isBlank()) {
            books = bookRepository.findTopBooks(limit);         // 첫 페이지
        } else {
            books = bookRepository.findBooksAfterCursor(cursor, limit);  // 다음 페이지
        }
        // ... DTO 변환 후 InfiniteScrollResponse로 래핑
    }

    /** 이미지 URL 결정: books.image_url → book_image 테이블 순으로 탐색 */
    private String resolveImageUrl(Book book, Map<String, String> imageUrlByIsbn) {
        String url = null;
        if (book.getImageUrl() != null && !book.getImageUrl().isBlank()) {
            url = book.getImageUrl();              // 1순위: books 테이블의 image_url
        } else {
            url = imageUrlByIsbn.get(book.getIsbn());  // 2순위: book_image 테이블의 이미지
        }
        return normalizeStoredImageUrl(url);
    }

    /** 파일명만 저장된 URL을 API 경로로 변환 */
    private String normalizeStoredImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/api/") || url.startsWith("/images/")) {
            return url;  // 이미 완전한 URL이면 그대로 반환
        }
        // "893070599_l.jpg" 같은 파일명 → "/api/images/by-name/893070599_l.jpg"
        if (!url.contains("/") && url.contains(".")) {
            return "/api/images/by-name/" + url;
        }
        return url;
    }
}
```

---

### 8-4. ExternalBookService.java (외부 API 검색)

```java
@Service
public class ExternalBookService {

    // 알라딘 API 재시도 설정
    private static final int  ALADIN_MAX_RETRIES     = 3;     // 최대 3회 재시도
    private static final long ALADIN_RETRY_BASE_MS   = 400L;  // 첫 번째 대기: 400ms
    // 두 번째: 800ms, 세 번째: 1200ms (선형 증가)

    private final WebClient webClient;    // 비동기 HTTP 클라이언트 (Spring WebFlux)
    private final String kakaoRestApiKey;
    private final String googleApiKey;
    private final String aladinTtbKey;

    @Cacheable(cacheNames = "externalBooks", key = "#query + '::' + #source")
    public List<BookDto> search(String query, String source) {
        String normalizedSource = Optional.ofNullable(source).orElse("auto").toLowerCase();
        return switch (normalizedSource) {    // switch 표현식 (Java 14+)
            case "aladin" -> searchAladin(query);
            case "google" -> searchGoogle(query);
            case "kakao"  -> searchKakao(query);
            case "auto"   -> {
                // 알라딘 우선(표지 품질 최고), 카카오, 구글 순으로 검색 후 합병
                List<BookDto> aladinResult = safeSearchAladin(query);
                List<BookDto> kakaoResult  = safeSearchKakao(query);
                List<BookDto> googleResult = safeSearchGoogle(query);
                yield mergeByPriority(aladinResult, kakaoResult, googleResult);
                // yield: switch 표현식에서 값을 반환하는 키워드
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 소스: " + source);
        };
    }

    /** 카카오 도서 검색 API 호출 */
    private List<BookDto> searchKakao(String query) {
        KakaoBookResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("dapi.kakao.com")
                        .path("/v3/search/book")
                        .queryParam("query", query)
                        .queryParam("size", 50)
                        .build())
                .header("Authorization", "KakaoAK " + kakaoRestApiKey)  // REST API 인증
                .retrieve()
                .onStatus(                        // HTTP 4xx/5xx 응답 처리
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> { throw new ResponseStatusException(...); }
                )
                .bodyToMono(KakaoBookResponse.class)  // 응답 JSON → KakaoBookResponse 역직렬화
                .block();                             // 비동기를 동기로 전환 (블로킹 대기)

        return response.documents().stream()
                .map(doc -> {
                    String isbn = extractIsbn(doc.isbn());  // "ISBN10 ISBN13" → ISBN-13 추출
                    return new BookDto(isbn, doc.title(), ...);
                })
                .collect(Collectors.toList());
    }

    /** 알라딘 API — 429/503 등 일시적 오류 시 재시도 */
    private AladinItemSearchResponse callAladinWithRetry(String searchQuery, String queryType) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= ALADIN_MAX_RETRIES; attempt++) {
            try {
                return webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("http")
                                .host("www.aladin.co.kr")
                                .path("/ttb/api/ItemSearch.aspx")
                                .queryParam("ttbkey", aladinTtbKey)
                                .queryParam("Query", searchQuery)
                                .queryParam("QueryType", queryType)  // "ISBN13" or "Title"
                                .queryParam("Cover", "Big")          // 고화질 표지
                                .queryParam("output", "js")          // JSON 응답
                                .build())
                        .retrieve()
                        .bodyToMono(AladinItemSearchResponse.class)
                        .block();
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryableAladinStatus(status) || attempt == ALADIN_MAX_RETRIES) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ...);
                }
                long delay = ALADIN_RETRY_BASE_MS * attempt;  // 재시도 대기
                sleepSilently(delay);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "알라딘 API 재시도 실패");
    }

    /** 재시도 가능한 HTTP 상태 코드 판단 */
    private boolean isRetryableAladinStatus(int status) {
        return status == 403    // Forbidden (일시적 차단)
            || status == 408    // Request Timeout
            || status == 429    // Too Many Requests
            || status >= 500;   // 서버 에러
    }

    /** ISBN-10 → ISBN-13 변환 (978 접두어 추가 + 체크 디지트 재계산) */
    private String convertIsbn10ToIsbn13(String isbn10) {
        String base = "978" + isbn10.substring(0, 9);  // 체크 디지트 제외하고 978 붙이기
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(base.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;  // 홀수 위치는 가중치 3
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return base + checkDigit;
    }

    /** 3개 소스 결과 합병 — 동일 ISBN이면 이미지가 있는 쪽 우선 */
    private List<BookDto> mergeByPriority(List<BookDto> primary, ...) {
        Map<String, BookDto> merged = new LinkedHashMap<>();  // 입력 순서 유지
        for (BookDto item : primary) {
            merged.put(bookKey(item), item);  // 알라딘 결과 먼저 삽입
        }
        applyPriorityFill(merged, secondary);  // 카카오: 없는 것 추가 / 이미지 보완
        applyPriorityFill(merged, tertiary);   // 구글: 없는 것 추가 / 이미지 보완
        return new ArrayList<>(merged.values());
    }
}
```

---

### 8-5. YjuLibraryService.java (도서관 소장 조회)

> 조회 순서: **메모리 캐시 → DB 캐시 → Playwright gRPC 크롤링**

```java
@Service
public class YjuLibraryService {

    // 메모리 캐시 (앱 재시작 시 초기화, 10분 TTL)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    // ConcurrentHashMap: 멀티스레드 환경에서 안전한 HashMap
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;  // 10분

    /** DB 캐시 TTL: 6시간 (Playwright 크롤링 최소화) */
    private static final long DB_CACHE_TTL_SECONDS = 6 * 3600;

    public LibraryAvailability checkAvailability(String isbn, String title) {
        if (isbn == null || isbn.isBlank()) {
            return LibraryAvailability.notFound(YJU_LIBRARY_SEARCH_URL);
        }
        isbn = isbn.replaceAll("[^0-9]", "").trim();  // 하이픈·공백 제거
        String cacheKey = isbn;

        // ── 1단계: 메모리 캐시 ──
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.data;  // 캐시 히트 → 즉시 반환
        }

        // ── 2단계: DB 캐시 ──
        Instant cutoff = Instant.now().minusSeconds(DB_CACHE_TTL_SECONDS);
        // checked_at이 6시간 이내인 레코드가 있으면 DB에서 반환
        Optional<LibraryHoldingCache> dbCached = holdingCacheRepository
                .findByIsbnAndCheckedAtAfter(cacheKey, cutoff);
        if (dbCached.isPresent()) {
            LibraryAvailability fromDb = new LibraryAvailability(...);
            cache.put(cacheKey, new CacheEntry(fromDb));  // 메모리 캐시에도 저장
            return fromDb;
        }

        // ── 3단계: Playwright gRPC 크롤링 (느림, 최후 수단) ──
        if (isbn.length() == 10) {
            isbn = convertIsbn10ToIsbn13(isbn);  // ISBN-10이면 ISBN-13으로 변환 후 검색
        }
        LibraryAvailability result = callScraperService(isbn, null);

        // 캐시 저장 (다음 요청부터 Playwright 불필요)
        cache.put(cacheKey, new CacheEntry(result));  // 메모리 캐시
        saveToDbCache(isbn, result);                  // DB 캐시

        return result;
    }

    /** gRPC로 Python 스크래퍼 서비스 호출 */
    private LibraryAvailability callScraperService(String isbn, String title) {
        LibraryResponse grpcResponse = grpcClient.checkLibrary(isbn, title, null);
        // gRPC: Protocol Buffers 기반 RPC — REST보다 빠르고 타입 안전
        return new LibraryAvailability(
                grpcResponse.getFound(),
                grpcResponse.getAvailable(),
                grpcResponse.getLocation(),
                ...
        );
    }

    // 소장 정보 DTO (불변 클래스)
    public static class LibraryAvailability {
        private final boolean found;       // 소장 여부
        private final boolean available;   // 대출 가능 여부
        private final String location;     // 위치 (예: "제1자료실")
        private final String callNumber;   // 청구기호
        private final String detailUrl;    // 도서관 링크
        private final String statusCode;   // "AVAILABLE", "ON_LOAN", "NOT_OWNED", "UNKNOWN"
        // ...

        // 정적 팩토리 메서드 — "소장 없음" 케이스를 명확하게 표현
        public static LibraryAvailability notFound(String searchUrl) {
            return new LibraryAvailability(false, false, null, null, searchUrl, null, "NOT_OWNED");
        }
        public static LibraryAvailability error(String message, String searchUrl) {
            return new LibraryAvailability(false, false, null, null, searchUrl, message, "ERROR");
        }
    }

    // 메모리 캐시 항목 — 만료 시각 포함
    private static class CacheEntry {
        private final LibraryAvailability data;
        private final long expiresAt;  // System.currentTimeMillis() + TTL

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
```

---

### 8-6. LibraryGrpcClient.java (gRPC 클라이언트)

```java
@Service
public class LibraryGrpcClient {

    private static final int GRPC_DEADLINE_SECONDS      = 20;  // 일반 조회 타임아웃
    private static final int EBOOK_GRPC_DEADLINE_SECONDS = 60; // 전자책 조회 타임아웃 (더 오래 걸림)

    @Value("${app.grpc.host:ydanawa-library-scraper}")
    private String grpcHost;

    @Value("${app.grpc.port:9090}")
    private int grpcPort;

    private ManagedChannel channel;              // gRPC TCP 연결 채널
    private LibraryServiceGrpc.LibraryServiceBlockingStub blockingStub;  // 동기 호출용 stub

    @PostConstruct  // Bean 생성 완료 후 자동 실행
    public void init() {
        channel = ManagedChannelBuilder
                .forAddress(grpcHost, grpcPort)
                .usePlaintext()  // TLS 없이 평문 통신 (내부망에서는 충분)
                .build();
        blockingStub = LibraryServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy  // Bean 소멸 전 실행 (앱 종료 시 연결 정리)
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    public LibraryResponse checkLibrary(String isbn, String title, String author) {
        LibraryRequest.Builder requestBuilder = LibraryRequest.newBuilder();
        if (isbn != null && !isbn.isBlank()) requestBuilder.setIsbn(isbn);
        return blockingStub
                .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)  // 타임아웃 설정
                .getLibraryStatus(requestBuilder.build());
        // 타임아웃 초과 시 StatusRuntimeException 발생
    }

    // gRPC 응답을 Java 레코드로 변환하는 내부 레코드들
    public record BookResult(String title, String author, String isbn, int price, ...) {}
    public record BookInfoResult(String isbn13, String availabilityStatus, ...) {}
    public record EbookStatusResult(String title, boolean found, int totalHoldings, ...) {}
}
```

---

### 8-7. BookDataLoaderService.java (초기 데이터 로딩)

```java
@Service
public class BookDataLoaderService implements CommandLineRunner {
    // CommandLineRunner: 앱 시작 완료 후 run() 자동 실행

    private static final String[] SEED_KEYWORDS = {
        // IT, 문학, 자기계발, 과학 등 다양한 분야 키워드
        "자바", "파이썬", "인간실격", "데미안", ...
    };

    @Override
    public void run(String... args) {
        long existingCount = bookRepository.count();

        // DB에 도서가 50권 미만이면 외부 API에서 로드
        if (existingCount < 50) {
            loadBooksFromExternalApi();
        }
        // 이미 충분하면 건너뜀 → 재시작 시 불필요한 API 호출 방지
    }

    private void loadBooksFromExternalApi() {
        List<Book> booksToSave = new ArrayList<>();
        int totalFetched = 0;

        for (String keyword : SEED_KEYWORDS) {
            List<BookDto> books = externalBookService.search(keyword, "aladin");
            if (books.isEmpty()) {
                books = externalBookService.search(keyword, "kakao");  // 알라딘 실패 시 카카오 시도
            }

            for (BookDto dto : books) {
                if (dto.isbn() != null && !bookRepository.existsById(dto.isbn())) {
                    // DB에 없는 책만 저장 (중복 방지)
                    Book book = new Book();
                    book.setIsbn(dto.isbn());
                    book.setTitle(dto.title());  // setTitle이 titleNorm도 자동 설정
                    // ...
                    booksToSave.add(book);
                    totalFetched++;

                    // 50권씩 배치 저장 (메모리 관리)
                    if (booksToSave.size() >= 50) {
                        bookRepository.saveAll(booksToSave);
                        booksToSave.clear();
                    }
                }
            }

            Thread.sleep(500);  // API 호출 간격 (429 Too Many Requests 방지)

            if (totalFetched >= 200) break;  // 200권 이상 수집하면 중단
        }

        if (!booksToSave.isEmpty()) {
            bookRepository.saveAll(booksToSave);  // 나머지 저장
        }
    }
}
```

---

## 9. Controller(Web) 레이어

### 9-1. HomeController.java

```java
@RestController  // @Controller + @ResponseBody — 메서드 반환값을 JSON으로 직렬화
public class HomeController {

    @GetMapping("/")   // HTTP GET "/" 요청 처리
    public Map<String, Object> root() {
        return Map.of(          // Java 9+의 불변 Map 생성
                "status",    "ok",
                "service",   "Y-Danawa",
                "timestamp", Instant.now().toString()  // ISO-8601 형식 문자열
        );
    }
}
// 응답 예시: {"status":"ok","service":"Y-Danawa","timestamp":"2026-03-06T12:00:00Z"}
```

---

### 9-2. AuthController.java

```java
@RestController
@RequestMapping("/api/auth")  // 이 컨트롤러의 모든 경로는 /api/auth 로 시작
public class AuthController {

    // 생성자 주입 — 모든 의존성을 생성자에서 받음
    public AuthController(UserService userService, UserProfileService userProfileService,
                          PasswordEncoder passwordEncoder, JwtService jwtService) { ... }

    /** GET /api/auth/validate?username=xxx&studentId=yyy
     *  회원가입 전 중복 확인 */
    @GetMapping("/validate")
    public Map<String, Object> validate(
            @RequestParam(required = false) String username,   // URL 쿼리 파라미터
            @RequestParam(required = false) String studentId
    ) {
        boolean usernameAvailable  = username  == null || username.isBlank()
                || !userService.existsByUsername(username);
        boolean studentIdAvailable = studentId == null || studentId.isBlank()
                || !userService.existsByStudentId(studentId);
        return Map.of("usernameAvailable", usernameAvailable,
                      "studentIdAvailable", studentIdAvailable);
    }

    /** POST /api/auth/register — 회원가입 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)  // 성공 시 HTTP 201 반환
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        // @Valid: 요청 바디의 유효성 검사 (Jakarta Validation)
        // @RequestBody: HTTP 요청 바디 JSON → RegisterRequest 역직렬화

        // 중복 체크
        if (userService.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_EXISTS");
            // HTTP 409 Conflict
        }
        if (userService.existsByStudentId(request.studentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "STUDENT_ID_EXISTS");
        }
        if (userService.isPasswordInUse(request.password(), passwordEncoder)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PASSWORD_IN_USE");
        }

        LocalDateTime now = LocalDateTime.now();
        User saved = userService.save(new User(
                request.username(),
                passwordEncoder.encode(request.password()),  // 비밀번호 BCrypt 해싱
                request.email(),
                request.fullName(),
                request.department(),
                request.studentId(),
                request.phone(),
                true,   // isEnabled: 즉시 활성화
                false,  // isLocked: 잠금 해제 상태
                0,      // failedLoginAttempts: 실패 0회
                null,   // lastLoginAt
                now, now
        ));
        String token = jwtService.generateToken(saved.getUsername());
        return new LoginResponse(saved.getUsername(), "ok", token,
                userProfileService.getRoleNames(saved.getUserId()));
    }

    /** POST /api/auth/login — 로그인 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        // orElseThrow: Optional이 비어 있으면 예외 발생 → HTTP 401

        // 비밀번호 검증 (BCrypt 해시 비교)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // 계정 상태 확인
        if (Boolean.FALSE.equals(user.getIsEnabled()) || Boolean.TRUE.equals(user.getIsLocked())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed");
            // HTTP 403 Forbidden
        }

        String token = jwtService.generateToken(user.getUsername());
        return new LoginResponse(user.getUsername(), "ok", token,
                userProfileService.getRoleNames(user.getUserId()));
    }
}
```

---

### 9-3. BookController.java (핵심 Controller)

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    /** GET /api/books/search?q=자바
     *  도서 목록 검색 (Danawa 스타일 리스트) */
    @GetMapping(value = "/search", params = {"q", "!page", "!size"})
    // params = {"q", "!page", "!size"}: q 파라미터는 있고 page,size는 없는 요청
    public Map<String, Object> searchBooks(@RequestParam("q") String keyword) {
        // 1. 로컬 DB 검색
        BookService.SearchResult localResult = bookService.searchWithFallback(keyword);

        // 2. 외부 API 검색 (알라딘·카카오·구글)
        List<BookDto> externalRows;
        try {
            externalRows = externalBookService.search(keyword, "auto");
        } catch (Exception e) {
            externalRows = Collections.emptyList();  // 외부 API 실패해도 로컬 결과 반환
        }

        // 3. 결과 병합 + 중복 제거
        List<BookDto> allRows = new ArrayList<>();
        if (!localResult.isFallback()) allRows.addAll(localResult.books());
        allRows.addAll(externalRows);

        // LinkedHashMap으로 중복 제거 (ISBN을 키로 사용, 정보가 더 완전한 쪽 유지)
        Map<String, SearchListItemResponse> deduped = new LinkedHashMap<>();
        for (BookDto row : allRows) {
            String isbn13 = normalizeIsbn13(row.isbn());
            if (isbn13 == null) continue;  // ISBN-13이 없으면 상세 조회 불가 → 제외
            String key = "isbn13:" + isbn13;

            SearchListItemResponse candidate = new SearchListItemResponse(...);
            SearchListItemResponse prev = deduped.get(key);
            if (prev == null || score(candidate) > score(prev)) {
                deduped.put(key, candidate);  // 더 완전한 정보로 교체
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.copyOf(deduped.values()));
        response.put("fallback", isFallback);
        if (isFallback) {
            response.put("fallbackMessage", "'" + keyword + "'에 대한 검색 결과가 없습니다...");
        }
        return response;
    }

    /** GET /api/books/{isbn13} — 도서 상세 조회 */
    @GetMapping("/{isbn13}")
    public BookDetailResponse getBookDetail(@PathVariable("isbn13") String isbn13,
                                             HttpServletRequest request) {
        // @PathVariable: URL 경로의 {isbn13} 부분을 파라미터로 추출

        String normalized = normalizeIsbn13(isbn13);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 must be exactly 13 digits");
        }

        // IP 기반 레이트 리미팅 (과도한 요청 차단)
        String ip = request.getRemoteAddr();
        String rateKey = ip + ":book-detail:" + normalized;
        if (!libraryRateLimiter.allow(rateKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited");
        }

        // 로컬 DB → 외부 API 순으로 도서 정보 조회
        BookDto book = bookService.findByIsbn13(normalized).orElseGet(() -> {
            List<BookDto> external = externalBookService.search(normalized, "auto");
            return external.stream()
                    .filter(b -> normalized.equals(normalizeIsbn13(b.isbn())))
                    .findFirst()
                    .orElse(null);
        });

        // 도서관 소장 조회 (캐시 → Playwright 순)
        YjuLibraryService.LibraryAvailability availability =
                yjuLibraryService.checkAvailability(normalized, null);

        // 판매처 링크 생성 (알라딘, 교보, YES24)
        VendorLinks vendors = buildVendorLinks(normalized);

        return new BookDetailResponse(normalized, ..., vendors, ...,
                new LibraryDetailResponse(...));
    }

    /** GET /api/books/infinite?cursor=9788000001234&limit=30
     *  무한 스크롤 */
    @GetMapping("/infinite")
    public ResponseEntity<InfiniteScrollResponse<BookDto>> getBooksInfiniteScroll(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit
    ) {
        if (limit > 100) limit = 100;  // 최대 100건으로 제한
        InfiniteScrollResponse<BookDto> response = bookService.getBooksWithCursor(cursor, limit);
        return ResponseEntity.ok(response);  // HTTP 200 + 응답 바디
    }

    /** 정보 완전도 점수 계산 (중복 제거 시 더 완전한 항목 선택에 사용) */
    private int score(SearchListItemResponse item) {
        int score = 0;
        if (item.isbn13()    != null && !item.isbn13().isBlank())    score += 4;
        if (item.thumbUrl()  != null && !item.thumbUrl().isBlank())  score += 3;
        if (item.author()    != null && !item.author().isBlank())    score += 2;
        if (item.publisher() != null && !item.publisher().isBlank()) score += 1;
        return score;
    }

    // 응답용 레코드들 (컨트롤러 내부 클래스)
    public record SearchListItemResponse(String isbn13, String title, String author,
                                          String publisher, String thumbUrl) {}
    public record BookDetailResponse(String isbn13, String title, String author,
                                      String publisher, String coverUrl,
                                      VendorLinks vendors, EbookInfo ebook,
                                      LibraryDetailResponse library) {}
    public record VendorLinks(String aladin, String kyobo, String yes24) {}
}
```

---

### 9-4. LogController.java (클릭 로그)

```java
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final ClickLogRepository clickLogRepository;
    private final String apiKey;   // 설정에서 주입된 API 키

    /** POST /api/logs/click — 도서 클릭 이벤트 저장 */
    @PostMapping("/click")
    @ResponseStatus(HttpStatus.CREATED)  // HTTP 201
    public Map<String, Object> sendClickLog(
            @Valid @RequestBody ClickLogRequest request,
            @RequestHeader(value = "X-API-KEY", required = false) String requestApiKey
            // @RequestHeader: HTTP 헤더값 추출
    ) {
        // API 키 인증 (설정된 경우에만)
        if (apiKey != null && !apiKey.isBlank()) {
            if (requestApiKey == null || !apiKey.equals(requestApiKey)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
            }
        }

        ClickLog log = new ClickLog(
                request.isbn(),
                request.target_channel(),  // 어느 판매처 링크를 클릭했는지
                request.slider_value(),    // 슬라이더 값
                LocalDateTime.now()
        );
        ClickLog saved = clickLogRepository.save(log);

        return Map.of("status", "saved", "click_id", saved.getClickId());
    }
}
```

---

## 10. Security 레이어

### 10-1. SecurityConfig.java

```java
@Configuration
public class SecurityConfig {

    @Bean  // Spring이 이 메서드의 반환값을 Bean으로 관리
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            // CSRF 비활성화: JWT + REST API 방식에서는 CSRF 공격이 해당 없음
            .csrf(csrf -> csrf.disable())

            // 세션 생성 안 함: JWT는 서버에 세션을 저장하지 않음 (Stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll()        // API 전체 공개
                .requestMatchers("/images/**").permitAll()     // 이미지 공개
                .requestMatchers("/error").permitAll()         // 에러 페이지 공개
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()  // API 문서 공개
                .requestMatchers("/actuator/health").permitAll()  // 헬스체크 공개
                .anyRequest().authenticated()                 // 나머지는 인증 필요
            )
            .httpBasic(Customizer.withDefaults());  // HTTP Basic 인증도 허용 (개발 편의)

        // JwtAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 끼워 넣음
        // → 모든 요청에서 JWT 먼저 확인
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

---

### 10-2. JwtAuthFilter.java

```java
@Component  // Spring Bean 등록 (싱글톤)
public class JwtAuthFilter extends OncePerRequestFilter {
    // OncePerRequestFilter: 하나의 요청당 정확히 한 번만 실행되는 필터

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // HTTP 요청 헤더에서 Authorization 값 추출
        String header = request.getHeader("Authorization");

        // "Bearer {토큰}" 형식인지 확인
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);  // "Bearer " (7글자) 이후가 토큰

            if (jwtService.isTokenValid(token)) {
                String username = jwtService.extractUsername(token);

                // Spring Security 컨텍스트에 인증 정보 저장
                // (이후 컨트롤러에서 @AuthenticationPrincipal로 꺼내 쓸 수 있음)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,                       // 비밀번호 (인증 완료 후 불필요)
                                Collections.emptyList()     // 권한 목록 (현재 빈 목록)
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 토큰이 없거나 유효하지 않아도 다음 필터로 진행
        // (permitAll 경로는 인증 없이 통과됨)
        filterChain.doFilter(request, response);
    }
}
```

---

## 11. Config 레이어

### 11-1. CacheConfig.java (Caffeine 캐시)

```java
@Configuration
@EnableCaching  // @Cacheable, @CacheEvict 어노테이션 활성화
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // Caffeine: 고성능 인메모리 캐시 라이브러리 (Guava Cache의 후속)
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "books",         // BookService에서 사용
                "externalBooks", // ExternalBookService에서 사용
                "users",         // UserService, UserProfileService에서 사용
                "yjuLibrary",    // 도서관 조회 캐시
                "popularBooks"   // 인기 도서 캐시
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)                     // 최대 1000개 항목 저장
                .expireAfterWrite(Duration.ofHours(24))  // 24시간 후 자동 만료
        );
        return manager;
    }
}
```

---

### 11-2. UserSeedConfig.java (초기 관리자 계정)

```java
@Configuration
// @ConditionalOnProperty: 설정값이 조건을 만족할 때만 이 설정 클래스 활성화
// app.auth.seed-enabled=true 가 설정된 경우에만 초기 계정 생성
@ConditionalOnProperty(name = "app.auth.seed-enabled", havingValue = "true")
public class UserSeedConfig {

    @Bean
    // CommandLineRunner: 앱 시작 완료 후 람다 실행
    public CommandLineRunner seedDefaultUser(UserService userService, PasswordEncoder passwordEncoder) {
        return args -> userService.findByUsername("admin")
                // admin 계정이 없으면 생성, 있으면 건너뜀 (orElseGet)
                .orElseGet(() -> userService.save(new User(
                        "admin",
                        passwordEncoder.encode("admin1234"),  // 해싱 후 저장
                        null, null, null, null, null,
                        true,  // isEnabled
                        false, // isLocked
                        0,     // failedAttempts
                        null,  // lastLoginAt
                        LocalDateTime.now(), LocalDateTime.now()
                )));
    }
}
```

---

## 12. 전체 흐름 요약

### 도서 검색 요청 흐름

```
[브라우저] GET /api/books/search?q=자바
    ↓
[JwtAuthFilter]        토큰 없어도 /api/** 는 permitAll → 통과
    ↓
[BookController]       searchBooks("자바") 호출
    ↓
[BookService]          searchWithFallback("자바")
  → [BookRepository]   searchByKeyword("자바") — DB SQL 실행
    결과 없으면 → findPopularBooks(5) Fallback
    ↓
[ExternalBookService]  search("자바", "auto")
  → Aladin API + Kakao API + Google API 병렬 호출
    ↓
[BookController]       두 결과 병합, ISBN 중복 제거, 응답 JSON 생성
    ↓
[브라우저] JSON 응답 수신
```

---

### 로그인 흐름

```
[브라우저] POST /api/auth/login {"username":"alice","password":"1234"}
    ↓
[AuthController]       login(request)
    ↓
[UserService]          findByUsername("alice") — Caffeine 캐시 → DB
    ↓
[PasswordEncoder]      matches("1234", "$2a$10$...") — BCrypt 검증
    ↓
[JwtService]           generateToken("alice") — HMAC-SHA256 서명
    ↓
[AuthController]       LoginResponse {"username":"alice","token":"eyJ..."}
    ↓
[브라우저] 토큰 저장 후 이후 요청 헤더에 "Authorization: Bearer eyJ..." 포함
```

---

### 도서관 소장 조회 흐름

```
[브라우저] GET /api/books/9788997170692
    ↓
[BookController]       getBookDetail("9788997170692")
    ↓
[YjuLibraryService]    checkAvailability("9788997170692", null)
  ① 메모리 캐시 확인 (ConcurrentHashMap, 10분 TTL)
  ② DB 캐시 확인 (library_holding_cache 테이블, 6시간 TTL)
  ③ gRPC → Python 스크래퍼(Playwright) 크롤링
     → 결과를 메모리 캐시 + DB에 저장
    ↓
[BookController]       상세 응답 JSON 구성 후 반환
```

---

### 캐시 전략 요약

| 캐시 종류 | 위치 | TTL | 목적 |
|---|---|---|---|
| Caffeine (books) | 메모리 | 24시간 | 도서 검색 결과 |
| Caffeine (externalBooks) | 메모리 | 24시간 | 외부 API 결과 |
| Caffeine (users) | 메모리 | 24시간 | 사용자 조회 |
| YjuLibraryService.cache | 메모리 | 10분 | 도서관 소장 정보 |
| library_holding_cache | PostgreSQL | 6시간 | 도서관 소장 정보 (앱 재시작 후에도 유지) |

---

### 주요 기술 스택

| 기술 | 역할 |
|---|---|
| Spring Boot 3 | 애플리케이션 프레임워크 |
| Spring Data JPA + Hibernate | DB ORM |
| Spring Security | 인증·인가 |
| JJWT | JWT 생성·검증 |
| Spring WebFlux WebClient | HTTP 비동기 클라이언트 (외부 API 호출) |
| gRPC (Protocol Buffers) | Python 스크래퍼와 통신 |
| PostgreSQL + pg_trgm | DB + 유사도 검색 |
| Caffeine | 인메모리 캐시 |
| HikariCP | DB 커넥션 풀 |

---

---

## 13. Python / FastAPI — library-scraper 서비스

> Spring Boot 백엔드가 도서관 웹사이트를 직접 크롤링하지 않고,
> **gRPC로 이 Python 서비스에게 위임**합니다.
> Playwright(브라우저 자동화)를 내부적으로 사용해 JavaScript SPA 사이트를 스크래핑합니다.

---

### 13-1. config.py — 설정 싱글톤

```python
from __future__ import annotations   # 파이썬 3.10 미만에서도 타입 힌트 forward reference 허용

import os
from dataclasses import dataclass    # 데이터 클래스 데코레이터 (자동으로 __init__, __repr__ 생성)
from functools import lru_cache      # 함수 결과를 캐싱하는 데코레이터 (최대 n개)
from pathlib import Path             # OS 독립적 파일 경로 처리

from dotenv import load_dotenv       # .env 파일을 환경변수로 로드
```

```python
BASE_DIR = Path(__file__).resolve().parent
# __file__ = 현재 파일(config.py)의 경로
# .resolve() = 절대 경로로 변환
# .parent = 상위 디렉터리 (= library-scraper/)

DOTENV_PATH = BASE_DIR / ".env"              # 서비스 로컬 .env
ROOT_DOTENV_PATH = BASE_DIR.parent / ".env"  # 프로젝트 루트 .env (com/.env)
```

```python
def _safe_load_dotenv(path: Path, override: bool = False) -> None:
    if not path.exists():       # 파일이 없으면 조용히 건너뜀
        return
    for enc in ("utf-8-sig", "cp949"):   # Windows 저장 인코딩까지 fallback 시도
        try:
            load_dotenv(path, encoding=enc, override=override)
            return
        except UnicodeDecodeError:
            continue            # 인코딩 실패 시 다음 인코딩으로 재시도

# root .env 먼저 로드 (기본값), 그 다음 로컬 .env로 덮어씀 (override=True)
_safe_load_dotenv(ROOT_DOTENV_PATH, override=False)
_safe_load_dotenv(DOTENV_PATH, override=True)
```

> **override=False**: 이미 환경변수가 있으면 덮어쓰지 않음.
> **override=True**: 반드시 이 파일의 값으로 덮어씀. 로컬 개발자가 커스텀 가능.

```python
@dataclass(frozen=True)    # frozen=True → 불변(Immutable) 객체. 실수로 값 변경 불가
class Settings:
    http_host: str          # FastAPI HTTP 서버 바인딩 주소
    http_port: int          # FastAPI HTTP 포트 (기본 8090)
    grpc_host: str          # gRPC 서버 바인딩 주소
    grpc_port: int          # gRPC 포트 (기본 9090)

    lib_base_url: str       # 도서관 사이트 기본 URL
    lib_login_url: str      # 로그인 URL
    lib_search_url_prefix: str  # 검색 URL 앞부분
    lib_user_id: str        # 도서관 로그인 ID (학번)
    lib_user_password: str  # 도서관 로그인 패스워드

    playwright_headless: bool       # 헤드리스 모드 (True=창 없이 실행)
    playwright_timeout_ms: int      # 요소 대기 타임아웃 (ms)
    playwright_concurrency: int     # 동시에 실행 가능한 브라우저 수
    playwright_hard_timeout_ms: int # 강제 종료 타임아웃 (ms)

    kakao_rest_api_key: str     # 카카오 책 검색 API 키
    aladin_ttb_key: str         # 알라딘 API TTB 키
    google_api_key: str         # Google Books API 키

    ebook_base_url: str             # 전자책 사이트 기본 URL
    ebook_cache_ttl_sec: int        # 전자책 캐시 유효 시간(초)
    ebook_total_timeout_sec: int    # 전자책 조회 전체 타임아웃(초)
    # ... 기타 설정들
```

```python
@lru_cache(maxsize=1)       # 최대 1개 결과만 캐싱 → 사실상 싱글톤 패턴
def get_settings() -> Settings:
    return Settings(
        http_host=_get_str("HTTP_HOST", "0.0.0.0"),      # 환경변수 없으면 기본값 사용
        http_port=_get_int("HTTP_PORT", 8090),
        grpc_port=_get_int("GRPC_PORT", 9090),
        lib_user_id=_get_str_any(                         # 여러 환경변수 이름 중 하나라도 있으면 사용
            ["LIBRARY_ID", "LIB_USER_ID", "STUDENT_ID", "YJU_STUDENT_ID", "YJU_ID"],
            "",
        ),
        # ... 나머지 설정
    )
```

> **lru_cache**: Least Recently Used 캐시. `maxsize=1`로 설정하면
> 처음 호출 시 Settings 객체를 생성하고, 이후에는 동일한 객체를 반환.
> 멀티스레드 환경에서도 안전한 싱글톤.

```python
def validate_runtime_settings(settings: Settings) -> None:
    # 필수 환경변수 누락 시 서버 시작 전에 명확한 오류 메시지 출력
    if not settings.lib_user_id:
        raise ValueError("LIBRARY_ID is required. Set it in library-scraper/.env")
    if not settings.lib_user_password:
        raise ValueError("LIBRARY_PW is required. Set it in library-scraper/.env")
```

---

### 13-2. run_servers.py — 서버 실행 진입점

```
[run_servers.py 실행]
       ↓
[Thread] FastAPI (uvicorn) → 백그라운드 데몬 스레드 (HTTP 요청 처리)
       ↓
[Main Thread] gRPC 서버 → 메인 스레드에서 실행
              (Playwright는 메인 스레드에서만 안정적으로 동작)
```

```python
# FastAPI 서버를 별도 스레드에서 실행
t = threading.Thread(target=run_fastapi, daemon=True)
# daemon=True: 메인 스레드 종료 시 같이 종료됨 (좀비 프로세스 방지)
t.start()

# gRPC는 메인 스레드에서 실행 (Playwright 안정성 때문)
asyncio.run(serve())
```

> **왜 Playwright가 메인 스레드여야 하나?**
> Playwright의 일부 내부 구현(OS 이벤트 루프)이 메인 스레드를 요구합니다.
> 서브 스레드에서 실행 시 세마포어 오류 또는 크래시가 발생할 수 있음.

---

### 13-3. grpc_server.py — gRPC 서버

```python
from concurrent.futures import ThreadPoolExecutor   # 스레드풀 (gRPC 요청 처리용)
import grpc                                          # gRPC Python 라이브러리
import library_pb2, library_pb2_grpc               # protobuf 자동생성 코드
```

```python
class LibraryServicer(library_pb2_grpc.LibraryServiceServicer):
    # gRPC 서비스 구현 클래스. protobuf에 정의된 서비스를 Python으로 구현.

    def getLibraryStatus(self, request, context):
        # Java에서 호출 → ISBN/제목/저자로 도서관 소장 상태 조회
        # gRPC는 동기(sync) 메서드를 호출하지만, 내부 크롤링은 async
        # → asyncio.run_coroutine_threadsafe()로 이벤트 루프에 작업 전달
        future = asyncio.run_coroutine_threadsafe(
            self._async_get_library_status(request),
            self._loop                   # 미리 생성된 이벤트 루프
        )
        return future.result(timeout=25) # 최대 25초 대기 후 결과 반환

    async def _async_get_library_status(self, request):
        # 실제 크롤링 로직 (async)
        result = await service.check_library(request.isbn, request.title, request.author)
        return library_pb2.LibraryResponse(
            found=result.found,
            available=result.available,
            # ... 나머지 필드
        )
```

```python
async def serve():
    # gRPC 서버 시작
    server = grpc.aio.server(                   # 비동기 gRPC 서버
        ThreadPoolExecutor(max_workers=10)       # 동시 요청 처리 스레드 수
    )
    library_pb2_grpc.add_LibraryServiceServicer_to_server(servicer, server)
    server.add_insecure_port(f"{host}:{port}")  # TLS 없이 바인딩 (내부 통신용)
    await server.start()
    await server.wait_for_termination()         # 종료 신호(Ctrl+C) 대기
```

> **grpc.aio.server**: Python asyncio 기반 gRPC 서버.
> **insecure_port**: 암호화 없음. 도커 내부 네트워크에서만 통신하므로 안전.

---

### 13-4. main.py — FastAPI 앱 (HTTP API)

```python
from contextlib import asynccontextmanager   # 비동기 컨텍스트 매니저 (lifespan 패턴)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 서버 시작 시 실행 (startup)
    await init_service()            # LibraryBackendService 초기화
    asyncio.create_task(           # 공지사항 크롤링 백그라운드 작업 시작
        _notice_refresh_loop()
    )
    yield                           # ← 이 지점에서 서버가 실제로 실행됨
    # 서버 종료 시 실행 (shutdown)
    await cleanup_service()        # 브라우저 인스턴스 정리

app = FastAPI(lifespan=lifespan)   # lifespan 함수를 앱에 등록
```

> **lifespan 패턴**: FastAPI 0.93+ 권장 방식. 서버 시작/종료 이벤트를 한 곳에서 관리.
> 이전에는 `@app.on_event("startup")`을 사용했지만 deprecated.

```python
async def _notice_refresh_loop():
    # 공지사항을 4시간마다 자동 갱신하는 무한 루프
    while True:
        await _refresh_notices()           # 크롤링 실행
        await asyncio.sleep(4 * 3600)     # 4시간 대기 (14400초)
```

#### 주요 엔드포인트

```python
@app.get("/check-library")
async def check_library(isbn: str = "", title: str = "", author: str = ""):
    # 도서관 소장 정보 조회 (HTTP 버전 — 직접 테스트용)
    result = await service.check_library(isbn, title, author)
    return result.to_dict()

@app.get("/books/search")
async def search_books(q: str, page: int = 1, size: int = 20):
    # Kakao/Aladin/Google API로 도서 검색 후 통합 반환
    results = await service.search_books(q, page, size)
    return {"items": results, "page": page}

@app.get("/check-ebook")
async def check_ebook(title: str, author: str = "", publisher: str = ""):
    # 영진전문대 전자도서관 전자책 소장 여부 조회 (Playwright 크롤링)
    result = await service.check_ebook(title, author, publisher)
    return result

@app.get("/notices/banners")
async def get_notice_banners():
    # 캐싱된 공지사항 배너 이미지 목록 반환
    return {"banners": _cached_notices}

@app.post("/notices/refresh")
async def refresh_notices():
    # 수동으로 공지사항 갱신 트리거
    await _refresh_notices()
    return {"status": "refreshed"}

@app.get("/proxy/image")
async def proxy_image(url: str):
    # 외부 이미지를 프록시로 제공 (CORS 우회)
    # url 파라미터는 반드시 허용된 도메인만 접근 가능 (보안 처리됨)
    ...
```

---

### 13-5. app_service.py — LibraryBackendService

```python
class LibraryBackendService:
    """
    gRPC 서비스와 FastAPI 엔드포인트 양쪽에서 호출되는 핵심 비즈니스 로직 레이어.
    Playwright 크롤러, 외부 API 클라이언트를 통합 관리.
    """

    async def check_library(self, isbn: str, title: str, author: str):
        # 1. ISBN 정규화 (하이픈 제거, 13자리 변환)
        isbn13 = self._normalize_isbn13(isbn)
        # 2. 스크래퍼에 위임
        result = await self._scraper.check_availability(isbn13, title, author)
        return result

    async def check_ebook(self, title: str, author: str, publisher: str):
        # 전자책 조회에는 타임아웃 적용 (전자책 사이트가 느릴 수 있음)
        try:
            result = await asyncio.wait_for(
                self._ebook_crawler.search(title, author, publisher),
                timeout=settings.ebook_total_timeout_sec   # 기본 40초
            )
        except asyncio.TimeoutError:
            # 타임아웃 시 "찾을 수 없음" 결과 반환 (서버 오류 아님)
            return EbookResult(found=False, error="timeout")
        return result

    def _normalize_isbn13(self, isbn: str) -> str:
        # "978-89-97170-69-2" → "9788997170692"
        cleaned = re.sub(r"[^0-9X]", "", isbn.upper())  # 숫자와 X만 남김
        if len(cleaned) == 10:
            # ISBN-10 → ISBN-13 변환 (978 접두사 추가 후 체크섬 재계산)
            ...
        return cleaned
```

> **asyncio.wait_for**: 코루틴에 타임아웃을 적용. 지정 시간 초과 시 `asyncio.TimeoutError` 발생.

---

### 13-6. scraper_service.py — LibraryScraper (핵심 크롤러)

> 영진전문대 도서관(lib.yju.ac.kr)은 **Knockout.js SPA** 기반 사이트.
> 일반 HTTP 요청으로는 HTML이 렌더링되지 않아, **Playwright(실제 브라우저)**로만 접근 가능.

```
[LibraryScraper 흐름]
       ↓
① LRU 캐시 확인 (메모리)
  - stable 결과 (소장/미소장 확정): 24시간 TTL
  - transient 결과 (대출중 등 변동 가능): 10분 TTL
       ↓ 캐시 미스 시
② Playwright 브라우저 실행 (세마포어로 동시 접속 수 제한)
       ↓
③ 로그인 → 도서 검색 → 결과 파싱
       ↓
④ 결과를 캐시에 저장 후 반환
```

```python
# 세마포어: 동시에 실행 가능한 브라우저 인스턴스 수 제한
self._sem = asyncio.Semaphore(settings.playwright_concurrency)  # 기본 4

async def check_availability(self, isbn: str, title: str, author: str):
    async with self._sem:           # 세마포어 획득 (초과 시 대기)
        return await self._do_check(isbn, title, author)

async def _do_check(self, isbn, title, author):
    # 캐시 확인
    cached = self._cache.get(isbn)
    if cached and not cached.is_expired():
        return cached.result        # 캐시 HIT → 즉시 반환

    # Playwright로 실제 크롤링
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page()
        try:
            result = await self._crawler.search(page, isbn, title)
        finally:
            await browser.close()   # 반드시 브라우저 닫기

    # 결과 캐싱 (stable vs transient TTL 구분)
    ttl = STABLE_TTL if result.is_definitive else TRANSIENT_TTL
    self._cache[isbn] = CacheEntry(result, expires_at=time.time() + ttl)
    return result
```

```python
# 도서관 상태 코드 파싱
STATUS_MAP = {
    "대출가능": ("AVAILABLE", True),    # 대출 가능
    "대출중":   ("CHECKED_OUT", False), # 대출 중
    "예약중":   ("RESERVED", False),    # 예약 중
    "분실":     ("LOST", False),        # 분실
    "제본중":   ("BINDING", False),     # 제본 중
    "정리중":   ("PROCESSING", False),  # 정리 중
}
```

---

### 13-7. library_search_engine.py — LibrarySessionCrawler

```python
class LibrarySessionCrawler:
    """
    실제 Playwright 브라우저를 조작해
    도서관 사이트에 로그인하고 도서를 검색하는 크롤러.
    """

    async def search(self, page, isbn: str, title: str) -> SearchResult:
        # 1단계: 로그인
        await self._login(page)
        # 2단계: ISBN으로 검색 URL 생성
        query_url = self._build_search_url(isbn or title)
        await page.goto(query_url)
        # 3단계: 결과 대기 (SPA 렌더링 완료 대기)
        await page.wait_for_selector(".search-result-item", timeout=15000)
        # 4단계: 결과 파싱
        return await self._parse_results(page, isbn, title)

    def _build_search_url(self, query: str) -> str:
        # SPA 라우터용 URL 인코딩 (UTF-8 percent encoding)
        # 예: "파이썬" → "https://lib.yju.ac.kr/.../%ED%8C%8C%EC%9D%B4%EC%8D%AC"
        encoded = hybrid_encode_query(query)
        return f"{settings.lib_search_url_prefix}{encoded}"

    def clean_title(self, title: str) -> str:
        # "파이썬 완전 정복 (개정판)" → "파이썬 완전 정복"
        # 부제목, 판차 정보, 특수문자 제거 후 비교 정확도 향상
        title = re.sub(r"\([^)]*\)", "", title)   # 괄호 내용 제거
        title = re.sub(r"[\s]+", " ", title)       # 연속 공백 정리
        return title.strip()

    def normalize_text(self, text: str) -> str:
        # 대소문자 통일, 공백 제거, 특수문자 제거
        return re.sub(r"[^가-힣a-z0-9]", "", text.lower())

    async def _scan_rows_for_title(self, page, target_title: str):
        # 검색 결과 목록에서 제목이 일치하는 행 찾기
        # 정확히 일치하지 않아도 normalize_text로 유사도 비교
        rows = await page.query_selector_all(".book-row")
        for row in rows:
            row_title = await row.query_selector_eval(".title", "el => el.textContent")
            if self.normalize_text(row_title) == self.normalize_text(target_title):
                return row
        return None
```

> **`page.wait_for_selector()`**: 지정한 CSS 선택자의 요소가 DOM에 나타날 때까지 대기.
> SPA 사이트는 JavaScript가 실행된 후에야 요소가 생기므로 이 대기가 필수.

---

### 13-8. notice_crawler.py — NoticeCrawler

```python
@dataclass
class NoticeImage:
    url: str         # 이미지 URL (도서관 사이트 내부 URL)
    alt: str         # 이미지 alt 텍스트 (공지 제목)
    notice_id: str   # 게시글 ID (중복 제거용)
    detail_url: str  # 공지 상세 페이지 URL

class NoticeCrawler:
    async def crawl(self) -> list[NoticeImage]:
        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=True)
            page = await browser.new_page()

            # 1단계: 도서관 공지 게시판 접근
            await page.goto(NOTICE_BOARD_URL)
            await page.wait_for_load_state("networkidle")  # 모든 네트워크 요청 완료 대기

            # 2단계: 게시글 목록에서 이미지가 있는 글만 추출
            notices = await self._extract_board_items(page)

            # 3단계: 각 게시글 상세 페이지에서 이미지 추출
            images = []
            for notice in notices[:10]:   # 최신 10개만
                detail_imgs = await self._extract_detail_images(page, notice)
                images.extend(detail_imgs)

            await browser.close()
        return images

    def _is_layout_image(self, img_url: str) -> bool:
        # 레이아웃/버튼 이미지 걸러내기 (실제 공지 이미지만 추출)
        LAYOUT_PATTERNS = ["/btn_", "/ico_", "/bg_", "blank.gif"]
        return any(pattern in img_url for pattern in LAYOUT_PATTERNS)
```

> **`wait_for_load_state("networkidle")`**: 500ms 동안 네트워크 요청이 없을 때까지 대기.
> 동적 로딩이 많은 사이트에서 완전한 렌더링을 보장하는 가장 확실한 방법.

---

## 14. Vue.js 프론트엔드

> **Vue 3 Composition API** (`<script setup>`) 기반의 SPA 프론트엔드.
> Vite로 빌드, Tailwind CSS로 스타일링, Pinia로 전역 상태 관리.

---

### 14-1. main.js — 앱 초기화

```javascript
import { createApp } from 'vue'       // Vue 앱 인스턴스 생성 함수
import { createPinia } from 'pinia'   // 전역 상태 관리 라이브러리
import App from './App.vue'           // 루트 컴포넌트
import router from './router'         // Vue Router 인스턴스

const app = createApp(App)    // 루트 컴포넌트로 앱 인스턴스 생성
app.use(createPinia())        // Pinia 플러그인 등록 (전역 스토어 사용 가능하게)
app.use(router)               // Router 플러그인 등록 (URL ↔ 컴포넌트 매핑)
app.mount('#app')             // index.html의 <div id="app">에 Vue 앱 마운트
```

> **왜 순서가 중요한가?**
> `app.use()`로 플러그인을 먼저 등록한 뒤 `mount()`를 호출해야 합니다.
> mount 이후에 플러그인을 추가하면 일부 기능이 동작하지 않습니다.

---

### 14-2. router/index.js — 라우터 설정

```javascript
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/',              name: 'home',        component: HomeView },
  { path: '/search',        name: 'search',      component: SearchView },
  { path: '/books/:isbn13', name: 'book-detail', component: BookDetailView },
  // :isbn13 는 동적 파라미터 → route.params.isbn13 으로 접근
  { path: '/login',         name: 'login',       component: LoginView },
  { path: '/register',      name: 'register',    component: RegisterView },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
    meta: { requiresAuth: true }    // 로그인 필요 표시
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }  // 없는 경로 → 404 리다이렉트
]

const router = createRouter({
  history: createWebHistory(),   // HTML5 History API 사용 (해시 없는 깔끔한 URL)
  routes
})
```

#### 네비게이션 가드 (인증 체크)

```javascript
router.beforeEach((to, from, next) => {
  // 모든 페이지 이동 전에 실행되는 전역 가드
  const token = localStorage.getItem('token')   // 로컬스토리지에서 JWT 확인

  if (to.meta.requiresAuth && !token) {
    // requiresAuth 페이지인데 토큰이 없으면 → 로그인 페이지로 리다이렉트
    next({ name: 'login', query: { redirect: to.fullPath } })
    // query.redirect: 로그인 후 원래 가려던 페이지로 돌아오기 위해 저장
  } else {
    next()   // 정상적으로 이동 허용
  }
})
```

> **beforeEach**: 라우터 레벨의 인증 미들웨어.
> 서버의 JWT 필터와 역할이 동일 — 인증 없는 접근을 차단.

---

### 14-3. stores/auth.js — Pinia 인증 스토어

```javascript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  // --- 상태(State) ---
  const token    = ref(localStorage.getItem('token') || '')
  // 페이지 새로고침 시에도 로그인 상태 유지: localStorage에서 복원

  const username = ref(localStorage.getItem('username') || '')
  const roles    = ref(JSON.parse(localStorage.getItem('roles') || '[]'))

  // --- 계산된 값(Computed) ---
  const isLoggedIn = computed(() => !!token.value)
  // !!token.value: 빈 문자열이면 false, 토큰이 있으면 true

  const isAdmin = computed(() =>
    roles.value.includes('ROLE_ADMIN')  // 관리자 권한 확인
  )

  // --- 액션(Action) ---
  async function login(loginId, password) {
    const { data } = await api.login(loginId, password)   // 서버 API 호출
    token.value    = data.token       // 반응형 상태 업데이트
    username.value = data.username
    roles.value    = data.roles

    // 브라우저 종료 후에도 유지되도록 localStorage에 저장
    localStorage.setItem('token',    data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('roles',    JSON.stringify(data.roles))
  }

  function logout() {
    token.value    = ''
    username.value = ''
    roles.value    = []
    // localStorage 초기화
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('roles')
  }

  return { token, username, roles, isLoggedIn, isAdmin, login, logout }
})
```

> **defineStore**: Pinia 스토어 정의. 컴포넌트 트리 어디서든 `useAuthStore()`로 접근 가능.
> **ref()**: 반응형 변수. 값이 바뀌면 이를 참조하는 모든 컴포넌트가 자동 재렌더링.
> **computed()**: 다른 반응형 값에서 자동으로 계산되는 읽기 전용 값.

---

### 14-4. api/index.js — Axios 인터셉터

```javascript
import axios from 'axios'

// Axios 인스턴스 생성 (기본 설정 적용)
const instance = axios.create({
  baseURL: '/api',        // 모든 요청에 '/api' 접두사 자동 추가
  timeout: 60000,         // 60초 타임아웃 (도서관 크롤링이 느릴 수 있음)
})
```

```javascript
// 요청 인터셉터: 모든 요청 발송 전에 실행
instance.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
    // 모든 API 요청 헤더에 JWT 자동 첨부
    // 수동으로 매번 토큰을 추가할 필요 없음
  }
  return config   // 수정된 config 반환 (없으면 요청이 전송되지 않음)
})
```

```javascript
// 응답 인터셉터: 모든 응답 수신 후에 실행
instance.interceptors.response.use(
  response => response,   // 성공(2xx)은 그대로 통과

  error => {
    if (error.response?.status === 401) {
      // 토큰 만료 또는 인증 실패 시 자동 로그아웃
      localStorage.removeItem('token')
      window.location.href = '/login'   // 로그인 페이지로 강제 이동
    }
    return Promise.reject(error)   // 에러를 다시 throw하여 호출부에서 catch 가능
  }
)
```

> **인터셉터 패턴**: 공통 관심사(토큰 첨부, 401 처리)를 한 곳에 집중.
> 컴포넌트마다 토큰 처리 코드를 반복하지 않아도 됨.

```javascript
// API 메서드 모음
export default {
  searchBooks: (q, page, size) =>
    instance.get('/books/search', { params: { q, page, size } }),

  getBookDetail: (isbn13) =>
    instance.get(`/books/${isbn13}`),

  getEbookInfo: (isbn) =>
    instance.get(`/books/${isbn}/ebook`),

  getBooksInfinite: (cursor, size = 20) =>
    instance.get('/books/infinite', { params: { cursor, size } }),
    // cursor: 마지막으로 받은 책의 ISBN (없으면 처음부터)

  getNoticeBanners: () =>
    instance.get('/notices/banners'),

  login: (loginId, password) =>
    instance.post('/auth/login', { loginId, password }),
}
```

---

### 14-5. App.vue — 루트 컴포넌트

```html
<template>
  <div class="min-h-screen flex flex-col">
    <!-- 항상 상단에 표시되는 네비게이션 바 -->
    <NavBar />

    <!-- URL에 따라 동적으로 교체되는 페이지 컴포넌트 -->
    <RouterView class="flex-1" />
    <!--          ↑ flex-1: 남은 세로 공간을 모두 차지 (footer를 항상 하단에 고정) -->

    <!-- 항상 하단에 표시되는 푸터 -->
    <FooterBar />
  </div>
</template>
```

> **RouterView**: 현재 URL에 매핑된 컴포넌트를 렌더링하는 Vue Router 컴포넌트.
> URL이 `/search`이면 `SearchView`를, `/books/123`이면 `BookDetailView`를 렌더링.

---

### 14-6. HomeView.vue — 메인 페이지

```html
<template>
  <!-- 히어로 섹션: 검색 폼 -->
  <section class="bg-gradient-to-br from-yju-dark via-yju to-yju-light text-white">
    <form @submit.prevent="doSearch">
    <!--  ↑ @submit.prevent: submit 이벤트 발생 시 페이지 새로고침 방지 후 doSearch 호출 -->
      <input
        v-model="keyword"
        <!--  ↑ v-model: 양방향 데이터 바인딩. 입력값이 keyword ref와 실시간 동기화 -->
        type="search"
        placeholder="도서명, 저자명, ISBN을 입력하세요..."
      />
      <button type="submit">검색</button>
    </form>
  </section>

  <!-- 소장 도서 그리드 -->
  <div v-if="loading">     <!-- loading이 true면 스켈레톤 UI 표시 -->
    <div v-for="n in 12">  <!-- 12개의 플레이스홀더 카드 -->
      <div class="skeleton ..."></div>   <!-- CSS 애니메이션 로딩 효과 -->
    </div>
  </div>

  <div v-else>    <!-- loading이 false면 실제 도서 목록 표시 -->
    <RouterLink
      v-for="book in recentBooks"
      :to="{ name: 'book-detail', params: { isbn13: book.isbn } }"
    >
      <!-- :to 는 v-bind:to의 약어. 동적 값을 바인딩 -->
      <img
        :src="book.imageUrl || 'https://placehold.co/120x160'"
        @error="e => e.target.src = 'https://placehold.co/120x160'"
        <!-- @error: 이미지 로드 실패 시 플레이스홀더로 대체 -->
      />
    </RouterLink>
  </div>
</template>
```

```javascript
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router  = useRouter()   // 라우터 인스턴스 (programmatic navigation용)
const keyword = ref('')        // 검색어 (v-model과 연결된 반응형 변수)
const loading = ref(true)      // 로딩 상태
const recentBooks = ref([])    // 도서 목록

function doSearch() {
  if (!keyword.value.trim()) return   // 빈 검색어 방지
  router.push({ name: 'search', query: { q: keyword.value.trim() } })
  // /search?q=검색어 로 이동
}

onMounted(async () => {
  // 컴포넌트가 DOM에 마운트된 직후 실행
  try {
    const { data } = await api.getBooksInfinite(undefined, 18)  // 18개 도서 로드
    recentBooks.value = data.items || []
  } catch {
    recentBooks.value = []    // 오류 시 빈 배열 (화면 깨짐 방지)
  } finally {
    loading.value = false     // 성공/실패 상관없이 로딩 종료
  }
})
</script>
```

> **`onMounted`**: 컴포넌트가 실제 DOM에 삽입된 후 호출.
> `created()`보다 늦지만, DOM 조작이 필요하거나 API 호출이 필요할 때 사용.

---

### 14-7. SearchView.vue — 검색 결과 페이지

```javascript
<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()          // 현재 라우트 정보 접근 (query, params 등)
const books = ref([])

// URL의 쿼리스트링이 바뀔 때마다 재검색
watch(() => route.query.q, async (newQ) => {
  if (!newQ) return
  await fetchBooks(newQ)          // 새 검색어로 도서 검색
}, { immediate: true })           // immediate: 최초 로드 시에도 즉시 실행

async function fetchBooks(q) {
  try {
    const { data } = await api.searchBooks(q, 1, 20)
    // 응답이 배열이면 직접 사용, 객체면 .items 추출 (API 응답 형태 유연하게 처리)
    books.value = Array.isArray(data) ? data : (data.items || [])
  } catch {
    books.value = []
  }
}
</script>
```

> **`watch`**: 반응형 값의 변화를 감지해 사이드이펙트를 실행.
> `route.query.q`가 바뀌면 (사용자가 검색어를 바꾸면) 자동으로 `fetchBooks` 실행.
> `immediate: true`가 없으면 처음 로드 시 watch가 실행되지 않아 검색 결과가 안 나옴.

---

### 14-8. BookDetailView.vue — 도서 상세 페이지

```javascript
<script setup>
import { ref, computed, onMounted } from 'vue'

const isbn13 = route.params.isbn13   // URL 파라미터에서 ISBN 추출
const book   = ref(null)
const prices = ref([])

// 가격 비교: 알라딘, YES24, 교보문고만 고정 순서로 표시
const ALLOWED_STORES = ['aladin', 'yes24', 'kyobo']
const filteredPrices = computed(() => {
  const map = Object.fromEntries(prices.value.map(p => [p.store, p]))
  // ['aladin', 'yes24', 'kyobo'] 순서를 유지하며 존재하는 것만 반환
  return ALLOWED_STORES.map(id => map[id]).filter(Boolean)
})

async function fetchDetail() {
  loading.value = true
  try {
    const { data } = await api.getBookDetail(isbn13)
    book.value = data

    // 가격·전자책 정보는 별도로 비동기 로드 (도서 기본 정보 먼저 표시)
    fetchPrices(isbn13, data.title)   // await 없이 호출 → 병렬 실행
    fetchEbook(isbn13)                // await 없이 호출 → 병렬 실행
  } catch {
    error.value = true
  } finally {
    loading.value = false   // 기본 정보 로딩만 완료되면 화면 표시
  }
}

async function fetchEbook(isbn) {
  ebookLoading.value = true
  try {
    const { data } = await api.getEbookInfo(isbn)
    // 기존 book 객체에 ebook 정보만 추가 (spread 연산자로 불변성 유지)
    book.value = { ...book.value, ebook: data }
  } catch {
    // 전자책 로드 실패해도 페이지 깨지지 않음 (optional 정보)
  } finally {
    ebookLoading.value = false
  }
}
</script>
```

> **병렬 API 호출 전략**: `fetchPrices`와 `fetchEbook`을 `await` 없이 호출.
> 두 요청이 동시에 실행되어 응답 속도가 빨라짐.
> 각각 로딩 상태가 분리되어 있어 UI가 순차적으로 채워지는 효과.

```html
<!-- 전자책 정보: 로딩/있음/없음 세 가지 상태 처리 -->
<template v-if="ebookLoading">
  <div class="animate-spin ..."></div>   <!-- 로딩 스피너 -->
</template>
<template v-else-if="book.ebook?.found">
  <!-- 전자책 있음: 대출 정보 표시 -->
  <a :href="book.ebook.deepLinkUrl">전자책 대출하기</a>
</template>
<div v-else>
  <!-- 전자책 없음 -->
  <p>전자책 정보 없음</p>
</div>
```

> **`v-if / v-else-if / v-else`**: 조건부 렌더링. 조건에 따라 DOM 요소 자체를 생성/제거.
> **`?.` (Optional Chaining)**: `book.ebook`이 null/undefined여도 에러 없이 `undefined` 반환.

---

## 15. 전체 시스템 흐름 (3개 레이어 통합)

```
[사용자 브라우저]
Vue.js SPA (Vite + Tailwind)
       |
       | HTTP (axios, baseURL='/api', Bearer Token 자동 첨부)
       |
       ↓
[Spring Boot (Java) — com 서비스]
  Controller → Service → Repository → PostgreSQL
       |
       | 캐시 미스 시 gRPC (Protocol Buffers)
       |
       ↓
[Python FastAPI + gRPC 서버 — library-scraper]
  LibraryServicer → LibraryBackendService
       |
       | Playwright (실제 Chromium 브라우저)
       |
       ↓
[외부 웹사이트]
  lib.yju.ac.kr   (도서관 소장 정보)
  ebook.yjc.ac.kr (전자책 정보)

[외부 API]
  Kakao Books API → 도서 검색
  Aladin API      → 도서 검색 + 가격
  Google Books    → 표지 이미지 fallback
```

---

### 도서 검색 전체 흐름

```
① 브라우저: /search?q=파이썬 접속
② Vue Router: SearchView 렌더링, watch가 q 변화 감지
③ axios: GET /api/books/search?q=파이썬
④ Spring JwtAuthFilter: 토큰 없으면 통과 (public endpoint)
⑤ BookController: searchBooks("파이썬", 1, 20)
⑥ BookService: DB 검색 (pg_trgm 유사도) → 결과 있으면 반환
             결과 없으면 → ExternalBookService (Kakao/Aladin API) → DB 저장
⑦ 각 도서 항목에 holding 정보 병합 (gRPC → Playwright 크롤링 또는 캐시)
⑧ JSON 응답 → axios → Vue: books.value 갱신 → 화면 재렌더링
```

---

### 도서 상세 + 전자책 비동기 로드 흐름

```
① 브라우저: /books/9788997170692 접속
② BookDetailView: onMounted → fetchDetail() 실행
③ GET /api/books/9788997170692 → 도서 기본 정보 (제목, 저자, 표지, 도서관 상태)
④ 기본 정보 표시 완료 → book.value 업데이트 → 화면 렌더링

동시에 (병렬):
⑤-A GET /api/books/.../ebook → 전자책 정보 (Playwright로 ebook.yjc.ac.kr 크롤링)
⑤-B GET /api/books/.../prices → 가격 비교 (Aladin/YES24/교보 API 호출)

⑥ 각 응답 도착 시 화면 부분 업데이트 (Progressive Loading)
```

---

### 기술 스택 전체 요약

| 레이어 | 기술 | 역할 |
|---|---|---|
| **프론트엔드** | Vue 3 + Composition API | UI 렌더링, 라우팅 |
| | Pinia | 전역 상태 (로그인 정보) |
| | Axios + 인터셉터 | HTTP 통신, JWT 자동 첨부 |
| | Tailwind CSS | 유틸리티 기반 스타일링 |
| | Vite | 번들러 (빠른 개발 서버) |
| **백엔드 (Java)** | Spring Boot 3 | 앱 프레임워크 |
| | Spring Security + JWT | 인증·인가 |
| | Spring Data JPA | ORM (DB 접근) |
| | Spring WebFlux WebClient | 외부 API 비동기 호출 |
| | Caffeine | 인메모리 캐시 |
| | gRPC (proto3) | Python 서비스 통신 |
| **스크래퍼 (Python)** | FastAPI + uvicorn | HTTP API 서버 |
| | gRPC (grpcio) | Java 서비스와 통신 |
| | Playwright | 브라우저 자동화 크롤링 |
| | asyncio + uvloop | 비동기 이벤트 루프 |
| | lru_cache + dataclass | 설정 싱글톤 패턴 |
| **데이터베이스** | PostgreSQL | 관계형 DB |
| | pg_trgm | 한글 유사도 검색 |
| | HikariCP | 커넥션 풀 |

---

*작성일: 2026-03-06 / 대상: Y-Danawa 프로젝트 팀원 학습용*
