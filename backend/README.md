# Blind Matching App Backend v0.2

Spring Boot + Gradle + JPA + QueryDSL 기반 블라인드 소개팅 서비스 백엔드입니다.

PM 저장소의 모노레포 구조를 사용하며, 백엔드 소스는 `backend/` 디렉터리에 위치합니다.

---

## 기술 구성

- Java 21
- Spring Boot 3.5.16
- Gradle Groovy DSL
- MySQL 8.4
- Spring Data JPA / Hibernate
- QueryDSL 5.1
- Spring Security
- JWT HS256
- WebSocket + STOMP
- Swagger UI / OpenAPI
- Docker Compose
- GitHub Actions

### 인증 구성

- Access Token 유효 기간: 30분
- Refresh Token 유효 기간: 14일
- Refresh Token은 SHA-256 해시 형태로 DB에 저장
- Refresh Token 로테이션 적용
- 폐기된 Refresh Token 재사용 시 토큰 탈취로 판단
- WebSocket CONNECT 단계에서 JWT 인증
- 채팅방 구독 및 메시지 발송 시 참여자 권한 검증

---

## 프로젝트 구조

```text
blind-matching-app/
├── backend/
│   ├── docs/
│   ├── gradle/
│   ├── src/
│   ├── .dockerignore
│   ├── build.gradle
│   ├── Dockerfile
│   ├── gradlew
│   ├── gradlew.bat
│   └── settings.gradle
├── frontend/
├── qa/
├── .github/
│   └── workflows/
│       └── ci.yml
├── .env.example
├── docker-compose.yml
└── README.md
```

---

## 빠른 시작

### 사전 준비

Windows 로컬 실행에 필요한 프로그램은 다음 두 가지입니다.

- Git
- Docker Desktop

다음 프로그램은 별도로 설치하지 않아도 됩니다.

- Java
- Gradle
- MySQL
- MariaDB
- Redis

Spring Boot 애플리케이션과 MySQL은 모두 Docker 컨테이너에서 실행됩니다.  
Gradle Wrapper 또한 Docker 이미지 빌드 과정에서 사용됩니다.

Docker Desktop을 실행한 뒤 화면 왼쪽 아래에 `Engine running`이 표시되는지 확인하세요.

### Windows PowerShell 또는 Git Bash

```powershell
git clone git@github.com:blind-matching-app-team/blind-matching-app.git
cd blind-matching-app

docker compose up -d --build
```

첫 실행 시 다음 작업이 수행되므로 시간이 다소 걸릴 수 있습니다.

- MySQL 8.4 이미지 다운로드
- Java 21 이미지 다운로드
- Gradle 의존성 다운로드
- Spring Boot 애플리케이션 빌드
- 데이터베이스 및 테이블 초기화
- 애플리케이션 컨테이너 실행

### 실행 상태 확인

```powershell
docker compose ps
```

정상 실행 시 다음과 유사하게 표시됩니다.

```text
NAME        SERVICE   STATUS
bma-mysql   mysql     Up (healthy)
bma-app     app       Up
```

### 애플리케이션 로그 확인

```powershell
docker compose logs -f app
```

### MySQL 로그 확인

```powershell
docker compose logs -f mysql
```

### 접속 주소

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- Swagger 리다이렉트 주소: <http://localhost:8080/swagger-ui.html>
- Health Check: <http://localhost:8080/actuator/health>

Health Check 정상 응답:

```json
{
  "status": "UP"
}
```

---

## Docker Compose 명령

| 목적 | 명령 |
|---|---|
| 최초 실행 및 이미지 재빌드 | `docker compose up -d --build` |
| 컨테이너 상태 확인 | `docker compose ps` |
| 종료된 컨테이너까지 확인 | `docker compose ps -a` |
| 애플리케이션 로그 확인 | `docker compose logs -f app` |
| MySQL 로그 확인 | `docker compose logs -f mysql` |
| 전체 로그 확인 | `docker compose logs -f` |
| 컨테이너 중지 및 삭제 | `docker compose down` |
| DB 볼륨 포함 전체 초기화 | `docker compose down -v` |
| MySQL 접속 | `docker compose exec mysql mysql -ubma -pbma1234 bma` |
| Redis 포함 실행 | `docker compose --profile cache up -d --build` |
| 애플리케이션만 재빌드 | `docker compose build app` |
| 애플리케이션만 재시작 | `docker compose up -d app` |

### Docker 서비스 구성

| 서비스 | 이미지 또는 빌드 | 호스트 포트 | 설명 |
|---|---|---:|---|
| `app` | `backend/Dockerfile` | 8080 | Java 21 JRE에서 Spring Boot 실행 |
| `mysql` | `mysql:8.4` | 3306 | 애플리케이션 데이터베이스 |
| `redis` | `redis:7.4-alpine` | 6379 | 선택 서비스, 기본적으로 실행되지 않음 |

