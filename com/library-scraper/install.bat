@echo off
chcp 65001 >nul
echo ============================================
echo  Library Scraper - 패키지 설치
echo ============================================

echo.
echo [1/4] grpcio / grpcio-tools 업그레이드 (library_pb2_grpc 호환)...
python -m pip install --upgrade grpcio grpcio-tools

echo.
echo [2/4] protobuf 6.x 설치 (library_pb2.py protobuf 6.31.1 기준)...
python -m pip install "protobuf>=6.31.1,<7.0"

echo.
echo [3/4] 나머지 패키지 설치...
python -m pip install "fastapi==0.109.0" "uvicorn[standard]==0.27.0" "playwright==1.41.0" "pydantic==2.5.3" "python-dotenv==1.0.1" "httpx==0.27.0"

echo.
echo [4/4] Playwright Chromium 브라우저 설치...
python -m playwright install chromium

echo.
echo ============================================
echo  설치 확인
echo ============================================
python -c "import grpc; print('grpcio      :', grpc.__version__)"
python -c "import google.protobuf; print('protobuf    :', google.protobuf.__version__)"
python -c "import library_pb2; import library_pb2_grpc; print('library_pb2 : OK')"
python -c "import fastapi; print('fastapi     :', fastapi.__version__)"
python -c "import uvicorn; print('uvicorn     :', uvicorn.__version__)"
python -c "from playwright.sync_api import sync_playwright; print('playwright  : OK')"
python -c "import pydantic; print('pydantic    :', pydantic.__version__)"
python -c "import httpx; print('httpx       :', httpx.__version__)"
python -c "import dotenv; print('python-dotenv: OK')"

echo.
echo ============================================
echo  완료!
echo ============================================

