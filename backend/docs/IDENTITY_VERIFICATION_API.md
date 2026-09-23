# S15 본인인증 API — BMA-79

S15 사양서 v1.4(S15-01~09), BMA-31(본인인증 MVP 필수, S4→S9 관문), BMA-19 안건3(미성년자 가입 불가) 기준.

## 1. 흐름

```
S4 "매칭 시작하기" ── GET /verification/identity/status ──▶ verified=true ──▶ S9 (POST /matching/queue)
                                                     └▶ verified=false ─▶ S15
S15-04 "통신사 본인인증 시작" ── POST /verification/identity/request ──▶ 인증사 SDK(transactionId, sdkParams)
SDK 결과 ── POST /verification/identity/confirm ──▶ 200 VERIFIED ─▶ S9
                                                  └▶ 403 VERIFY_003 (미성년자) ─▶ S15-06~09, 계정은 이미 롤백됨 → S15-09 확인 → S1
```

- 매칭 대기열 진입 `POST /api/v1/matching/queue` 는 본인인증 완료 계정만 통과한다. 미인증 → `403 VERIFY_001`. 완료한 계정은 **재인증을 요구하지 않는다**(AC).
- `GET /api/v1/users/me` 에도 `identityVerified`, `identityVerifiedAt` 이 실린다.

## 2. 엔드포인트 (모두 액세스 토큰 필수)

| 화면 | API |
| --- | --- |
| S4 분기 / S15 재노출 여부 | `GET /api/v1/verification/identity/status` |
| S15-04 인증 시작 | `POST /api/v1/verification/identity/request` |
| SDK 결과 수신 | `POST /api/v1/verification/identity/confirm` |

### 2.1 상태

```json
{"verified": false, "verifiedAt": null, "provider": null, "matchingAllowed": false, "lastAttemptStatus": "REQUESTED"}
```

`lastAttemptStatus`: `REQUESTED` / `VERIFIED` / `REJECTED_MINOR` / `REJECTED_DUPLICATE` / `FAILED` / `EXPIRED` / `null`(시도 없음).

### 2.2 요청

응답 `{"transactionId":"…","provider":"stub","sdkParams":{…},"expiresAt":"2026-09-24T00:41:10"}`.
거래 ID 는 10분 유효(`IDENTITY_REQUEST_EXPIRE_MINUTES`). 재요청하면 이전 거래 ID 는 만료된다. 이미 완료한 계정 → `409 VERIFY_002`.
`sdkParams` 는 업체별로 다르다(3절).

### 2.3 확인

```json
{"transactionId": "…", "providerPayload": { "...SDK 가 돌려준 결과 그대로..." }}
```

성공: `{"status":"VERIFIED","verifiedAt":"…","provider":"stub","adult":true,"matchingAllowed":true}`.

| 코드 | HTTP | 상황 | 프론트 |
| --- | --- | --- | --- |
| `VERIFY_003` | 403 | 인증사 생년월일 기준 **만 19세 미만**. 계정은 이 시점에 롤백(탈퇴 처리·토큰 폐기)된다 | S15-06~09 노출, 확인 → S1 |
| `VERIFY_004` | 409 | 같은 사람(CI)이 이미 다른 계정으로 인증함 | 안내 후 S1/고객센터 |
| `VERIFY_005` | 404 | 거래 ID 가 없거나 내 요청이 아님 | 다시 요청 |
| `VERIFY_006` | 409 | 거래 ID 만료·이미 처리 | 다시 요청 |
| `VERIFY_007` | 400 | 인증사 검증 실패 | 다시 시도 |
| `VERIFY_002` | 409 | 이미 완료 | S9 로 |

미성년자 판정은 **인증사가 돌려준 생년월일만** 기준으로 한다. 프로필(S3)의 자기 입력 생년월일은 참고용이며 판정에 쓰지 않는다.
만 나이는 생일이 지나야 오른다(19번째 생일 당일부터 성인).

## 3. 인증사 연동 구조 (업체 미확정 대응)

업체 연동은 `IdentityVerificationGateway` 뒤에 숨겼다. 프론트 계약(요청 → SDK → 확인)은 업체와 무관하게 고정된다.

| 설정 `app.verification.identity.provider` | 구현 | 상태 |
| --- | --- | --- |
| `stub` (기본) | `StubIdentityVerificationGateway` — `providerPayload.result{name,birthDate,genderCode,phoneNumber,ci?}` 를 그대로 신뢰 | 개발·검증용. **운영 금지** |
| `pass` / `nice` / `toss` … | 업체 확정 후 구현체 추가 | 4절 제안 참고 |

