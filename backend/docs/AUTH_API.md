# 인증 API 계약 (S1 로그인·회원가입)

프론트(BMA-34)가 MSW 목을 맞출 때 이 문서를 정본으로 삼는다.

> **경로 주의**
> 티켓 초안에는 `/auth/signup` 처럼 적혀 있으나 **실제 경로는 `/api/v1/auth/signup`** 이다.
> 저장소의 컨트롤러 10개가 모두 `/api/v1` 을 쓰고 있어 이쪽을 정본으로 고정했다.
> `server.servlet.context-path` 는 설정하지 않으므로 위 경로가 그대로 최종 URL 이다.

---

## 1. 공통 규약

### 기본 경로

```
/api/v1/<도메인>/<리소스>
```

### 응답 봉투

성공/실패 모두 같은 형태로 감싼다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "성공",
  "data": { },
  "timestamp": "2026-08-10T16:40:00"
}
```

| 필드 | 설명 |
| --- | --- |
| `success` | 성공 여부 |
| `code` | 성공은 `SUCCESS`, 실패는 `<도메인>_<3자리>` (예: `AUTH_009`) |
| `message` | 사용자에게 그대로 노출 가능한 메시지 |
| `data` | 성공 시 본문. **실패 시에도 상세가 있으면 채워진다** |
| `timestamp` | 응답 생성 시각 |

### null 필드 처리

전역으로 `default-property-inclusion: non_null` 이 켜져 있어 **값이 `null` 인 필드는 응답에서 빠진다.**
키가 없는 것과 값이 `null` 인 것을 구분해야 한다면 `undefined` 검사를 써야 한다.

- 본문이 없는 성공 응답과 상세가 없는 실패 응답에는 **`data` 키가 아예 없다.**
- 단, 아래 "정지 계정 상세"처럼 명세가 필드 존재를 요구하는 DTO 는 예외 처리되어
  값이 `null` 이어도 키가 유지된다.

### 인증 헤더

```
Authorization: Bearer <accessToken>
```

인증 없이 호출 가능한 경로: `/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/refresh`
**`/api/v1/auth/logout` 은 인증이 필요하다.** 리프레시 토큰 문자열만 알면 타인의 세션을
끊을 수 있었기 때문에 인증 경로로 옮겼다.

---

## 2. 회원가입

```
POST /api/v1/auth/signup      (인증 불필요)
```

### 요청

```json
{
  "email": "user@example.com",
  "password": "abcd1234",
  "phoneNumber": "010-1234-5678"
}
```

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `email` | O | 이메일 형식, 255자 이하. 서버가 소문자로 정규화한다 |
| `password` | O | 8~64자, **영문과 숫자를 모두 포함** |
| `phoneNumber` | X | `0`으로 시작, 하이픈 선택 |

### 성공 (200)

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": { "userId": 1, "status": "ACTIVE" }
}
```

가입 직후 상태는 `ACTIVE` 이므로 바로 로그인할 수 있다.

### 실패

| 상황 | HTTP | `code` | `data` |
| --- | --- | --- | --- |
| 이메일 중복 | 409 | `AUTH_003` | 아래 참고 |
| 휴대전화 중복 | 409 | `AUTH_006` | 없음 |
| 검증 실패 | 400 | `COMMON_001` | 없음 (`message` 에 필드별 사유) |

**이메일 중복 상세** — 소셜로 가입된 이메일인지 구분해 안내하기 위한 것이다.
계정 자동 통합이나 별도 계정 생성은 하지 않고 **차단 + 안내**로만 처리한다.

```json
{
  "success": false,
  "code": "AUTH_003",
  "message": "이미 사용 중인 이메일입니다.",
  "data": {
    "errorCode": "EMAIL_DUPLICATE",
    "provider": "KAKAO"
  }
}
```

`provider` 값: `LOCAL` / `KAKAO` / `NAVER` / `GOOGLE` / `APPLE`
→ `LOCAL` 이면 "이미 가입된 이메일", 그 외면 "카카오로 가입된 이메일이에요" 식으로 안내한다.

---

## 3. 로그인

```
POST /api/v1/auth/login       (인증 불필요)
```

### 요청

```json
{ "email": "user@example.com", "password": "abcd1234" }
```

