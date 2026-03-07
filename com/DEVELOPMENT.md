# Y-다나와 (YJC 도서 통합 검색) 개발 현황

> 영진전문대학교(yjc.ac.kr) 도서 통합 검색 플랫폼
> 도서관 소장 여부 · 전자책 · 온라인 서점 가격 비교를 한 화면에서 제공

---

## 목차
1. [프론트엔드](#1-프론트엔드)
2. [데이터베이스](#2-데이터베이스)
3. [백엔드](#3-백엔드)
4. [인프라 / Docker](#4-인프라--docker)
5. [배포 스크립트](#5-배포-스크립트)

---

## 1. 프론트엔드

### 기술 스택
| 항목 | 버전 |
|------|------|
| Vue 3 (Composition API, `<script setup>`) | ^3.4 |
| Vite | ^5.3 |
| Tailwind CSS | ^3.4 |
| Pinia (상태 관리) | ^2.1 |
| Vue Router 4 | ^4.3 |
| Axios | ^1.7 |

### 디렉토리 구조
```
com/frontend/
├── src/
│   ├── api/
│   │   └── index.js          # Axios 인스턴스 + JWT 자동 첨부 + 401 자동 로그아웃
│   ├── stores/
│   │   └── auth.js           # Pinia: 로그인/로그아웃/역할 관리
│   ├── router/
│   │   └── index.js          # Vue Router (인증 가드 포함)
│   ├── views/
│   │   ├── HomeView.vue      # 메인 페이지 (히어로 검색 + 소장 도서 그리드)
│   │   ├── SearchView.vue    # 검색 결과 목록 + 스켈레톤 로딩
│   │   ├── BookDetailView.vue# 도서 상세 (도서관 대출·전자책·가격 비교)
│   │   ├── LoginView.vue     # JWT 로그인 폼
│   │   ├── RegisterView.vue  # 회원가입 (실시간 중복 검사)
│   │   ├── ProfileView.vue   # 내 프로필
│   │   └── NotFoundView.vue  # 404 페이지
│   ├── components/
│   │   ├── NavBar.vue        # 통합 검색바 + 로그인/로그아웃 + 모바일 대응
│   │   ├── BookCard.vue      # 표지 + 도서 정보 카드
│   │   ├── FooterBar.vue     # 푸터 (영진전문대 도서관 바로가기 링크 포함)
│   │   └── NoticeBanner.vue  # 공지사항 배너
│   ├── App.vue
│   ├── main.js
│   └── style.css             # Tailwind directives + 커스텀 유틸리티
├── tailwind.config.js        # yju 커스텀 색상 팔레트 정의
├── vite.config.js            # /api → localhost:8080 프록시
├── package.json
├── index.html
├── Dockerfile                # nginx 멀티스테이지 빌드
└── nginx.conf                # SPA 라우팅 + /api/ 리버스 프록시
```

### 주요 기능
- **통합 검색**: 제목 / 저자 / ISBN 검색, 실시간 검색어 전달
- **도서 상세**: 도서관 소장 여부 + 대출 가능 상태, 전자책 링크, 알라딘·카카오 가격 비교
- **무한 스크롤**: 홈 화면 소장 도서 그리드 (Intersection Observer)
- **JWT 인증**: 로그인 후 토큰을 localStorage에 저장, 모든 API 요청에 자동 첨부
- **인증 가드**: `requiresAuth: true` 라우트는 비로그인 시 `/login` 리다이렉트
- **반응형 UI**: 모바일 햄버거 메뉴, Tailwind 반응형 클래스

### 커스텀 색상 (tailwind.config.js)
```js
colors: {
  yju: {
    DEFAULT: '#1e3a8a',   // 메인 네이비
    light:   '#2563eb',   // 밝은 파랑
    dark:    '#172554',   // 진한 네이비
    accent:  '#f59e0b',   // 강조 황금색
  }
}
```

### Axios 인터셉터 (src/api/index.js)
```js
// 요청마다 JWT 자동 첨부
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
// 401 응답 시 자동 로그아웃
api.interceptors.response.use(null, error => {
  if (error.response?.status === 401) {
    localStorage.removeItem('token')
    router.push('/login')
  }
  return Promise.reject(error)
})
```

---

## 2. 데이터베이스

### 사용 DBMS
- **PostgreSQL 16** (Docker 이미지: `pgvector/pgvector:pg16`)
- 익스텐션: `pg_trgm` (한국어 포함 부분 문자열 검색 가속)

### 연결 정보
| 항목 | 값 |
|------|----|
| DB명 | `ydanawa_db` |
| 유저 | `root` |
| 호스트 (개발, 로컬) | `localhost:5433` |
| 호스트 (컨테이너 내부) | `db:5432` |

### 테이블 구조

#### `books` — 통합 도서 정보
```sql
isbn            VARCHAR(32)   PRIMARY KEY
title           VARCHAR(255)  NOT NULL
title_norm      VARCHAR(500)  NOT NULL DEFAULT ''   -- 공백 제거 + 소문자 정규화
author          VARCHAR(255)
publisher       VARCHAR(255)
image_url       VARCHAR(2048)
published_date  DATE
price           DOUBLE PRECISION
created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
```

#### `book_image` — 도서 표지 이미지 (바이너리 저장)
```sql
id           BIGSERIAL     PRIMARY KEY
book_isbn    VARCHAR(32)   NOT NULL  REFERENCES books(isbn) ON DELETE CASCADE
kind         VARCHAR(50)   NOT NULL   -- 'cover', 'thumbnail' 등
source       VARCHAR(50)   NOT NULL   -- 'kakao', 'aladin', 'google' 등
content_type VARCHAR(100)
width        INTEGER
height       INTEGER
sha256       VARCHAR(64)   NOT NULL   -- 중복 저장 방지
bytes        BYTEA         NOT NULL
created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
UNIQUE (book_isbn, sha256)
```

#### `library_holding_cache` — 도서관 소장 정보 캐시
```sql
isbn         VARCHAR(32)   PRIMARY KEY
found        BOOLEAN       NOT NULL DEFAULT FALSE
available    BOOLEAN       NOT NULL DEFAULT FALSE
status_code  VARCHAR(50)   NOT NULL DEFAULT 'UNKNOWN'
status_text  VARCHAR(100)
location     VARCHAR(255)
call_number  VARCHAR(255)
detail_url   VARCHAR(2048)
checked_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
```
> Playwright 크롤링 결과를 캐시하여 반복 호출을 방지

#### 로그 테이블
- `search_log` — 검색어 / 학과 / 결과수 기록
- `click_log` — 도서 클릭 이벤트 기록

#### 기타 도메인 테이블
- `users` — 회원 (username, password, 학과, 학번 등)
- `user_roles` — 사용자 역할 (ROLE_USER, ROLE_ADMIN)
- `book_records` — (예약/대출 기록 등 확장용)
- `campus_notice` — 캠퍼스 공지사항

### 인덱스 전략
```sql
-- 일반 B-tree 인덱스
CREATE INDEX idx_books_title   ON books(title);
CREATE INDEX idx_books_author  ON books(author);

-- pg_trgm GIN 인덱스 (부분 문자열 검색)
CREATE INDEX idx_books_title_norm_trgm
    ON books USING gin (title_norm gin_trgm_ops);

-- 공백 제거 + 소문자 기반 검색 인덱스
CREATE INDEX idx_books_title_nospace
    ON books USING gin (REPLACE(LOWER(title), ' ', '') gin_trgm_ops);
CREATE INDEX idx_books_author_nospace
    ON books USING gin (REPLACE(LOWER(author), ' ', '') gin_trgm_ops);
```

---

## 3. 백엔드

### 기술 스택
| 항목 | 설명 |
|------|------|
| Spring Boot 3 | 메인 애플리케이션 프레임워크 |
| Spring Data JPA | ORM, PostgreSQL |
| Spring Security | JWT 인증/인가 |
| WebFlux WebClient | 외부 API 비동기 호출 |
| gRPC | Python 스크래퍼와 통신 |
| Gradle | 빌드 도구 |

### 패키지 구조 (`yju.danawa.com`)
```
web/           -- REST 컨트롤러
service/       -- 비즈니스 로직
domain/        -- JPA 엔티티
repository/    -- Spring Data 리포지토리
dto/           -- 요청/응답 DTO
security/      -- JWT 필터, Security 설정
config/        -- 각종 빈 설정
util/          -- 유틸리티
```

### REST API 엔드포인트

#### 도서 (`BookController`, `GrpcBookController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/books/search?q=` | 제목/저자 통합 검색 |
| GET | `/api/books/infinite?page=&size=` | 무한 스크롤 도서 목록 |
| GET | `/api/books/{isbn}` | 도서 상세 |
| GET | `/api/grpc/library-status/{isbn}` | 도서관 소장 여부 (gRPC 경유) |

#### 외부 도서 정보 (`ExternalBookController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/external/kakao?q=` | 카카오 도서 검색 |
| GET | `/api/external/aladin?isbn=` | 알라딘 도서 정보 |

#### 인증 (`AuthController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/auth/login` | JWT 토큰 발급 |
| POST | `/api/auth/register` | 회원가입 |
| GET | `/api/auth/check-username?username=` | 아이디 중복 확인 |

#### 사용자 (`UserProfileController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/users/me` | 내 프로필 조회 |
| PUT | `/api/users/me` | 프로필 수정 |

#### 로그 (`LogController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/logs/search` | 검색 로그 저장 |
| POST | `/api/logs/click` | 클릭 로그 저장 |

#### 공지사항 (`NoticeController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/notices` | 공지사항 목록 |

#### 이미지 (`BookImageController`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/images/{isbn}` | 도서 표지 이미지 제공 (DB BYTEA) |

### 핵심 서비스

#### `BookService`
- pg_trgm 기반 한국어 유사도 검색
- 공백 제거 + 소문자 정규화(title_norm) 활용

#### `YjuLibraryService` + `LibraryGrpcClient`
- gRPC로 Python `library-scraper` 서비스에 소장 여부 쿼리
- `LibraryHoldingCache` 테이블에 결과 캐싱
- `LibraryRateLimiter`로 과도한 스크래핑 방지

#### `EbookLibraryService`
- 영진전문대 전자책 서비스(ebook.yjc.ac.kr) 연동
- 캐시 TTL: 900초

#### `ExternalBookService`
- 카카오 도서 API, 알라딘 API 연동 (WebClient)

#### `BookPriceService`
- 알라딘 상품 조회로 온라인 서점 가격 제공

#### `BookImageService` / `CrawlerImageFileService`
- DB BYTEA 기반 이미지 저장/조회
- SHA256 해시로 중복 이미지 방지

#### `JwtService`
- JWT 토큰 생성 / 검증
- `JwtAuthFilter`에서 모든 요청에 적용

#### `CampusNoticeService`
- 캠퍼스 공지사항 관리

#### `UserSeedConfig`
- 앱 시작 시 기본 admin 계정 자동 생성

### gRPC 구조
- 백엔드(Java) → `LibraryGrpcClient` → gRPC → Python `library-scraper` (포트 9090)
- Python 스크래퍼가 Playwright로 영진전문대 도서관 페이지 크롤링
- HTTP 상태 API도 별도 제공 (포트 8090)

### JWT 인증 흐름
```
클라이언트 → POST /api/auth/login
  ↓ (아이디/비밀번호 검증)
서버 → JWT 토큰 반환
  ↓ (이후 모든 요청에 Authorization: Bearer <token>)
JwtAuthFilter → 토큰 검증 → SecurityContext 설정
```

---

## 4. 인프라 / Docker

### 컨테이너 구성 (compose.yaml)
| 서비스 | 컨테이너명 | 이미지/빌드 | 포트 |
|--------|-----------|------------|------|
| `db` | ydanawa-db | pgvector/pgvector:pg16 | 5433:5432 |
| `library-scraper` | ydanawa-library-scraper | ./library-scraper/Dockerfile | 8090, 9090 |
| `backend` | ydanawa-backend | ./Dockerfile | 8080:8080 |
| `frontend` | ydanawa-frontend | ./frontend/Dockerfile | 80:80 |

### 네트워크
- 전용 브릿지 네트워크 `ydanawa-network`
- 컨테이너 간 통신: 서비스명(hostname) 사용 (`db:5432`, `library-scraper:9090`)
- 외부 접근: 호스트 포트 매핑

### nginx (frontend 컨테이너)
```nginx
# SPA 라우팅 (새로고침 대응)
location / {
    try_files $uri $uri/ /index.html;
}
# API 리버스 프록시
location /api/ {
    proxy_pass http://backend:8080;
}
# 이미지 리버스 프록시
location /images/ {
    proxy_pass http://backend:8080;
}
```

### Python 서비스 (library-scraper)
- gRPC 서버 (포트 9090): 도서관 소장 여부 스크래핑
- HTTP 서버 (포트 8090): 상태 확인 API
- Playwright 기반 headless 브라우저 크롤링
- 대상: 영진전문대 도서관 (lib.yjc.ac.kr), 전자책 (ebook.yjc.ac.kr)

---

## 5. 배포 스크립트

### START.bat (Windows 원클릭 배포)
단계별 자동 실행:
```
[0/5] Docker 엔진 확인
[1/5] crawling.py 실행 (도서 데이터 크롤링)
[2/5] Gradle bootJar 빌드 (백엔드 JAR)
[3/5] npm install + npm run build (프론트엔드)
[4/5] docker compose build --no-cache + up -d --force-recreate
[4.5/5] library-scraper 동작 확인 (선택)
[5/5] 완료 (접속 주소 안내)
```

### 접속 주소
```
http://localhost       -- 메인 사이트 (nginx → Vue SPA)
http://localhost:8080  -- 백엔드 API 직접 접근 (개발용)
http://localhost:5433  -- PostgreSQL 직접 접근 (개발/DB 툴)
```

### 유용한 명령어
```bash
docker compose ps              # 컨테이너 상태 확인
docker compose logs -f         # 전체 로그 실시간 확인
docker compose logs -f backend # 백엔드 로그만
docker compose down            # 전체 중지
docker compose build --no-cache frontend && docker compose up -d --force-recreate frontend
                               # 프론트엔드만 재빌드/재시작
```

---

*문서 최종 업데이트: 2026-03-06*
