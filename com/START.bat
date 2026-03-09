@echo off

setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

echo.
echo ========================================
echo   Y-DANAWA 도커 빌드 및 실행
echo ========================================
echo.

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo [0/5] 도커 엔진 확인 중...
where docker >nul 2>&1
if errorlevel 1 (
    echo [오류] docker 명령어를 찾을 수 없습니다.
    echo        Docker Desktop을 설치해주세요.
    pause
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo [오류] docker compose를 사용할 수 없습니다.
    echo        Docker Desktop을 최신 버전으로 업데이트해주세요.
    pause
    exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
    echo [오류] Docker 데몬에 연결할 수 없습니다.
    echo.
    echo [해결 방법]
    echo   1^) Docker Desktop을 먼저 실행해주세요.
    echo   2^) 터미널을 관리자 권한으로 실행해주세요.
    echo   3^) docker-users 그룹 권한 부여:
    echo      net localgroup docker-users %USERNAME% /add
    echo   4^) 위 명령 실행 후 로그아웃 후 다시 로그인해주세요.
    echo.
    pause
    exit /b 1
)
echo [완료] 도커 엔진 연결 확인
echo.

:: ─── 백엔드 빌드 ───
echo [1/5] 백엔드 JAR 빌드 중...
call "%ROOT%gradlew.bat" bootJar -x test
if errorlevel 1 (
    echo [오류] Gradle 빌드 실패
    pause
    exit /b 1
)
echo [완료] 빌드 성공
echo.

:: ─── 프론트엔드 빌드 ───
echo [2/5] 프론트엔드 빌드 중...
where node >nul 2>&1
if errorlevel 1 goto fe_no_node
if not exist "%ROOT%frontend\package.json" goto fe_no_pkg

pushd "%ROOT%frontend"
if exist "node_modules" goto fe_build
echo   - npm install 중...
cmd /c "npm install --legacy-peer-deps"
if errorlevel 1 goto fe_error

:fe_build
echo   - npm run build 중...
cmd /c "npm run build"
if errorlevel 1 goto fe_error

popd
echo [완료] 프론트엔드 빌드 성공
echo.
goto after_frontend

:fe_error
popd
echo [오류] 프론트엔드 빌드 실패
pause
exit /b 1

:fe_no_node
echo [경고] Node.js 없음 - 프론트엔드 빌드 건너뜁니다.
echo        설치: https://nodejs.org
goto after_frontend

:fe_no_pkg
echo [경고] frontend\package.json 없음 - 건너뜁니다.
goto after_frontend

:after_frontend
::: ─── 도커 빌드 & DB 시작 ───
echo [3/5] 도커 이미지 빌드 및 DB 컨테이너 시작 중...
docker compose build --no-cache
if errorlevel 1 (
    echo [오류] 도커 이미지 빌드 실패
    pause
    exit /b 1
)

:: 기존 컨테이너 정리 (이름 충돌 방지)
docker compose down --remove-orphans >nul 2>&1

:: DB 컨테이너만 먼저 시작 (크롤링을 위해)
docker compose up -d db
if errorlevel 1 (
    echo [오류] DB 컨테이너 시작 실패
    pause
    exit /b 1
)

:: DB healthy 대기 (최대 60초)
echo   - DB 준비 대기 중...
set "DB_READY=0"
set "DB_WAIT=0"
:db_wait_loop
if !DB_READY! EQU 1 goto db_wait_done
if !DB_WAIT! GEQ 12 goto db_wait_done
set /a DB_WAIT+=1
docker exec ydanawa-db pg_isready -U root -d ydanawa_db >nul 2>&1
if not errorlevel 1 (
    set "DB_READY=1"
    echo   - DB 준비 완료
    goto db_wait_done
)
echo   - DB 대기 중... (!DB_WAIT!/12)
timeout /t 5 /nobreak >nul
goto db_wait_loop
:db_wait_done
if !DB_READY! EQU 0 (
    echo [오류] DB가 시간 내에 준비되지 않았습니다.
    pause
    exit /b 1
)
echo [완료] DB 컨테이너 시작됨
echo.

:: ─── 크롤링 실행 (DB 준비 후) ───
echo [4/5] 크롤링 실행 중...
where python >nul 2>&1
if errorlevel 1 (
    echo [경고] Python을 찾을 수 없습니다. 크롤링을 건너뜁니다.
    echo        Python 설치: https://www.python.org
    goto after_crawling
)

if exist "%ROOT%crawling.py" (
    python "%ROOT%crawling.py"
    if errorlevel 1 (
        echo [경고] 크롤링 중 에러가 발생했습니다. 서버 시작은 계속 진행합니다.
    ) else (
        echo [완료] 크롤링 성공
    )
) else (
    echo [안내] crawling.py가 없습니다. 크롤링을 건너뜁니다.
)

:after_crawling
echo.

:: ─── 나머지 컨테이너 시작 ───
echo [4.5/5] 나머지 컨테이너 시작 중...
docker compose up -d
if errorlevel 1 (
    echo [오류] docker compose up 실패
    pause
    exit /b 1
)
echo [완료] 모든 컨테이너 시작됨
echo.

:: ─── 스크래퍼 확인 (선택) ───
echo [4.5/5] 도서관 스크래퍼 동작 확인 중 (선택)...
set "SCRAPER_TEST=%ROOT%library-scraper\test_live.py"

if not exist "%SCRAPER_TEST%" (
    echo [안내] library-scraper\test_live.py 를 찾을 수 없습니다. 건너뜁니다.
    goto after_scraper
)

if not exist "%ROOT%library-scraper\.env" (
    echo [경고] library-scraper\.env 파일이 없습니다. 건너뜁니다.
    goto after_scraper
)

pushd "%ROOT%library-scraper"
python "%SCRAPER_TEST%"
if errorlevel 1 (
    echo [경고] 스크래퍼 동작 확인 실패.
) else (
    echo [완료] 스크래퍼 동작 확인 완료
)
popd

:after_scraper
echo.
echo [5/5] 완료!
echo.
echo ========================================
echo   모든 작업이 완료되었습니다
echo ========================================
echo.
echo 접속 주소 : http://localhost
echo.
echo 상태 확인 : docker compose ps
echo 로그 확인 : docker compose logs -f
echo 중지      : docker compose down
echo.
pause
endlocal
