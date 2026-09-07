# QA 테스트 (Playwright)

API 테스트와 E2E 테스트를 나눠 둔다. **Docker Desktop 은 필요 없다.**
검사 대상 서버만 떠 있으면 되고, 그 서버를 무엇으로 띄우든 상관없다.

```
tests/api/   백엔드 API 를 직접 호출한다. 브라우저가 필요 없다
tests/e2e/   브라우저로 화면을 조작한다. Chromium 이 필요하다
```

---

## 1. 준비물

| 항목 | 요구 |
| --- | --- |
| Node.js | **18 이상** (Playwright 1.62 요구사항) |
| 대상 서버 | API 테스트는 백엔드, E2E 는 프런트가 떠 있어야 한다 |

> **Node 가 18 미만이면** `npm ci` 부터 실패한다. 버전을 올리기 어렵다면
> 3절의 컨테이너 실행을 쓴다. 백엔드 인증 API 는 의존성 없이 도는
> `backend/scripts/smoke-auth.js` 로도 확인할 수 있다(Node 14 에서 동작).

```bash
cd qa
npm ci
```

---

## 2. 대상 서버 띄우기

### 백엔드 (API 테스트용)

```bash
docker compose up -d --build      # 저장소 루트에서
```

Docker Desktop, WSL2 안의 Docker Engine, 리눅스 Docker 어느 쪽이든 된다.
`http://localhost:8080/actuator/health` 가 `{"status":"UP"}` 이면 준비된 것이다.

### 프런트 (E2E 테스트용)

```bash
cd frontend
npm ci
npm run dev
```

프런트가 실제 API 를 쓰려면 **백엔드도 함께 떠 있어야 한다.**
백엔드 CORS 는 `http://localhost:3000` 과 `http://localhost:5173` 을 허용한다.

> 현재 프런트는 MSW 목으로 동작하며 실제 백엔드를 호출하지 않는다.
> 실제 연동은 Phase 3 통합 단계에서 붙인다.

---

## 3. 실행

### API 테스트

```bash
npm run test:api
```

브라우저를 띄우지 않으므로 `npx playwright install` 이 필요 없다.

### E2E 테스트

```bash
npx playwright install chromium    # 최초 1회
npm run test:e2e
```

### 전체 / 리포트

```bash
npm test          # api + e2e
npm run report    # 마지막 실행의 HTML 리포트 열기
```

### Node 18 을 설치하지 않고 돌리기

Playwright 공식 이미지에는 Node 와 브라우저가 모두 들어 있다.

```bash
# 저장소 루트에서. 리눅스 Docker Engine 기준
docker run --rm --network host -v "$(pwd)/qa:/qa" -w /qa \
  mcr.microsoft.com/playwright:v1.62.0-jammy \
  sh -c "npm ci && npx playwright test --project=api"
```

Docker Desktop(윈도우/맥)에서는 `--network host` 가 통하지 않으므로
호스트 주소를 직접 넘긴다.

```bash
docker run --rm -v "$(pwd)/qa:/qa" -w /qa \
  -e API_BASE_URL=http://host.docker.internal:8080 \
  mcr.microsoft.com/playwright:v1.62.0-jammy \
  sh -c "npm ci && npx playwright test --project=api"
```

---

## 4. 대상 주소 바꾸기

`playwright.config.ts` 가 환경변수를 읽는다.

| 변수 | 기본값 | 쓰는 곳 |
| --- | --- | --- |
| `API_BASE_URL` | `http://localhost:8080` | `api` 프로젝트 |
| `BASE_URL` | `http://localhost:3000` | `e2e` 프로젝트 |

```bash
API_BASE_URL=http://localhost:9090 npm run test:api
```

> **주의: `BASE_URL` 기본값이 3000 인데 Vite 개발 서버는 5173 에서 뜬다.**
> E2E 를 돌릴 때는 `BASE_URL=http://localhost:5173` 을 넘기거나
> 프런트를 3000 포트로 띄워야 한다. E2E 테스트가 실제로 추가될 때 정리한다.

---

## 5. 현재 상태

- `tests/api/health.spec.ts` — 백엔드 헬스체크 1건
- `tests/e2e/` — **아직 없다.** `--project=e2e` 는 테스트를 찾지 못한다

인증 API 와 온보딩 API 는 Playwright 대신 Postman 컬렉션으로 검증하고 있다.
같은 것을 두 곳에서 검증할 필요가 생기면 그때 정리한다.

- `backend/docs/postman/BMA-35-auth.postman_collection.json` (32건)
- `backend/docs/postman/BMA-38-onboarding.postman_collection.json` (30건)

## 6. CI

**현재 어떤 워크플로우에서도 QA 테스트가 실행되지 않는다.**
`.github/workflows/` 에는 백엔드 빌드와 프런트 빌드만 있다.

CI 에 붙이려면 대상 서버를 먼저 띄워야 한다. API 테스트는
`docker compose up -d` 후 헬스체크를 기다렸다가 `npm run test:api` 를
돌리면 되고, 그러면 Flyway 마이그레이션과 애플리케이션 기동까지
함께 검증된다.
