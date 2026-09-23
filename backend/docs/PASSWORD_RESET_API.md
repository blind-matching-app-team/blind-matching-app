# 비밀번호 재설정 API (S13) — BMA-61

S13 비밀번호 찾기 사양서 v1.4 의 백엔드. 로그인 전 흐름이라 **세 엔드포인트 모두 인증이 없다**(토큰 자체가 자격 증명).
공통 응답 형식은 `ApiResponse{success, code, message, data, timestamp}` 이며 아래는 `data` 만 적는다.

| S13 오브젝트 | 엔드포인트 |
|---|---|
| S13-04/05 이메일 입력 → 재설정 링크 보내기 | `POST /api/v1/auth/password-reset/request` |
| S13-06 링크로 2단계 진입(토큰 검증) | `GET /api/v1/auth/password-reset/validate?token=` |
| S13-07~09 새 비밀번호 설정 → S1 이동 | `POST /api/v1/auth/password-reset/confirm` |

---

## 1. 재설정 링크 요청

```
POST /api/v1/auth/password-reset/request
{ "email": "name@example.com" }
```

```json
{ "sent": true, "expiresInMinutes": 30, "debugResetLink": null }
```

- **미가입·탈퇴·소셜 전용(비밀번호 없음) 계정이어도 응답은 같다.** 이메일 존재 여부를 노출하지 않는다(S13-04). 프론트는 무조건 "이메일을 보냈어요" 토스트.
- 실제 발송 대상이면 32바이트 난수 토큰을 만들어 **SHA-256 해시만** `US_USER_TOKEN`(종류 `PASSWORD_RESET`, 만료 30분)에 저장하고, 메일에 `{link-base-url}?token=…` 링크를 보낸다.
- 같은 사용자가 다시 요청하면 이전 미사용 토큰은 폐기된다. 항상 마지막 링크 하나만 유효하다.
- 이메일은 앞뒤 공백 제거·소문자로 정규화한다.
- `debugResetLink` 는 `PASSWORD_RESET_EXPOSE_DEBUG_LINK=true` 일 때만 채워진다. 메일 없이 검증하기 위한 로컬 전용 설정이며 **운영에서는 반드시 false**.

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | COMMON_001 | 이메일 형식 오류/누락 |

## 2. 토큰 확인

```
GET /api/v1/auth/password-reset/validate?token=…
```

```json
{ "valid": true, "expiresAt": "2026-09-23T20:05:00" }
```

링크로 2단계 화면에 들어올 때 폼을 그리기 전에 호출한다. 만료·무효·사용됨이면 `400 AUTH_017` → 공통 에러화면(링크 만료 문구, S13-09 결정 v1.4).

## 3. 새 비밀번호 설정

```
POST /api/v1/auth/password-reset/confirm
{ "token": "…", "newPassword": "NewPass123" }
```

```json
{ "changedAt": "2026-09-23T19:40:00", "sessionsEnded": 2 }
```

- 새 비밀번호 규칙은 회원가입과 같다: 영문+숫자 포함, 8~64자 (S13-07).
- 성공 시 토큰을 소진시키고(재사용 불가), **모든 기기의 리프레시 토큰을 폐기**한다(`sessionsEnded`). 탈취된 세션을 끊기 위한 조치이며, 프론트는 S1(로그인)으로 보낸다.
- 만료 전 액세스 토큰은 30분까지 유효하다(서버가 액세스 토큰 상태를 보지 않음, BMA-56 과 동일).

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | AUTH_017 | 토큰 없음/만료/이미 사용/이전 링크 |
| 400 | COMMON_001 | 비밀번호 규칙 위반 |

---

## 4. 메일 발송 설정

| 설정 | 값 |
|---|---|
| `MAIL_MODE` | `log`(기본): 발송하지 않고 링크를 서버 로그에 남긴다 / `smtp`: 실제 발송 |
| `MAIL_HOST` `MAIL_PORT` `MAIL_USERNAME` `MAIL_PASSWORD` | smtp 모드의 SMTP 접속 정보(STARTTLS). Gmail 이면 앱 비밀번호 |
| `MAIL_FROM` | 발신 주소 |
| `PASSWORD_RESET_LINK_BASE_URL` | 프론트 2단계 화면 주소(기본 `http://localhost:5173/reset-password`) |
| `PASSWORD_RESET_TOKEN_MINUTES` | 링크 유효 시간(기본 30, 사양서 확정값) |

smtp 모드에서 발송이 실패해도 요청 API 는 성공으로 응답한다(존재 여부 비노출 원칙). 실패는 서버 로그(`ERROR 비밀번호 재설정 메일 발송 실패`)로만 남으므로 운영에서는 이 로그에 알림을 건다.
메일 본문은 텍스트 1종("[BMA] 비밀번호 재설정 안내", 링크 + 유효 시간 + 본인 아님 안내)이다.

## 5. 결정·정정 사항

- 토큰 유효시간 30분(사양서 v1.4 확정), 발송 수단은 SMTP(스프링 메일)로 결정. SMTP 계정이 정해지기 전까지는 `log` 모드.
- 티켓의 2개 엔드포인트에 더해 `GET …/validate` 를 추가했다. 링크로 진입한 2단계 화면이 폼을 보여주기 전에 만료 여부를 알아야 하기 때문(S13-06 "토큰 검증 필요").
- 요청 횟수 제한(rate limit)은 넣지 않았다. 필요하면 IP/이메일 단위로 API 게이트웨이 또는 필터에서 건다.

## 6. 검증

- 단위: `PasswordResetServiceTest` (존재 여부 비노출, 소셜 전용 무시, 해시 저장·이전 토큰 폐기, 확정 시 세션 폐기, 만료/사용/위조 거부)
- 통합: `backend/scripts/verify-password-reset.sh` (16 단계, 만료 시나리오는 `DB_EXEC_CMD` 설정 시) / Postman `docs/postman/BMA-61-password-reset.postman_collection.json`
