# 🚀 Y-DANAWA 프로젝트 실행 가이드

> **최종 업데이트**: 2026-03-07
>
> 이 문서는 Git에서 클론한 뒤 **처음부터 끝까지** 프로젝트를 실행하는 방법을 설명합니다.

---

## 📋 목차

1. [사전 준비 (필수 설치)](#1--사전-준비-필수-설치)
2. [프로젝트 클론](#2--프로젝트-클론)
3. [환경 변수 (.env) 설정](#3--환경-변수-env-설정)
4. [JDK 경로 설정 (gradle.properties)](#4--jdk-경로-설정-gradleproperties)
5. [Docker Desktop 설정](#5--docker-desktop-설정)
6. [자동 실행 (START.bat)](#6--자동-실행-startbat)
7. [수동 실행 (단계별)](#7--수동-실행-단계별)
8. [실행 확인](#8--실행-확인)
9. [자주 발생하는 오류 & 해결법](#9--자주-발생하는-오류--해결법)
10. [개발 모드 (Docker 없이)](#10--개발-모드-docker-없이)
11. [종료 & 정리](#11--종료--정리)

---

## 1. 📦 사전 준비 (필수 설치)

아래 프로그램들이 **모두** 설치되어 있어야 합니다.

| 프로그램 | 버전 | 다운로드 | 확인 명령어 |
|---------|------|---------|-----------|
| **Docker Desktop** | 최신 | [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/) | `docker --version` |
| **JDK 21** | 21 이상 | [adoptium.net](https://adoptium.net/) 또는 IntelliJ 내장 | `java -version` |
| **Node.js** | 20 이상 | [nodejs.org](https://nodejs.org/) | `node -v` |
| **Python** | 3.10 이상 | [python.org](https://www.python.org/) | `python --version` |
| **Git** | 최신 | [git-scm.com](https://git-scm.com/) | `git --version` |

### ✅ 설치 확인 (PowerShell에서 실행)

```powershell
docker --version
java -version
node -v
npm -v
python --version
git --version
```

> 💡 **모든 명령어가 정상 출력**되어야 합니다. 하나라도 안 되면 해당 프로그램을 설치하세요.

---

## 2. 📥 프로젝트 클론

```powershell
# 원하는 폴더로 이동
cd C:\

# Git 클론
git clone <깃 저장소 URL> yjudanawa-damo
cd yjudanawa-damo
```

클론 후 폴더 구조:
```
yjudanawa-damo/
└── com/                    ← 메인 프로젝트 루트 (여기서 모든 작업)
    ├── compose.yaml        ← Docker Compose 설정
    ├── Dockerfile          ← 백엔드 Docker 이미지
    ├── build.gradle        ← 백엔드 Gradle 빌드
    ├── gradlew.bat         ← Gradle Wrapper (JDK만 있으면 실행 가능)
    ├── START.bat           ← 원클릭 자동 실행 스크립트
    ├── crawling.py         ← 도서 데이터 크롤링 스크립트
    ├── .env.example        ← 환경 변수 템플릿 (루트)
    ├── frontend/           ← Vue.js 프론트엔드
    │   ├── Dockerfile
    │   ├── package.json
    │   └── .env.example
    ├── library-scraper/    ← Python 스크래퍼 서비스
    │   ├── Dockerfile
    │   ├── requirements.txt
    │   └── .env.example
    └── src/                ← Spring Boot 백엔드 소스
```

---

## 3. 🔑 환경 변수 (.env) 설정

> ⚠️ **가장 중요한 단계!** `.env` 파일이 없으면 서비스가 동작하지 않습니다.
>
> `.env` 파일은 **Git에 올라가지 않으므로** 반드시 직접 생성해야 합니다.

### 3-1. 루트 `.env` 파일 생성

```powershell
cd C:\yjudanawa-damo\com
Copy-Item .env.example .env
```

생성된 `.env` 파일을 열어서 **실제 API 키**를 입력합니다:

```dotenv
# ─── C:\yjudanawa-damo\com\.env ───

# Kakao 도서 검색 API (https://developers.kakao.com/ 에서 발급)
KAKAO_REST_API_KEY=여기에_카카오_REST_API_키_입력

# Aladin TTB API (https://www.aladin.co.kr/ttb/api/Login.aspx 에서 발급)
ALADIN_TTB_KEY=여기에_알라딘_TTBKey_입력

# Google Books API (https://console.cloud.google.com/ 에서 발급)
GOOGLE_API_KEY=여기에_구글_API_키_입력

# 아래는 수정하지 않아도 됩니다
APP_GRPC_HOST=localhost
APP_GRPC_PORT=9090
PLACEHOLDER_IMAGE_URL=https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80+%EC%97%86%EC%9D%8C
```

### 3-2. library-scraper `.env` 파일 생성

```powershell
cd C:\yjudanawa-damo\com\library-scraper
Copy-Item .env.example .env
```

생성된 `.env` 파일을 열어서 **학교 도서관 로그인 정보**를 입력합니다:

```dotenv
# ─── C:\yjudanawa-damo\com\library-scraper\.env ───

# 런타임 설정 (수정 불필요)
HTTP_HOST=0.0.0.0
HTTP_PORT=8090
GRPC_HOST=0.0.0.0
GRPC_PORT=9090

# 학교 도서관 URL (수정 불필요)
LIB_BASE_URL=https://lib.yju.ac.kr
LIB_LOGIN_URL=https://lib.yju.ac.kr/Cheetah/Login/Login
LIB_SEARCH_URL_PREFIX=https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/

# ⭐ 여기에 본인의 학번/비밀번호 입력
LIBRARY_ID=본인_학번
LIBRARY_PW=본인_비밀번호
LIB_USER_ID=본인_학번
LIB_USER_PASSWORD=본인_비밀번호

# Playwright 설정 (수정 불필요)
LIB_ID_SELECTOR=#formText input[name='loginId']
LIB_PASSWORD_SELECTOR=#formText input[name='loginpwd']
LIB_SUBMIT_SELECTOR=#formText button[type='submit']
PLAYWRIGHT_HEADLESS=true
PLAYWRIGHT_TIMEOUT_MS=10000
PLAYWRIGHT_HARD_TIMEOUT_MS=10000
PLAYWRIGHT_CONCURRENCY=4
STATUS_WAIT_TIMEOUT_MS=10000

# 이미지/API 설정 (수정 불필요)
EXTERNAL_API_TIMEOUT_SEC=8
PLACEHOLDER_IMAGE_URL=https://placehold.co/120x174?text=%EC%9D%B4%EB%AF%B8%EC%A7%80+%EC%97%86%EC%9D%8C
SCRAPER_PUBLIC_BASE_URL=http://localhost:8090

# Ebook 설정 (수정 불필요)
EBOOK_BASE_URL=https://ebook.yjc.ac.kr
EBOOK_SEARCH_URL_PREFIX=https://ebook.yjc.ac.kr/search/?srch_order=total&src_key=
ALLOWED_EBOOK_HOSTS=ebook.yjc.ac.kr
EBOOK_CACHE_TTL_SEC=900
EBOOK_RESULT_WAIT_TIMEOUT_MS=10000
EBOOK_DETAIL_WAIT_TIMEOUT_MS=10000
EBOOK_TOTAL_TIMEOUT_SEC=10
```

### 3-3. (선택) 프론트엔드 `.env` 파일

프론트엔드는 `.env` 없이도 동작합니다. 필요한 경우:

```powershell
cd C:\yjudanawa-damo\com\frontend
Copy-Item .env.example .env
```

### 📁 최종 `.env` 파일 체크리스트

| 파일 경로 | 필수 여부 | 핵심 입력값 |
|----------|---------|-----------|
| `com/.env` | ✅ **필수** | `KAKAO_REST_API_KEY`, `ALADIN_TTB_KEY`, `GOOGLE_API_KEY` |
| `com/library-scraper/.env` | ✅ **필수** | `LIB_USER_ID`, `LIB_USER_PASSWORD` |
| `com/frontend/.env` | ⬜ 선택 | 없어도 동작 |

> 🔒 **주의**: `.env` 파일은 절대 Git에 커밋하지 마세요! `.gitignore`에 이미 등록되어 있습니다.

---

## 4. ☕ JDK 경로 설정 (gradle.properties)

`com/gradle.properties` 파일에서 **본인 PC의 JDK 21 경로**를 설정해야 합니다.

```powershell
# gradle.properties 파일을 텍스트 편집기로 엽니다
notepad C:\yjudanawa-damo\com\gradle.properties
```

파일 내용을 **본인의 JDK 경로**로 수정합니다:

```properties
# 본인 PC에 설치된 JDK 21 경로로 변경하세요
# 역슬래시(\)를 이중으로 써야 합니다 (\\)
org.gradle.java.home=C:\\Users\\본인유저명\\.jdks\\temurin-21.0.6
```

### JDK 경로 찾는 방법

```powershell
# 방법 1: 시스템에 설치된 Java 확인
where java

# 방법 2: IntelliJ에서 다운로드한 JDK 확인
dir "$env:USERPROFILE\.jdks"

# 방법 3: JAVA_HOME 확인
echo $env:JAVA_HOME
```

**흔한 JDK 경로 예시:**

| 설치 방법 | 경로 예시 |
|----------|---------|
| IntelliJ 다운로드 | `C:\Users\유저명\.jdks\temurin-21.0.6` |
| Adoptium 설치 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot` |
| Oracle JDK | `C:\Program Files\Java\jdk-21` |
| MS OpenJDK | `C:\Users\유저명\.jdks\ms-21.0.9` |

---

## 5. 🐳 Docker Desktop 설정

### 5-1. Docker Desktop 실행

1. **Docker Desktop**을 실행합니다 (시작 메뉴에서 검색)
2. 트레이 아이콘에서 **고래 아이콘이 움직이지 않을 때까지** 기다립니다 (= 준비 완료)
3. 정상 확인:

```powershell
docker info
```

> `Server: Docker Desktop` 등의 정보가 출력되면 정상입니다.

### 5-2. Docker 권한 오류 시

`error during connect: ... permission denied` 에러가 나오면:

```powershell
# 관리자 PowerShell에서 실행
net localgroup docker-users %USERNAME% /add
```

이후 **로그아웃 → 재로그인** 해야 적용됩니다.

### 5-3. Docker 리소스 설정 (권장)

Docker Desktop → ⚙️ Settings → Resources:

| 항목 | 권장값 |
|------|-------|
| CPUs | 4 이상 |
| Memory | 4GB 이상 |
| Disk image size | 20GB 이상 |

---

## 6. ⚡ 자동 실행 (START.bat) — 가장 쉬운 방법

모든 `.env` 설정과 JDK 경로 설정이 완료되었다면, **더블클릭 한 번으로 실행**할 수 있습니다.

### 실행 방법

```
📁 yjudanawa-damo/
└── 📁 com/
    └── 🖱️ START.bat   ← 이 파일을 더블클릭!
```

또는 PowerShell에서:

```powershell
cd C:\yjudanawa-damo\com
cmd.exe /c START.bat
```

### START.bat이 자동으로 하는 일

| 단계 | 작업 | 설명 |
|------|------|------|
| [0/5] | Docker 엔진 확인 | Docker가 실행 중인지 확인 |
| [1/5] | 백엔드 JAR 빌드 | `gradlew.bat bootJar` (Spring Boot) |
| [2/5] | 프론트엔드 빌드 | `npm install` → `npm run build` (Vue.js) |
| [3/5] | Docker 이미지 빌드 + DB 시작 | `docker compose build` → DB 컨테이너 시작 |
| [4/5] | 크롤링 실행 | `crawling.py`로 도서 데이터 DB에 저장 |
| [5/5] | 전체 서비스 시작 | `docker compose up -d` |

### 완료 후

```
========================================
  모든 작업이 완료되었습니다
========================================

접속 주소 : http://localhost
```

브라우저에서 **http://localhost** 로 접속하면 됩니다! 🎉

---

## 7. 🔧 수동 실행 (단계별)

START.bat이 안 되거나, 단계별로 디버깅하고 싶을 때 사용합니다.

### Step 1: 백엔드 JAR 빌드

```powershell
cd C:\yjudanawa-damo\com

# Gradle로 Spring Boot JAR 빌드 (테스트 생략)
.\gradlew.bat bootJar -x test
```

✅ 성공 시: `BUILD SUCCESSFUL` 출력
📁 결과물: `build/libs/com-0.0.1-SNAPSHOT.jar`

### Step 2: 프론트엔드 빌드

```powershell
cd C:\yjudanawa-damo\com\frontend

# 의존성 설치
npm install --legacy-peer-deps

# 프로덕션 빌드
npm run build
```

✅ 성공 시: `✓ built in X.XXs` 출력
📁 결과물: `frontend/dist/` 폴더

### Step 3: Docker 이미지 빌드

```powershell
cd C:\yjudanawa-damo\com

# 모든 서비스의 Docker 이미지 빌드
docker compose build
```

✅ 성공 시: 에러 없이 빌드 완료

### Step 4: DB 컨테이너 시작 & 크롤링

```powershell
# DB만 먼저 시작
docker compose up -d db

# DB가 준비될 때까지 대기 (약 10~15초)
Start-Sleep -Seconds 15

# DB 준비 확인
docker exec ydanawa-db pg_isready -U root -d ydanawa_db

# 크롤링 실행 (도서 데이터 DB에 저장)
python crawling.py
```

### Step 5: 전체 서비스 시작

```powershell
# 모든 컨테이너 시작
docker compose up -d

# 상태 확인
docker compose ps
```

✅ 모든 서비스가 `Up` 또는 `Up (healthy)` 상태여야 합니다:

```
NAME                      STATUS
ydanawa-backend           Up (healthy)    ← 8080 포트
ydanawa-db                Up (healthy)    ← 5433 포트
ydanawa-frontend          Up              ← 80 포트
ydanawa-library-scraper   Up (healthy)    ← 8090, 9090 포트
ydanawa-redis             Up              ← 6379 포트
```

---

## 8. ✅ 실행 확인

### 웹 브라우저 접속

| 서비스 | URL | 설명 |
|--------|-----|------|
| **메인 사이트** | [http://localhost](http://localhost) | 프론트엔드 (Vue.js) |
| **백엔드 API** | [http://localhost:8080/api/books/infinite?page=0&size=5](http://localhost:8080/api/books/infinite?page=0&size=5) | 도서 목록 API |
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | API 문서 |
| **스크래퍼 API** | [http://localhost:8090/health](http://localhost:8090/health) | 스크래퍼 헬스체크 |

### PowerShell에서 확인

```powershell
# 컨테이너 상태
docker compose ps

# 백엔드 로그
docker compose logs backend --tail 30

# 전체 로그 (실시간)
docker compose logs -f

# 특정 서비스 로그
docker compose logs library-scraper --tail 50
```

---

## 9. ❌ 자주 발생하는 오류 & 해결법

### 오류 1: `Docker 데몬에 연결할 수 없습니다`

```
[오류] Docker 데몬에 연결할 수 없습니다.
```

**해결:**
1. Docker Desktop을 먼저 실행하세요
2. 트레이에서 고래 아이콘이 멈출 때까지 기다리세요
3. 그래도 안 되면: 관리자 PowerShell에서 `net localgroup docker-users %USERNAME% /add` 실행 후 재로그인

---

### 오류 2: `Gradle 빌드 실패` — JDK 경로 문제

```
ERROR: JAVA_HOME is set to an invalid directory
```

**해결:**
1. `com/gradle.properties` 파일을 열고 본인의 JDK 21 경로로 수정하세요

```properties
org.gradle.java.home=C:\\Users\\본인유저명\\.jdks\\temurin-21.0.6
```

2. JDK가 없으면 IntelliJ에서: **File → Project Structure → SDK → + → Download JDK → Temurin 21** 선택

---

### 오류 3: `npm ERESOLVE unable to resolve dependency tree`

```
npm error ERESOLVE unable to resolve dependency tree
npm error peer vite@"^5.0.0 || ^6.0.0" from @vitejs/plugin-vue@5.2.4
```

**해결:**

```powershell
cd C:\yjudanawa-damo\com\frontend
npm install --legacy-peer-deps
```

---

### 오류 4: `.env 파일이 없습니다`

```
[경고] library-scraper/.env 파일이 없습니다.
```

**해결:**
[3. 환경 변수 (.env) 설정](#3--환경-변수-env-설정) 섹션을 따라 `.env` 파일을 생성하세요.

---

### 오류 5: `ALADIN_TTB_KEY가 설정되지 않았습니다`

```
[경고] ALADIN_TTB_KEY가 설정되지 않았습니다.
```

**해결:**
`com/.env` 파일에 알라딘 API 키를 입력하세요:
```dotenv
ALADIN_TTB_KEY=ttb실제키값
```

키 발급: [알라딘 TTB API](https://www.aladin.co.kr/ttb/api/Login.aspx)

---

### 오류 6: 포트 충돌 `port is already allocated`

```
Error starting userland proxy: listen tcp4 0.0.0.0:80: bind: address already in use
```

**해결:**

```powershell
# 어떤 프로세스가 포트를 사용 중인지 확인
netstat -ano | findstr :80

# 해당 PID의 프로세스 종료
taskkill /PID <PID번호> /F

# 또는 기존 Docker 컨테이너 정리 후 재시작
docker compose down
docker compose up -d
```

---

### 오류 7: 백엔드가 `unhealthy` 상태

```powershell
# 로그 확인
docker compose logs backend --tail 50
```

흔한 원인:
- DB 연결 실패 → DB 컨테이너가 healthy인지 확인: `docker compose ps db`
- `.env` API 키 누락 → `com/.env` 파일 확인
- 시작 시간 부족 → 2~3분 더 기다려보세요 (start_period: 120초)

---

### 오류 8: `psycopg2` 또는 Python DB 라이브러리 없음 (크롤링 시)

```
ModuleNotFoundError: No module named 'psycopg2'
```

**해결:**

```powershell
pip install psycopg2-binary
```

---

## 10. 💻 개발 모드 (Docker 없이)

프론트엔드/백엔드를 Docker 없이 직접 실행하여 **핫 리로드** 개발을 할 수 있습니다.

### DB + Redis만 Docker로 실행

```powershell
cd C:\yjudanawa-damo\com
docker compose up -d db redis
```

### 백엔드 실행 (IntelliJ)

1. IntelliJ에서 `com` 폴더를 프로젝트로 열기
2. `src/main/java/yju/danawa/com/ComApplication.java` 실행 (Run)
3. 또는 터미널에서:

```powershell
cd C:\yjudanawa-damo\com
.\gradlew.bat bootRun
```

> 백엔드는 `http://localhost:8080` 에서 실행됩니다.

### 프론트엔드 개발 서버 실행

```powershell
cd C:\yjudanawa-damo\com\frontend
npm install --legacy-peer-deps
npm run dev
```

> 프론트엔드 개발 서버: `http://localhost:5173`
>
> `/api` 요청은 자동으로 `http://localhost:8080`으로 프록시됩니다.

### 스크래퍼 실행 (선택)

```powershell
cd C:\yjudanawa-damo\com\library-scraper
pip install -r requirements.txt
python -m playwright install chromium
python run_servers.py
```

> gRPC 서버: `localhost:9090` / HTTP 서버: `localhost:8090`

---

## 11. 🛑 종료 & 정리

### 서비스 중지 (데이터 유지)

```powershell
cd C:\yjudanawa-damo\com
docker compose down
```

### 서비스 중지 + 데이터 삭제 (DB 초기화)

```powershell
docker compose down -v
```

> ⚠️ `-v` 옵션은 DB 데이터도 함께 삭제합니다!

### Docker 이미지까지 삭제 (완전 정리)

```powershell
docker compose down -v --rmi all
```

### 다시 시작

```powershell
cd C:\yjudanawa-damo\com
cmd.exe /c START.bat
```

---

## 📌 빠른 참조 카드

```
┌──────────────────────────────────────────────────┐
│              Y-DANAWA 빠른 시작                    │
├──────────────────────────────────────────────────┤
│                                                  │
│  1. Docker Desktop 실행                           │
│  2. .env 파일 생성 (API 키 입력)                    │
│     • com/.env                                   │
│     • com/library-scraper/.env                   │
│  3. gradle.properties JDK 경로 수정               │
│  4. com/START.bat 더블클릭                         │
│  5. http://localhost 접속                         │
│                                                  │
├──────────────────────────────────────────────────┤
│  상태 확인:  docker compose ps                    │
│  로그 확인:  docker compose logs -f              │
│  서비스 중지: docker compose down                 │
│  재시작:     docker compose up -d                 │
└──────────────────────────────────────────────────┘
```

---

## 🔑 API 키 발급 가이드

### Kakao REST API Key

1. [Kakao Developers](https://developers.kakao.com/) 접속 → 로그인
2. **내 애플리케이션** → **애플리케이션 추가하기**
3. 앱 이름: `Y-Danawa` (자유)
4. 생성된 앱 클릭 → **앱 키** → **REST API 키** 복사

### Aladin TTB Key

1. [알라딘 TTB](https://www.aladin.co.kr/ttb/api/Login.aspx) 접속 → 로그인
2. **TTB 키 발급받기** 클릭
3. 발급된 `ttbXXXXXXXXXXXX` 형태의 키 복사

### Google API Key

1. [Google Cloud Console](https://console.cloud.google.com/) 접속 → 로그인
2. 프로젝트 생성 → **API 및 서비스** → **라이브러리**
3. **Books API** 검색 → **사용 설정**
4. **사용자 인증 정보** → **API 키 만들기** → 키 복사