### MySQL 기본 설정

| 항목 | 기본값 |
|---|---|
| Database | `bma` |
| User | `bma` |
| Password | `bma1234` |
| Root Password | `root1234` |
| Character Set | `utf8mb4` |
| Collation | `utf8mb4_0900_ai_ci` |
| Time Zone | `Asia/Seoul` |

> 테이블 생성과 이후의 모든 스키마 변경은 애플리케이션 기동 시 **Flyway**가 담당합니다.
> 마이그레이션 파일은 `backend/src/main/resources/db/migration/` 에 있습니다.
> 작성 규칙과 운영 절차는 [DB 마이그레이션 가이드](docs/DB_MIGRATION.md)를 참고하세요.
>
> DB 콘솔에서 직접 `ALTER TABLE` 하거나 `ddl-auto`를 `update`로 되돌리면
> 마이그레이션 이력과 실제 스키마가 어긋납니다. 스키마 변경은 새 마이그레이션 파일로만 합니다.

예전 `docker-entrypoint-initdb.d` 방식으로 만들어진 볼륨이 남아 있으면 Flyway가 기동을 거부합니다.
이 경우 기존 볼륨을 삭제하고 처음부터 다시 만들어야 합니다.

```powershell
docker compose down -v
docker compose up -d --build
```

`docker compose down -v`를 실행하면 로컬 개발 DB의 모든 데이터가 삭제됩니다.

---

## 환경 변수 설정

Compose 파일에는 로컬 개발용 기본값이 지정되어 있어 `.env` 파일 없이도 실행할 수 있습니다.

포트, 비밀번호, JWT Secret 등을 변경하려면 루트의 `.env.example`을 `.env`로 복사해 사용하세요.

### Windows PowerShell

```powershell
Copy-Item .env.example .env
```

### Git Bash

```bash
cp .env.example .env
```

`.env`는 개인별 설정과 비밀 값을 포함할 수 있으므로 Git에 커밋하지 않습니다.

```gitignore
.env
```

`.env.example`은 예시 파일이므로 Git에 포함합니다.

---

## 주요 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MYSQL_DATABASE` | `bma` | MySQL 데이터베이스명 |
| `MYSQL_USER` | `bma` | MySQL 애플리케이션 계정 |
| `MYSQL_PASSWORD` | `bma1234` | MySQL 애플리케이션 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | `root1234` | MySQL root 비밀번호 |
| `MYSQL_PORT` | `3306` | 호스트 MySQL 포트 |
| `APP_PORT` | `8080` | 호스트 Spring Boot 포트 |
| `REDIS_PORT` | `6379` | 호스트 Redis 포트 |
| `DB_URL` | 환경별 설정 | 데이터베이스 접속 URL |
| `DB_USERNAME` | `bma` | Spring Datasource 사용자 |
| `DB_PASSWORD` | `bma1234` | Spring Datasource 비밀번호 |
| `JWT_SECRET` | 개발용 기본값 | JWT 서명 키, 32바이트 이상 권장 |
| `JWT_ACCESS_SECONDS` | `1800` | Access Token 유효 기간 |
| `JWT_REFRESH_SECONDS` | `1209600` | Refresh Token 유효 기간 |
| `STORAGE_ROOT` | `./uploads` | 파일 저장 루트 |
| `STORAGE_MAX_FILE_SIZE` | `10485760` | 파일당 최대 크기, 기본 10MB |
| `STORAGE_MAX_IMAGES` | `6` | 사용자당 최대 이미지 수 |
| `CORS_ALLOWED_ORIGINS` | localhost:3000, localhost:5173 | REST API 허용 Origin |
| `WS_ALLOWED_ORIGINS` | localhost:3000, localhost:5173 | WebSocket 허용 Origin |
| `PAYMENT_GATEWAY` | `stub` | 결제 Gateway 구현체 |
| `PAYMENT_WEBHOOK_SECRET` | 빈 값 | 웹훅 서명 검증 Secret |

Docker 내부에서 애플리케이션은 `localhost`가 아니라 Compose 서비스명인 `mysql`을 사용합니다.

```text
jdbc:mysql://mysql:3306/bma
```

---

## Gradle Wrapper

백엔드 저장소에는 다음 Gradle Wrapper 파일이 포함되어 있습니다.