스텁 확인 예시:

```json
{"transactionId":"…","providerPayload":{"result":{"name":"홍길동","birthDate":"1995-05-05","genderCode":"M","phoneNumber":"01012345678"}}}
```

## 4. 인증사 후보 비교 (팀 확인용 제안)

허익님 요청(2026-08-22)대로 후보를 정리한 것이며 **계약은 팀 결정** 사항이다. 비용은 업체 견적에 따라 달라지므로 "대략"으로만 적었다.

| 후보 | 방식 | 장점 | 단점·주의 | 비용(대략, 견적 필요) |
| --- | --- | --- | --- | --- |
| **NICE평가정보 본인확인(표준창)** | 웹/앱 표준창 안에서 PASS 앱·문자·공동인증 선택. 결과는 암호화 토큰으로 서버 검증 | 가장 널리 쓰여 레퍼런스·라이브러리 풍부, 통신 3사 PASS 앱 인증 포함, CI/DI 제공 | 계약·심사(사업자, 개인정보 처리 서류) 1~2주, 초기 설정비 있을 수 있음 | 건당 수십 원대 + 초기 비용 |
| **KMC(한국모바일인증)** | 표준창(PASS 앱·문자) | NICE 와 유사, 스타트업 계약 사례 많음 | 레퍼런스가 NICE 보다 적음 | 건당 수십 원대 |
| **토스 본인인증** | 토스 앱 푸시로 인증, REST API | 연동 단순(REST), 토스 사용자 UX 좋음, 결제(BMA-84 토스)와 계약 창구 통일 | 토스 미사용자는 인증 불가 → 단독 사용은 어려움, 보조 수단으로 적합 | 건당 과금(견적) |
| **PASS 직접(통신 3사 각각)** | 사별 API | — | 3사 각각 계약해야 해 스타트업에 비현실적 | — |

**제안**: 1순위 NICE 표준창(PASS 앱 포함, CI 제공), 보조로 토스 본인인증. 계약이 되면 `NiceIdentityVerificationGateway` 를 추가하고 `IDENTITY_PROVIDER=nice` 로 전환한다(프론트 변경 없음, `sdkParams` 내용만 바뀜).
공통 요건: 사업자등록, 개인정보처리방침 내 본인확인 항목, CI 보관 정책(우리는 SHA-256 해시만 저장).

## 5. 저장 데이터

- `US_USER`: `IDENTITY_VERIFIED_YN/DATE`, `IDENTITY_PROVIDER`, `IDENTITY_CI_HASH`(SHA-256, 중복 계정 방지), `IDENTITY_BIRTH_DATE`(성인 판정 근거). 탈퇴 시 해시·생년월일 삭제.
- `US_IDENTITY_VERIFICATION`: 시도 단위 이력(거래 ID·만료·상태·마스킹된 이름/전화·생년월일·실패 사유). 원문 실명·전화번호·CI 는 저장하지 않는다.

## 6. 검증

```bash
BASE=http://localhost:8080 DB_EXEC_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e" bash backend/scripts/verify-identity.sh   # 12단계
```

Postman `postman/BMA-79-identity.postman_collection.json`. 기존 BMA-66/84/72 컬렉션·스크립트에는 대기열 진입 전 본인인증(스텁) 단계를 추가했다.

## 7. 결정 사항 (티켓에 없어 정한 것)

- 미성년자 롤백은 **confirm 시점**에 서버가 수행한다(S15-09 확인 버튼은 화면 이동만). 거부 화면이 떠 있는 동안 계정이 남아 있지 않게 하기 위함.
- 롤백은 기존 탈퇴 처리(`AccountService.withdraw`)와 같다: 프로필·사진·토큰 삭제, 이메일 익명화. 같은 이메일로 재가입은 가능하지만 같은 CI 로는 성인이 될 때까지 인증을 통과할 수 없다.
- 매칭 관문은 대기열 진입(S9)에만 건다. 좋아요·추천 등 기존 흐름과 이미 성사된 매칭의 대화는 그대로.
- 인증사 전화번호로 `PHONE_VERIFIED_YN` 을 갱신하지 않는다(휴대전화 유니크 제약 충돌 방지). 필요하면 별도 티켓.