### 성공 (200)

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 1800,
    "userId": 1,
    "profileCompleted": false
  }
}
```

**`profileCompleted`** 로 라우팅을 분기한다.
`false` → 신규 유저, S2(온보딩) / `true` → 기존 유저, 메인 허브

### 검증 순서 (고정)

① 이메일+비밀번호 자격 증명 → ② 통과하면 계정 상태(정지 여부)

순서를 반대로 하면 비밀번호를 몰라도 계정 정지 여부를 알아낼 수 있다. 이 순서는 바뀌지 않는다.
그래서 **비밀번호가 틀리면 정지 계정이라도 `AUTH_001` 이 나간다.**

### 실패

| 상황 | HTTP | `code` | `data` |
| --- | --- | --- | --- |
| 이메일 없음 / 비밀번호 불일치 | 401 | `AUTH_001` | 없음 |
| **정지 계정** | 403 | `AUTH_009` | 아래 참고 |
| 탈퇴·가입대기 등 그 외 비활성 | 403 | `AUTH_007` | 없음 |

가입 여부가 노출되지 않도록 "이메일 없음"과 "비밀번호 불일치"는 같은 응답을 쓴다.

### 정지 계정 상세 (S1-18~21 이용정지 화면)

```json
{
  "success": false,
  "code": "AUTH_009",
  "message": "이용이 정지된 계정입니다.",
  "data": {
    "errorCode": "ACCOUNT_SUSPENDED",
    "restrictionType": "TEMPORARY",
    "reason": "욕설 사용",
    "restrictedUntil": "2026-09-01T00:00:00Z"
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `errorCode` | 항상 `ACCOUNT_SUSPENDED` |
| `restrictionType` | `TEMPORARY` 또는 `PERMANENT` |
| `reason` | 정지 사유. 사용자에게 그대로 노출 가능 |
| `restrictedUntil` | 해제 일시. **UTC ISO-8601**. 영구 정지면 `null` |

**`restrictedUntil` 은 영구 정지일 때도 필드가 사라지지 않는다.** 값만 `null` 이다.
전역 `non_null` 설정을 이 DTO 에서만 덮어썼고, 회귀 테스트로 고정해 두었다
(`SuspendedAccountDetailTest`).

```json
{
  "data": {
    "errorCode": "ACCOUNT_SUSPENDED",
    "restrictionType": "PERMANENT",
    "reason": "반복적인 부적절한 콘텐츠 게시",
    "restrictedUntil": null
  }
}
```

시간대는 UTC 고정이다. **KST 변환은 프론트에서 한다.**

정지 판정 규칙:
- `SF_USER_SANCTION` 의 활성 제재가 정본이다. 로그인을 막는 종류는 `SUSPEND` 와 `BAN` 뿐이며
  `WARNING` / `CHAT_LIMIT` / `MATCH_LIMIT` 는 로그인을 막지 않는다.
- `BAN` 이거나 **종료 일시가 없는 `SUSPEND`** 는 `PERMANENT` 로 본다.
- 제재가 여러 건이면 영구가 우선, 같은 종류면 해제가 가장 늦은 것을 쓴다.

---

## 4. 토큰 재발급

```
POST /api/v1/auth/refresh     (인증 불필요)
```

### 요청

```json
{ "refreshToken": "eyJhbGciOi..." }
```

### 성공 (200)

로그인과 동일한 `TokenResponse`. **리프레시 토큰도 새 값으로 교체된다(로테이션).**
클라이언트는 응답의 `refreshToken` 으로 반드시 갱신해 저장해야 한다.

### 실패

| 상황 | HTTP | `code` |
| --- | --- | --- |
| 서명·형식 오류 | 401 | `AUTH_002` |
| **이미 사용된 토큰 재제출** | 401 | `AUTH_008` |
| 비활성 계정 | 403 | `AUTH_007` |
| 사용자 없음 | 404 | `USER_001` |

`AUTH_008` 은 탈취 의심으로 간주해 **해당 사용자의 모든 리프레시 토큰을 폐기**한다.
이 응답을 받으면 재로그인시켜야 한다.

---

## 5. 로그아웃

```
POST /api/v1/auth/logout      (인증 필요)
```

### 요청

```
Authorization: Bearer <accessToken>
```
```json
{ "refreshToken": "eyJhbGciOi..." }
```

### 성공 (200)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "성공",
  "timestamp": "2026-08-10T16:40:00"
}
```

**`data` 키 자체가 없다.** 전역 `non_null` 설정 때문에 본문이 없는 응답에서는
`data` 가 통째로 빠진다. `data === null` 이 아니라 `data === undefined` 로 들어온다.

본인 소유가 아닌 토큰은 회수하지 않으며, 그 경우에도 성공을 반환한다(존재 여부 비노출).

---

## 6. 소셜 로그인 (카카오 / 네이버 / 구글)

**백엔드 콜백 방식**이다. 인가 화면 이동과 3사 콜백 수신을 모두 서버가 처리하므로
클라이언트 시크릿이 브라우저에 노출되지 않는다. 프론트가 할 일은 버튼에서 링크로 보내는 것뿐이다.

```
① 프론트  →  GET  /api/v1/auth/social/{provider}/authorize     (버튼에서 이동)
②          →  302  3사 인가 화면
③ 3사     →  GET  /api/v1/auth/social/{provider}/callback      (서버가 받는다)
④          →  302  {프론트 주소}?ticket=...   또는   ?error=...
⑤ 프론트  →  POST /api/v1/auth/social/exchange { "ticket": "..." }  → TokenResponse
```

`provider`: `kakao` / `naver` / `google` (대소문자 무시)

### 왜 티켓을 한 번 더 거치나

④에서 JWT 를 그대로 쿼리에 실으면 브라우저 히스토리와 리퍼러 헤더에 토큰이 남는다.
그래서 **2분짜리 일회용 티켓**만 넘기고, 실제 토큰은 ⑤의 POST 로 건넨다.
티켓은 교환 즉시 소진되며 재사용하면 `AUTH_014` 가 난다.

### ① 인가 화면으로 이동

```
GET /api/v1/auth/social/kakao/authorize      (인증 불필요)
```

302 로 3사 인가 화면에 보낸다. 동시에 CSRF 방지용 `state` 를 HttpOnly 쿠키
(`bma_oauth_state`, 5분, SameSite=Lax)에 심는다. 콜백에서 이 값을 대조한다.

**프론트는 fetch 가 아니라 페이지 이동으로 호출해야 한다.** XHR 로 부르면 302 를 따라가며
쿠키가 제대로 심기지 않는다.

```html
<a href="/api/v1/auth/social/kakao/authorize">카카오로 시작하기</a>
```

### ③④ 콜백 — 프론트가 직접 호출하지 않는다

3사가 호출한다. 서버가 토큰 교환 → 사용자 정보 조회 → 가입/로그인까지 마친 뒤
프론트 주소로 302 한다.

| 결과 | 이동 주소 |
| --- | --- |
| 성공 | `{successRedirect}?ticket=<일회용 티켓>` |
| 실패 | `{failureRedirect}?error=<오류 코드>` |

기본값은 둘 다 `http://localhost:5173/oauth/result` 다(`OAUTH_SUCCESS_REDIRECT`,
`OAUTH_FAILURE_REDIRECT` 로 바꾼다). 프론트는 이 경로에 결과 처리 화면을 두고
`ticket` 이 있으면 ⑤로, `error` 가 있으면 안내를 띄우면 된다.

