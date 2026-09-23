# Reveal API (S10 매칭성사) — BMA-69

S10 사양서 v1.6 과 BMA-19 안건1 확정값(2026-08-18)을 반영한다. 공통 응답 형식은 `ApiResponse{success, code, message, data, timestamp}` 이며 아래는 `data` 만 적는다. 모든 엔드포인트는 인증이 필요하고, 매칭 참여자만 접근할 수 있다(아니면 `404 MATCH_003`).

| S10 오브젝트 | 엔드포인트 |
|---|---|
| S10-02~06, 09~11, 18 상대 정보·진행바·안내 / S10-19 보유 재매칭권 | `GET /api/v1/matches/{matchId}` |
| S10-05 진행바, S10-12 다음 단계 칩, S10-14 동의 모달 조건 | `GET /api/v1/matches/{matchId}/reveal` |
| 상대 프로필만 | `GET /api/v1/matches/{matchId}/reveal/partner` |
| S10-12 다음 단계 요청하기 | `POST /api/v1/matches/{matchId}/reveal-request` |
| S10-16 동의하고 열기 / S10-17 나중에 | `POST /api/v1/matches/{matchId}/reveal-consent` |
| S10-19 재매칭 요청(즉시) | `POST /api/v1/matches/{matchId}/rematch` (BMA-84/66) |
| S10-07 대화 시작하기 | `chatRoomId` 로 S11 이동 |

---

## 1. 단계와 전환 규칙

| 단계 | 이름(정책) | 사양서 | 보이는 것 |
|---|---|---|---|
| 0 | 실루엣 | 1단계 실루엣(blur 10px, "???") | 실루엣 이미지, 나이대, 성별, MBTI, 시/도 |
| 1 | 부분 공개 | 2단계 부분공개(blur 2px, "김민??") | 블러 이미지, 나이, 직업, **키(S10-18)**, 이름 앞 절반(`nicknameMasked`) |
| 2 | 전체 공개 | 3단계 | 원본 이미지, 이름 원문, 시/군/구 |

다음 단계로 올라가려면 세 조건이 모두 필요하다(`RV_REVEAL_POLICY`, V12).

| 조건 | 1단계로 | 2단계로 | 비고 |
|---|---|---|---|
| 매칭 후 경과 시간 | 24시간 | 24시간 | 정밀매칭 구독자는 스킵(아래 4절) |
| 양측 각자 메시지 | 10개(합산 20) | 25개(합산 50) | 내가 보낸 수와 상대가 보낸 수를 따로 센다 |
| 상호 동의 | 필요 | 필요 | 요청 = 내 동의. 상대가 동의하는 순간 상승 |

1단계 조건은 BMA-19 확정값이다. 2단계 임계값은 확정되지 않아 같은 규칙에 각자 25개(기존 시드 50 유지)를 두었다. 기존의 "대화 시간(분)" 조건은 확정안에 없어 0 으로 내렸다.

---

## 2. 매칭 상세

```
GET /api/v1/matches/{matchId}
```

```json
{
  "match": { "matchId": 31, "partnerUserId": 12, "matchStatus": "ACTIVE", "matchType": "QUEUE", "matchDate": "...",
             "revealLevel": 0, "chatRoomId": 27,
             "partner": { "userId": 12, "revealLevel": 0, "nickname": null, "nicknameMasked": null, "age": null, "ageGroup": "20대 후반",
                          "genderCode": "F", "regionCode": "SEOUL", "regionName": "서울특별시", "mbtiCode": "INFP",
                          "occupation": null, "heightCm": null, "introduction": "...", "imageKeys": ["...silhouette"] },
             "commonInterests": ["문화생활"],
             "reveal": { "currentLevel": 0, "currentLevelName": "실루엣", "nextLevel": 1, "messageCount": 3, "requiredMessageCount": 20, "progressRate": 0, "mutualConsentNeeded": true } },
  "reveal": { ...아래 3절과 동일... },
  "rematchTickets": 1
}
```

`match` 는 S5 카드와 같은 객체(BMA-47), `reveal` 은 3절의 상태, `rematchTickets` 는 S10-19 "보유 N개".

---

## 3. Reveal 상태

```
GET /api/v1/matches/{matchId}/reveal
```

```json
{
  "matchId": 31, "currentLevel": 0, "currentLevelName": "실루엣", "nextLevel": 1, "nextLevelName": "부분 공개", "maxLevelReached": false,
  "matchedAt": "2026-09-23T20:00:00", "requiredHours": 24, "hoursSatisfied": false, "hoursRemainingMinutes": 1092,
  "myMessages": 10, "partnerMessages": 3, "requiredMessagesPerUser": 10, "messagesSatisfied": false,
  "myMessagesRemaining": 0, "partnerMessagesRemaining": 7, "totalMessages": 13, "requiredTotalMessages": 20,
  "progressRate": 24,
  "canRequest": false, "myConsent": "PENDING", "partnerConsent": "PENDING", "incomingRequest": false, "requestedAt": null,
  "lastLevelUpDate": null
}
```