```text
backend/
├── gradlew
├── gradlew.bat
└── gradle/
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

따라서 Gradle을 별도로 설치할 필요가 없습니다.

Docker 이미지 빌드 시에도 저장소에 포함된 Gradle Wrapper를 사용하므로 로컬, 팀원 PC, CI의 Gradle 버전이 동일하게 유지됩니다.

---

## Docker 없이 직접 실행

Docker를 사용하지 않고 백엔드를 직접 실행하려면 다음 프로그램이 별도로 필요합니다.

- Java 21
- MySQL 8.x 또는 호환 DB

프로젝트 루트가 아니라 `backend/` 디렉터리에서 실행해야 합니다.

### Windows PowerShell

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

### macOS / Linux / Git Bash

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

Docker 밖에서 직접 실행할 때는 `application.yml` 또는 환경변수의 DB 주소가 `localhost`를 바라보도록 설정해야 합니다.

---

## CI — GitHub Actions

루트의 `.github/workflows/ci.yml`은 다음 시점에 자동 실행됩니다.

- `develop` 또는 `main` 대상 Pull Request 생성 및 업데이트
- `develop` 또는 `main` 브랜치에 Push
- GitHub Actions 화면에서 수동 실행

### CI Job

| Job 이름 | 수행 내용 |
|---|---|
| `build-and-test` | JDK 21 설치, Gradle Wrapper 빌드, 단위 테스트 실행, 테스트 리포트 업로드 |
| `validate-docker-compose` | Compose 문법 검증, Dockerfile 및 초기화 SQL 경로 확인 |

CI에서는 백엔드 디렉터리를 기준으로 다음 명령이 실행됩니다.

```bash
cd backend
./gradlew --no-daemon clean build
```

빌드나 테스트가 실패하면 PR 화면에 실패 상태가 표시됩니다.

테스트 리포트는 GitHub Actions 실행 화면의 `test-results` 아티팩트에서 확인할 수 있습니다.

현재 테스트는 주로 단위 테스트를 기준으로 구성되어 있습니다.  
추후 실제 MySQL 연결이 필요한 `@SpringBootTest` 통합 테스트를 추가하면 CI에 MySQL Service Container를 추가해야 합니다.

---

## 브랜치 룰셋 설정

워크플로우가 저장소에 반영된 뒤 GitHub에서 다음 설정이 필요합니다.

1. `Settings`
2. `Rules`
3. `Rulesets`
4. `develop` 또는 `main` 룰셋 선택
5. `Require status checks to pass` 활성화
6. 다음 상태 체크 추가
   - `build-and-test`
   - `validate-docker-compose`

체크 이름은 `ci.yml`의 각 Job `name` 값과 동일합니다.

워크플로우가 최소 한 번 실행된 이후에 상태 체크 이름이 GitHub 룰셋 검색 목록에 나타날 수 있습니다.

---

## 개발 브랜치 작업 흐름

작업은 반드시 `develop` 브랜치에서 분기합니다.

```bash
git checkout develop
git pull origin develop
git checkout -b feature/BMA-XX-작업내용
```

예시:

```bash
git checkout -b feature/BMA-13-backend-initial-setup
```

커밋 메시지에는 Jira 이슈 번호를 반드시 포함합니다.

```bash
git add .
git commit -m "BMA-13 백엔드 초기 프로젝트 구조 및 Docker 실행 환경 구성"
git push -u origin feature/BMA-13-backend-initial-setup
```

GitHub에서 다음 기준으로 Pull Request를 생성합니다.

```text
compare: feature/BMA-XX-작업내용
base: develop
```

`main`이 아니라 반드시 `develop`을 대상으로 생성합니다.

---

## 애플리케이션 아키텍처

```text
com.bma
├── common/
│   ├── config
│   ├── entity
│   ├── exception
│   ├── response
│   └── security
├── auth/
├── user/
├── storage/
├── onboarding/
├── matching/
├── chat/
├── reveal/
├── safety/
├── notification/
└── payment/
```

### 도메인별 역할

| 도메인 | 역할 |
|---|---|
| `common` | 공통 설정, 예외 처리, 응답 형식, JWT 및 보안 |
| `auth` | 회원가입, 로그인, 토큰 발급 및 로테이션 |
| `user` | 사용자 프로필, 선호 조건, 이미지 |
| `storage` | 파일 저장 추상화 및 로컬 저장 구현 |
| `onboarding` | 온보딩 질문과 사용자 답변 |
| `matching` | 추천, 좋아요, 패스, 상호 매칭, 대기열 |
| `chat` | 채팅방, 메시지, WebSocket |
| `reveal` | 단계별 프로필 공개 정책 및 동의 |
| `safety` | 사용자 차단 및 신고 |
| `notification` | 인앱 알림 |
| `payment` | 상품, 결제 승인, 웹훅 |

기본 계층 구조는 다음과 같습니다.

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Entity
```

Entity는 API 응답으로 직접 노출하지 않고 각 도메인의 DTO를 통해 변환합니다.

---

## 보안 설계 요약

### JWT 인증

