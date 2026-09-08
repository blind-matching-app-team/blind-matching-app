#!/usr/bin/env bash
# 로컬 개발 실행: Docker 백엔드 준비 후 Vite 프론트를 실행한다.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_HEALTH_URL="http://localhost:8080/actuator/health"
MAX_WAIT_SECONDS=60

cd "$PROJECT_ROOT"

print_docker_install_instructions() {
  cat >&2 <<'EOF'
Docker 설치 명령 (Ubuntu 개발 환경):
  curl -fsSL https://get.docker.com -o get-docker.sh
  sudo sh get-docker.sh
  sudo usermod -aG docker "$USER"

명령 실행 후 터미널을 완전히 닫았다가 다시 열어야 docker 그룹 권한이 적용됩니다.
WSL에서 Docker Desktop을 사용한다면 Docker Desktop을 실행하고
Settings > Resources > WSL Integration에서 현재 Ubuntu 배포판을 활성화하세요.
EOF
}

print_node_install_instructions() {
  cat >&2 <<'EOF'
Node.js 및 npm 설치 명령 (Ubuntu, Node.js 22):
  sudo apt update
  sudo apt install -y curl
  curl -fsSL https://deb.nodesource.com/setup_22.x -o /tmp/nodesource_setup.sh
  sudo -E bash /tmp/nodesource_setup.sh
  sudo apt install -y nodejs
EOF
}

if ! command -v docker >/dev/null 2>&1; then
  echo "오류: docker를 찾을 수 없습니다. Docker Engine 또는 Docker Desktop을 설치하세요." >&2
  print_docker_install_instructions
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "오류: Docker Compose 플러그인을 사용할 수 없습니다." >&2
  print_docker_install_instructions
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "오류: Docker 데몬에 연결할 수 없습니다. Docker Desktop 또는 Docker Engine 실행 상태와 권한을 확인하세요." >&2
  echo "Ubuntu Docker Engine 시작 명령: sudo systemctl start docker" >&2
  echo "WSL에서는 Docker Desktop 실행 및 WSL Integration 활성화 여부를 확인하세요." >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "오류: npm을 찾을 수 없습니다. Node.js와 npm을 설치한 뒤 다시 실행하세요." >&2
  print_node_install_instructions
  exit 1
fi

if [[ ! -x frontend/node_modules/.bin/vite ]]; then
  echo "프론트 의존성을 설치합니다..."
  (
    cd frontend
    npm ci
  )
fi

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
