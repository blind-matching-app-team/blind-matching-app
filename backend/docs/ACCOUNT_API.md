# 계정 API (S8 마이페이지) — BMA-56

S8 마이페이지 사양서(v1.4)의 백엔드 연동 항목을 정리한다. 공통 응답 형식은 `ApiResponse{success, code, message, data, timestamp}` 이며,
아래 `data` 만 적는다. 모든 엔드포인트는 인증(Bearer 액세스 토큰)이 필요하다.

| S8 오브젝트 | 엔드포인트 | 상태 |
|---|---|---|
| S8-08 프로필 요약(닉네임·이메일) | `GET /api/v1/users/me` | 확장 (nickname, loginProvider, passwordSet 추가) |
| S8-14 로그아웃 | `POST /api/v1/auth/logout` | 기존(BMA-35) 유지, 재검증 |
| S8-15 회원탈퇴 | `DELETE /api/v1/users/me` | 신규 |
| S8-16 비밀번호 변경 | `PUT /api/v1/users/me/password` | 신규 |
| S8-08 "인증됨" 배지, S8-17 사진인증 | — | S16 본인인증(BMA-79) 이후 |
| S8-19 이용권 구매 | — | 구독(BMA-84) 이후 |
| S8-11/12/13 (준비 중 토스트) | — | 프론트 전용 |

---

## 1. 내 계정 (S8-08 프로필 요약)

```
GET /api/v1/users/me
```

```json
{
  "userId": 12,
  "email": "me@example.com",
  "nickname": "블라인드",
  "loginProvider": "LOCAL",
  "passwordSet": true,
  "userStatus": "ACTIVE",
  "userRole": "USER",
  "emailVerified": false,
  "phoneVerified": false,
  "lastLoginDate": "2026-09-18T10:00:00",
  "joinedDate": "2026-09-01T09:00:00"
}
```

| 필드 | S8 오브젝트 | 설명 |
|---|---|---|
| `nickname` | S8-08 | 프로필 닉네임. 프로필 미작성이면 `null` (키는 항상 있다) |
| `email` | S8-08 | 가입 이메일 |
| `loginProvider` | — | `LOCAL` / `KAKAO` / `NAVER` / `GOOGLE` |
| `passwordSet` | S8-16 노출 조건 | `false` 면 비밀번호가 없는 소셜 전용 계정 → 비밀번호 변경 메뉴를 숨긴다 |

---

## 2. 로그아웃 (S8-14)

```
POST /api/v1/auth/logout           (인증 필요)
{ "refreshToken": "<현재 기기의 리프레시 토큰>" }
```

- 200: 해당 리프레시 토큰을 폐기한다. 이후 그 토큰으로 `POST /auth/refresh` 는 401.
- 액세스 토큰은 서버 상태를 보지 않으므로 만료(30분)까지는 유효하다. 프론트는 로그아웃 시 두 토큰을 모두 지우고 S1 로 이동한다.
- 다른 기기의 세션은 그대로 남는다(기기별 로그아웃).

---

## 3. 회원 탈퇴 (S8-15)

```
DELETE /api/v1/users/me            (인증 필요, 본문 없음)
```

```json
{ "userId": 12, "status": "WITHDRAWN", "withdrawnAt": "2026-09-18T10:05:00", "matchesEnded": 1 }
```

한 트랜잭션에서 아래를 처리한다. 실패하면 아무것도 바뀌지 않는다.

| 대상 | 처리 |
|---|---|
| 진행 중 매칭 | `UNMATCHED` 로 종료(`END_REASON_CODE=WITHDRAWN`), 채팅방 `ENDED`. 상대에게는 S7-14 "매칭이 종료됐어요" 알림만 간다(탈퇴 사실·사유 비노출) |
| 매칭 대기열 | 취소 |
| 프로필 사진 | 저장소 파일 삭제 + 논리 삭제 |
| 프로필 | 익명화: 닉네임 → `탈퇴회원{id}`, 성별·MBTI·지역·직업·키·소개 `null`, `INCOMPLETE`/0점, 논리 삭제 |
| 선호조건 · 온보딩 답변 · 알림 | 논리 삭제 |
| 리프레시 토큰 · 소셜 티켓 | 전부 폐기 |
| 계정 | `WITHDRAWN` + 논리 삭제. 이메일 → `withdrawn-{id}@deleted.invalid`, 전화·비밀번호·소셜 키 `null` |

**남는 것**: 매칭·메시지·신고 행. 상대방의 채팅목록이 매칭 히스토리를 겸하므로(BMA-49) 방은 `ENDED` 로 남고, 상대 쪽 `partner` 는 `null` 로 내려간다.
프론트는 `partner == null` 을 "탈퇴한 사용자"로 표시한다. 메시지 본문은 상대가 이미 받은 것이라 지우지 않는다.

**탈퇴 이후**

- 같은 이메일로 다시 가입할 수 있고(새 `userId`), 옛 닉네임도 다시 쓸 수 있다. 이전 프로필·매칭은 이어지지 않는다.
- 소셜 키를 지우므로 같은 소셜 계정으로 로그인하면 새 계정이 만들어진다.
- 옛 이메일/비밀번호 로그인 → 401, 리프레시 → 401. 만료 전 액세스 토큰으로 호출하면 `404 USER_001`.
- 복구(탈퇴 철회) 기능은 없다. 사양서 S8-15 위험 모달이 "되돌릴 수 없다"고 안내하는 것과 일치한다.

| 상태 | 코드 | 상황 |
|---|---|---|
| 401 | AUTH_* | 토큰 없음/만료 |
| 404 | USER_001 | 이미 탈퇴한 계정 |

---

## 4. 비밀번호 변경 (S8-16)

```
PUT /api/v1/users/me/password      (인증 필요)
{ "currentPassword": "Passw0rd!23", "newPassword": "NewPassw0rd!45" }
```

```json
{ "changedAt": "2026-09-18T10:03:00", "sessionsEnded": 2 }
```

- 새 비밀번호 규칙은 회원가입과 같다: 영문+숫자 포함, 8~64자.
- 성공하면 리프레시 토큰을 모두 폐기한다(`sessionsEnded` = 폐기 수). 다른 기기는 다음 재발급 때 로그아웃되고, 현재 기기는 액세스 토큰 만료 전에 재로그인하거나 바로 로그인 화면으로 보낸다.
- `GET /users/me` 의 `passwordSet=false` 인 소셜 전용 계정은 메뉴를 노출하지 않는다. 호출하면 409.

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | AUTH_015 | 현재 비밀번호 불일치 |
| 400 | COMMON_001 | 새 비밀번호가 현재와 같음 / 규칙 위반 / 필수값 누락 |
| 409 | AUTH_016 | 소셜 전용 계정(비밀번호 없음) |

---

## 5. 티켓 대비 정정 사항

- BMA-56 은 "회원탈퇴는 BMA-12 3장에 `DELETE /users/me` 로 명세됨" 이라 했으나, BMA-12 문서 3장에는 `GET /users/me` 만 있고 구현도 없었다. 이번에 신규 구현했다.
- "BMA-30 에서 언급된 익명화 정책" 은 BMA-30 본문에 없다. 위 3절 표를 익명화 정책으로 제안한다.
- S8-16 비밀번호 변경 API 는 어떤 티켓에도 없어 S8 사양서 기준으로 추가했다.

## 6. 검증

- 단위: `UserWithdrawTest` (익명화 규칙 고정)
- 통합: `backend/scripts/verify-account.sh` (20 단계, 로그아웃 시나리오 포함) / Postman `docs/postman/BMA-56-account.postman_collection.json`
