#!/usr/bin/env bash
# 로컬 개발 실행: Docker 백엔드 준비 후 Vite 프론트를 실행한다.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_HEALTH_URL="http://localhost:8080/actuator/health"
MAX_WAIT_SECONDS=60

cd "$PROJECT_ROOT"

echo "백엔드와 DB를 시작합니다..."
docker compose up -d

echo "백엔드 준비를 기다립니다..."
for ((elapsed = 0; elapsed < MAX_WAIT_SECONDS; elapsed += 2)); do
  if curl --silent --fail "$BACKEND_HEALTH_URL" >/dev/null; then
    echo "백엔드 준비 완료"
    echo "프론트를 http://localhost:5173 에서 시작합니다..."
    cd frontend
    exec npm run dev -- --port 5173 --strictPort
  fi

  sleep 2
done

echo "오류: ${MAX_WAIT_SECONDS}초 안에 백엔드가 준비되지 않았습니다." >&2
echo "확인 명령: docker compose logs app" >&2
exit 1
