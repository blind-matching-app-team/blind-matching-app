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

## 6. 소셜 로그인 — 미구현

```
POST /api/v1/auth/social/{provider}
```

`provider`: `kakao` / `naver` / `google`

**아직 구현되지 않았다.** 3사 개발자 콘솔 앱 등록과 Redirect URI 설정, 클라이언트 ID·시크릿
확보가 선행되어야 한다. 스키마는 준비돼 있다
(`US_USER.LOGIN_PROVIDER`, `PROVIDER_USER_KEY`, `UK_US_USER_PROVIDER` 유니크 제약).

요청/응답 형태는 콘솔 연동 방식(Authorization Code vs Access Token 전달)이 정해진 뒤 확정한다.

---

## 7. 확정 필요

- **`code` vs `data.errorCode`** — 봉투의 `code` 는 기존 규약(`AUTH_009`)을 따르고,
  정합성 검토 v1.6 이 요구한 이름(`ACCOUNT_SUSPENDED`)은 `data.errorCode` 로 함께 내려준다.
  프론트가 어느 쪽으로 분기할지 합의가 필요하다.
- 소셜 로그인 요청/응답 형태
- `refresh` 에서 정지 계정을 만났을 때도 정지 상세를 줄지 (현재는 `AUTH_007` 만 반환)