- `JwtAuthenticationFilter`에서 Access Token 검증
- JWT의 `typ` 클레임으로 Access Token과 Refresh Token 구분
- 위조된 토큰과 만료된 토큰의 오류 응답 구분
- Refresh Token은 원문이 아닌 SHA-256 해시로 DB 저장
- Refresh Token 재발급 시 기존 토큰 폐기 및 신규 토큰 발급
- 이미 폐기된 Refresh Token 재사용 시 탈취 가능성으로 판단

### WebSocket

- STOMP CONNECT 단계에서 JWT 검증
- SUBSCRIBE 및 SEND 요청마다 채팅방 참여자 여부 확인
- 발신자 ID는 클라이언트 페이로드가 아니라 인증 Principal에서 결정

### 블라인드 프로필

상대방 프로필은 `ProfileMaskingService`를 거쳐 공개 단계에 따라 마스킹됩니다.

| 단계 | 공개 정보 |
|---|---|
| 0 | 나이대, 지역 시·도, MBTI, 자기소개, 실루엣 이미지 |
| 1 | 실제 나이, 키, 직업, 블러 이미지 |
| 2 | 닉네임, 원본 프로필 이미지 |

### 파일 업로드

- 서버에서 Object Key 생성
- 사용자 업로드 파일명을 저장 경로로 직접 사용하지 않음
- 허용 MIME Type 화이트리스트 검사
- 파일 시그니처 검사
- 저장 경로가 지정한 Root 하위인지 재검증
- 사용자별 최대 이미지 수 제한

### 결제

- 결제 금액은 클라이언트 요청값이 아닌 서버 상품 가격으로 결정
- PG 승인 금액과 서버 주문 금액 대조
- `orderId` 기준 멱등 처리
- 웹훅 HMAC 서명 검증 후 상태 반영

---

## 공개 단계 정책

공개 단계는 `RV_REVEAL_POLICY` 테이블의 정책값을 기준으로 동작합니다.

| 단계 | 이름 | 필요 메시지 | 필요 대화 시간 | 상호 동의 |
|---:|---|---:|---:|---|
| 0 | 미공개 | 0건 | 0분 | 불필요 |
| 1 | 실루엣 및 부분 공개 | 20건 | 10분 | 불필요 |
| 2 | 전체 공개 | 50건 | 30분 | 필요 |

정책 수치는 추후 기획 결정에 따라 변경할 수 있으며, 애플리케이션 코드에 직접 하드코딩하지 않습니다.

---

## 테스트

### Docker 밖에서 테스트

Java 21이 설치된 환경에서 실행합니다.

```powershell
cd backend
.\gradlew.bat test
```

Git Bash, macOS 또는 Linux:

```bash
cd backend
./gradlew test
```

### 전체 빌드

```powershell
cd backend
.\gradlew.bat clean build
```

현재 테스트 범위에는 다음 항목이 포함됩니다.

- JWT 발급 및 검증
- Refresh Token 검증
- 파일 업로드 경로 탈출 차단
- 매칭 참여자 정렬 규칙
- 단계별 프로필 마스킹

---

## 문제 해결

### `docker: command not found`

Docker Desktop이 설치되지 않았거나 Docker CLI 경로가 PATH에 등록되지 않은 상태입니다.

Windows 사용자 PATH에 다음 경로가 포함되어 있는지 확인하세요.

```text
C:\Users\<사용자명>\AppData\Local\Programs\DockerDesktop\resources\bin
```

새 터미널을 열고 확인합니다.

```powershell
docker --version
docker compose version
```

### MySQL이 `unhealthy` 상태

```powershell
docker compose logs --tail=200 mysql
```

초기화 SQL을 수정했다면 기존 볼륨을 삭제한 뒤 다시 실행합니다.

```powershell
docker compose down -v
docker compose up -d --build
```

### 8080 포트 충돌

```powershell
netstat -ano | findstr :8080
```

기존 프로세스를 종료하거나 `.env`에서 포트를 변경합니다.

```env
APP_PORT=8081
```

변경 후 접속 주소:

```text
http://localhost:8081/swagger-ui/index.html
```

### 애플리케이션 컨테이너가 보이지 않음

종료된 컨테이너까지 확인합니다.

```powershell
docker compose ps -a
docker compose logs --tail=200 app
```

---

## 남은 작업

- 이미지 블러 및 실루엣 생성 후처리 파이프라인
- S3 Storage 구현체
- 실제 PG 결제 Gateway 연동
- 소셜 로그인
- 이메일 인증
- 휴대전화 인증
- 로그인 시도 제한 및 Rate Limit
- 가치관 답변 기반 매칭 점수 알고리즘
- 대기열 기반 자동 매칭 배치
- Redis 기반 Refresh Token 블랙리스트 또는 캐시 적용
- 통합 테스트 및 Testcontainers 도입