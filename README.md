# blind-matching-app

대화 기반 단계적 프로필 공개 매칭 서비스입니다.

## 디렉터리 구조

```text
blind-matching-app/
├─ backend/               # Spring Boot + JPA + QueryDSL API
├─ frontend/              # 프론트엔드 앱
├─ qa/                    # Playwright 기반 QA 테스트
├─ docker-compose.yml     # API + MySQL 통합 로컬 실행
├─ .env.example           # 로컬 환경변수 예시
└─ README.md
```

## 가장 간단한 실행 방법

### 사전 준비

- Git
- Docker (아래 중 하나)
  - Docker Desktop — 윈도우/맥에서 가장 간단합니다
  - WSL2 안의 Docker Engine — 윈도우에서 더 가볍습니다
  - 리눅스 Docker Engine

Java, Gradle, MySQL은 별도로 설치하지 않아도 됩니다.
`docker compose` 명령만 동작하면 어느 쪽이든 상관없습니다.

### Windows PowerShell

```powershell
git clone <저장소 주소>
cd blind-matching-app
Copy-Item .env.example .env
docker compose up -d --build
```

`.env` 복사는 선택입니다. 기본 개발값이 Compose에 지정되어 있어 생략해도 실행됩니다.

### 상태 및 로그 확인

```powershell
docker compose ps
docker compose logs -f app
```

MySQL과 API가 정상 실행되면 Swagger에 접속합니다.

```text
http://localhost:8080/swagger-ui.html
```

### 종료

```powershell
docker compose down
```

DB까지 초기화하려면 다음을 실행합니다.

```powershell
docker compose down -v
docker compose up -d --build
```

## 백엔드만 로컬 JVM으로 실행

JDK 21이 설치된 환경에서는 다음 방식도 사용할 수 있습니다.

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

이 방식은 로컬 3306 포트에 MySQL이 실행 중이어야 합니다.

## QA 실행

```powershell
cd qa
npm install
npm test
```

## 브랜치 전략

- `main`: 운영 배포
- `develop`: 테스트 환경 배포
- `feature/BMA-XX-설명`: 기능 개발
- `fix/BMA-XX-설명`: 버그 수정
- 모든 반영은 PR을 통해 진행
- 커밋 메시지와 브랜치명에 Jira 이슈 키 포함

## PR 규칙

- GitHub Actions의 `build-and-test`, `validate-docker-compose` 통과 필요
- PR 크기 400줄 이하 권장
- 문제 발생 시 `git revert`로 대응