| 필드 | 화면 |
|---|---|
| `hoursRemainingMinutes`, `partnerMessagesRemaining` / `myMessagesRemaining` | S10-12 미충족 칩 "18시간 12분 후 · 대화 7개 더 필요" |
| `canRequest` | S10-12 충족 칩(탭 가능) — 시간·메시지 충족이고 아직 내가 요청하지 않음 |
| `incomingRequest` | S10-14 동의 모달 노출 — 상대가 요청했고 내 동의를 기다림 |
| `progressRate` | S10-05 진행바(3구간 중 다음 단계까지). 시간·내 메시지·상대 메시지 중 가장 덜 채워진 쪽 |
| `myConsent` / `partnerConsent` | `PENDING` 또는 `ACCEPTED` 뿐. **거절 상태는 없다** |
| `maxLevelReached` | 전체 공개 도달. `nextLevel=null`, `progressRate=100` |

---

## 4. 요청과 동의

```
POST /api/v1/matches/{matchId}/reveal-request           (본문 없음)
POST /api/v1/matches/{matchId}/reveal-consent  { "consent": true }
```

```json
{ "leveledUp": false, "status": { ...3절 상태... } }
```

- 요청은 곧 내 동의다. 상대가 이미 동의(요청)해 두었으면 이 호출로 `leveledUp=true` 가 되고 양쪽에 S7 "프로필 공개 단계가 올라갔어요" 알림이 간다. 아니면 상대에게 S7-12 "???님이 다음 단계를 요청했어요" 알림이 한 번만 간다(재요청은 멱등, 알림 중복 없음).
- `consent:true` 는 요청과 같은 처리다(동의 → 양쪽 동의 → 상승). `consent:false` 또는 본문 없음은 "나중에"(S10-17): **아무것도 기록하지 않는다.** 상대 화면의 `partnerConsent` 는 `PENDING` 그대로이고, 상대에게 거절 사실이 노출되지 않는다.
- 구독자 24시간 스킵(BMA-19 안건4): 정밀매칭 구독자는 24시간 전에도 요청할 수 있다. 상대(비구독자)의 화면에는 일반 요청과 똑같이 `incomingRequest=true` 로 보이고, 상대가 동의하면 시간 조건 없이 올라간다. 비구독자 본인의 `canRequest` 는 실제 시간 기준이라 상대의 구독을 유추할 수 없다. 응답 어디에도 구독 필드는 없다.

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | REVEAL_002 | 조건 미충족(메시지 각자 N개 또는 24시간). 메시지에 현재/필요 수치 포함 |
| 400 | REVEAL_003 | 이미 전체 공개 |
| 404 | MATCH_003 | 참여자가 아니거나 종료된 매칭 |

### (구) `POST /api/v1/matches/{matchId}/reveal/consent`

`{ "revealLevel": 1, "consent": true }`. `revealLevel` 은 현재 단계 + 1 이어야 하며(아니면 `400 REVEAL_003`) 나머지는 새 API 와 같다. 기존 컬렉션(BMA-47/53) 호환용으로 남겨 두었다.

---

## 5. 스키마 (V12)

`RV_REVEAL_POLICY` 에 `MIN_HOURS_SINCE_MATCH`, `MIN_MESSAGES_PER_USER` 추가 및 시드 갱신(0 실루엣 / 1 부분 공개 24h·10·20·동의 / 2 전체 공개 24h·25·50·동의). 각자 메시지 수는 `CH_CHAT_MESSAGE` 를 발신자별로 세므로 별도 컬럼이 없다. 동의는 `RV_REVEAL_CONSENT`(PENDING/ACCEPTED 만 사용).

## 6. 결정·정정 사항

- 티켓의 `GET /matches/{matchId}` 와 `POST /matches/{matchId}/rematch`(BMA-84/66 에서 구현 완료)에 더해, 댓글에서 요구한 `reveal-request` / `reveal-consent` 를 추가했다.
- 이름 부분 공개 규칙(S10-10 미확정)은 "앞 절반(최소 1자) + ?" 로 두었다. 확정되면 `ProfileMaskingService.maskNickname` 한 곳만 바꾼다.
- 2단계(전체 공개) 임계값 각자 25개는 미확정이라 정한 값. `RV_REVEAL_POLICY` 행만 바꾸면 된다.
- 단계 0 의 이름을 "미공개"에서 사양서 용어 "실루엣"으로 바꿨다(BMA-47 문서·컬렉션 갱신).

## 7. 검증

- 단위: `RevealPolicyConditionTest`(각자 기준), 기존 `ProfileMaskingServiceTest`·`RevealPolicyProgressTest`
- 통합: `backend/scripts/verify-reveal.sh` (17 단계, 24시간 경과는 `DB_EXEC_CMD` 로 매칭 일시 조정) / Postman `docs/postman/BMA-69-reveal.postman_collection.json`