**`error` 로 올 수 있는 값**

| 코드 | 의미 |
| --- | --- |
| `SOCIAL_AUTH_CANCELED` | 사용자가 동의를 취소함 |
| `AUTH_010` | 콘솔 등록이 안 된 제공자 |
| `AUTH_011` | state 불일치. 다시 시도해야 한다 |
| `AUTH_012` | 토큰 교환 또는 사용자 정보 조회 실패 |
| `AUTH_013` | **제공자가 이메일을 주지 않음** (아래 참고) |
| `AUTH_003` | 같은 이메일이 다른 수단으로 이미 가입됨 |
| `AUTH_009` / `AUTH_007` | 정지 계정 / 이용 불가 계정 |

> 콜백은 브라우저 이동이라 JSON 오류를 돌려줘도 사용자가 볼 수 없다. 그래서 모든 실패를
> 302 + `?error=` 로 알린다. 상세 사유가 필요하면 ⑤에서 다시 확인해야 한다.

### ⑤ 티켓 교환

```
POST /api/v1/auth/social/exchange     (인증 불필요)
```

```json
{ "ticket": "..." }
```

성공하면 **이메일 로그인과 완전히 같은 `TokenResponse`** 를 돌려준다.
`profileCompleted` 도 그대로 들어 있어 라우팅 분기가 동일하다.

| 상황 | HTTP | `code` |
| --- | --- | --- |
| 티켓 없음/만료/이미 사용 | 401 | `AUTH_014` |
| 사용자 없음 | 404 | `USER_001` |
| 정지 계정 | 403 | `AUTH_009` (정지 상세 포함) |

### 계정 연결 규칙

- `LOGIN_PROVIDER` + `PROVIDER_USER_KEY` 가 일치하면 기존 계정으로 로그인한다.
- 일치하는 계정이 없고 **이메일이 이미 쓰이고 있으면 차단**하고 가입 수단을 안내한다
  (`AUTH_003` + `data.provider`). 계정 자동 통합이나 별도 계정 생성은 하지 않는다.
- 소셜 가입 계정은 `PASSWORD_HASH` 가 없어 이메일 로그인이 불가능하다.
  제공자가 인증을 마친 이메일이므로 `EMAIL_VERIFIED_YN` 은 `Y` 로 넣는다.

### ⚠ 카카오 이메일 — 미해결

`US_USER.EMAIL` 이 NOT NULL 이라 **이메일 없이는 가입할 수 없다.** 그런데 카카오는
이메일을 필수 동의로 받으려면 **비즈 앱 전환과 검수**가 필요하다. 개인 개발자 앱이거나
사용자가 동의하지 않으면 이메일이 오지 않는다.

현재 구현은 이 경우 `AUTH_013` 으로 **실패시킨다.** 가짜 이메일을 만들어 넣지 않는다.
어느 쪽으로 갈지는 아직 미정이다.

| 안 | 내용 |
| --- | --- |
| A | 비즈 앱 전환 + 검수로 이메일 필수 동의 확보 |
| B | 소셜 가입 후 이메일 입력 단계 추가 (S1 화면 추가) |
| C | 임시 이메일 생성 (`kakao_{id}@...`) — 나중에 부채가 된다 |
| D | `EMAIL` 을 nullable 로 변경 — 로그인·중복검사 전반 재검토 필요 |

네이버와 구글은 이메일이 무난히 나오므로 **카카오만 이 이슈가 있다.**

### 콘솔 등록

3사 개발자 콘솔에 등록할 Redirect URI 는 다음과 같다.

```
http://localhost:8080/api/v1/auth/social/kakao/callback
http://localhost:8080/api/v1/auth/social/naver/callback
http://localhost:8080/api/v1/auth/social/google/callback
```

발급받은 값은 `.env` 에 넣는다(`.env.example` 참고). **자격 증명이 비어 있는 제공자는
비활성으로 처리되어 요청 시 `AUTH_010` 을 반환한다.** 기동은 막지 않으므로
콘솔 등록이 끝난 것부터 하나씩 채우면 된다.

---

## 7. 결정 사항

### 오류 분기는 `data.errorCode` 를 본다 (확정)

정지 계정 응답에는 코드가 두 개 나간다.

```json
{
  "code": "AUTH_009",
  "data": { "errorCode": "ACCOUNT_SUSPENDED", ... }
}
```

- 봉투의 `code` 는 저장소 전체가 쓰는 기존 규약(`<도메인>_<3자리>`)을 그대로 유지한다.
- **프론트가 화면을 분기할 때는 `data.errorCode` 를 본다.** 정합성 검토 v1.6 이 이 이름으로
  요구했으므로 이쪽을 계약으로 삼는다.

`data.errorCode` 가 있는 오류는 현재 둘이다.

| `data.errorCode` | 봉투 `code` | 상황 |
| --- | --- | --- |
| `ACCOUNT_SUSPENDED` | `AUTH_009` | 정지 계정 로그인 |
| `EMAIL_DUPLICATE` | `AUTH_003` | 이메일 중복 가입 시도 |

나머지 오류에는 `data` 가 없다. 전역 `non_null` 때문에 **키 자체가 존재하지 않으므로**
`data?.errorCode` 처럼 안전 접근을 쓰고, 없으면 봉투의 `code` 로 처리하면 된다.

---

## 8. 확정 필요

- **카카오 이메일 대응** (6절 참고). 비즈 앱 전환 여부에 따라 갈린다.
- 소셜 로그인 콘솔 등록 및 키 발급.
- `refresh` 에서 정지 계정을 만났을 때도 정지 상세를 줄지.
  현재는 `AUTH_007` 만 반환한다. 액세스 토큰 만료 후 재발급 시점에 정지가 걸렸다면
  프론트가 이용정지 화면을 못 띄우므로, 로그인과 같게 맞추는 편이 일관적이다.
