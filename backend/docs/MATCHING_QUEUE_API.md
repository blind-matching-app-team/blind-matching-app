# 매칭대기 API (S9) — BMA-66

S9 매칭대기 사양서 v1.5 의 백엔드. 대기열 등록·상태 폴링·취소와 **실제로 짝을 찾아 매칭을 만드는 엔진**을 포함한다.
공통 응답 형식은 `ApiResponse{success, code, message, data, timestamp}` 이며 아래는 `data` 만 적는다. 모든 엔드포인트는 인증이 필요하다.

| S9 오브젝트 | 엔드포인트 |
|---|---|
| 진입(S4/S5 → S9) | `POST /api/v1/matching/queue` |
| 대기 화면 폴링, S9-04 경과 시간, 성사 시 S10 이동, 타임아웃 시 S9-06~09 | `GET /api/v1/matching/queue` |
| S9-01/05 대기 취소 → S5 | `DELETE /api/v1/matching/queue` |
| S9-08 다시 시도 | `POST /api/v1/matching/queue` (새 항목) |
| 성사/타임아웃 푸시(보조) | STOMP 구독 `/user/queue/matching` |

---

## 1. 진입

```
POST /api/v1/matching/queue
```

```json
{
  "status": "WAITING",
  "queueId": 41, "entrySource": "FREE",
  "enteredAt": "2026-09-23T20:00:00", "expiresAt": "2026-09-23T20:05:00",
  "waitedSeconds": 0, "remainingSeconds": 300,
  "matchId": null, "chatRoomId": null, "partnerUserId": null
}
```

- 진입 재원(BMA-84): 무료 3회/일(`FREE`) → 매칭기회 이용권(`ITEM`) → 둘 다 없으면 `409 PAY_006` 이고 프론트는 S9 대신 구매 모달(소모형 탭)을 연다(사양서 v1.4 갱신).
- 진입 직후 한 번 짝을 찾아본다. 이미 대기 중인 맞는 상대가 있으면 응답이 바로 `MATCHED`(matchId·chatRoomId·partnerUserId)다. 이때 프론트는 S9 를 거치지 않고 S10 으로 간다.
- 이미 대기 중이면 새로 소모하지 않고 그 항목을 돌려준다.

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | MATCH_005 | 프로필 미완성(닉네임·생년월일·지역·성별) |
| 409 | MATCH_006 | 선호 조건에서 매칭 참여를 꺼 둠 |
| 403 | VERIFY_001 | 본인인증 미완료(S4 → S15 관문, BMA-79). `IDENTITY_VERIFICATION_API.md` |
| 409 | PAY_006 | 매칭 기회 소진 → 구매 모달 |

## 2. 상태 폴링

```
GET /api/v1/matching/queue
```

가장 최근 항목 기준이다. 권장 폴링 주기 2~3초.

| status | 의미 | 프론트 |
|---|---|---|
| `WAITING` | 대기 중. `waitedSeconds`(S9-04), `remainingSeconds` | 대기 화면 유지 |
| `MATCHED` | 성사. `matchId`·`chatRoomId`·`partnerUserId` | S10 으로 이동 |
| `TIMEOUT` | 5분 초과. 소모한 이용권은 환불됨 | S9-06~09 (다시 시도 / 나중에 하기) |
| `NONE` | 대기 항목 없음(취소했거나 진입한 적 없음) | S5 |

대기 중인데 시간이 지난 항목은 이 조회에서 만료 처리되므로, 스케줄러보다 먼저 폴링해도 `TIMEOUT` 이 나온다.

## 3. 취소

```
DELETE /api/v1/matching/queue   → 200 (대기 중이 아니어도 200)
```

취소한 항목은 `CANCELED` 로 남고 이후 조회는 `NONE`. 무료 기회는 이미 쓴 것으로 센다(사용자 의사로 나간 것이라 환불하지 않는다).

## 4. 짝 찾기 규칙 (엔진)

1. 후보는 `WAITING` 항목을 **진입 순서(FIFO)** 로 본다. 만료된 항목은 그 자리에서 정리한다.
2. 제외: 자기 자신, 차단 관계(양방향), 이미 매칭 이력이 있는 상대(진행 중·종료 모두), 프로필 미완성, 매칭 참여를 끈 사용자.
3. **서로의 선호 조건을 모두 만족**해야 한다. 규칙은 추천 목록 필터와 같다: 성별 일치, 만 나이 범위(경계 포함), 희망 지역(시/군/구 일치 또는 그 시/도 하위). 키 조건은 없다(BMA-19 안건2). 선호 조건 행이 없으면 모두 허용.
4. 요청자가 정밀매칭 구독 중이면(BMA-84) 조건을 만족하는 후보 중 **공통 관심사가 가장 많은** 상대를 고른다. 아니면 첫 후보.
5. 두 항목에 행 잠금을 걸고 아직 `WAITING` 인지 다시 확인한 뒤 매칭을 만든다(`MT_MATCH.MATCH_TYPE=QUEUE`, 이전에 종료된 매칭이 있으면 재활성화) → 채팅방 생성 → Reveal 진행 초기화 → 양쪽 항목 `MATCHED`(matchId 기록) → 양쪽에 S7 "매칭이 성사되었어요!" 알림 → STOMP 푸시.

실행 시점: 진입·재매칭 직후 1회 + `app.matching.queue-sweep-ms`(기본 5초) 주기의 스케줄러(만료 처리 + 재시도). 단일 인스턴스 전제이며 다중 인스턴스 전에는 분산 락이 필요하다.

## 5. 타임아웃과 환불

- 기준: `app.matching.queue-expire-minutes` = **5** (BMA-65 댓글 확정, 사양서 v1.5). 환경변수 `MATCHING_QUEUE_EXPIRE_MINUTES`.
- 만료 항목은 `EXPIRED` 로 바꾸고: `ITEM` 진입이면 매칭기회 이용권 1개, `REMATCH` 진입이면 재매칭권 1개를 구매분으로 돌려준다(`PY_ITEM_LEDGER.REASON_CODE=REFUND`). `FREE` 진입은 오늘의 무료 횟수에서 세지 않는다. 짝을 못 찾은 것이 사용자 손해가 되지 않게 하기 위한 결정.
- 만료 시 `/user/queue/matching` 으로 `{"type":"TIMEOUT"}` 푸시.

## 6. WebSocket 푸시 (보조)

STOMP 연결(`/ws`, CONNECT 에 Bearer 액세스 토큰) 후 `/user/queue/matching` 을 구독하면 성사·타임아웃을 받는다.

```json
{"type":"MATCHED","queueId":41,"matchId":31,"chatRoomId":27,"partnerUserId":12}
{"type":"TIMEOUT","queueId":41,"matchId":null,"chatRoomId":null,"partnerUserId":null}
```

폴링이 정본이다. 소켓이 끊겨 있어도 `GET` 으로 같은 결과를 얻는다. 티켓의 "폴링/WebSocket 결정" 은 **둘 다(폴링 정본 + 푸시 보조)** 로 결정했다. S11 채팅과 같은 STOMP 인프라(BMA-73)를 쓴다.

## 7. 재매칭권과의 연동 (BMA-84)

`POST /api/v1/matches/{matchId}/rematch` 는 현재 매칭을 끝내고 `REMATCH` 재원으로 대기열에 넣은 뒤 즉시 짝을 찾는다. 응답의 `queue` 가 이 문서의 상태 객체이며, 맞는 상대가 대기 중이면 곧바로 `MATCHED` 다("즉시 재배정"). 5분 안에 짝이 없으면 재매칭권을 돌려준다.

## 8. 스키마 (V11)

`MT_MATCH_QUEUE` 에 `MATCH_ID`, `MATCHED_DATE` 추가. 상태 값: `WAITING` / `MATCHED` / `CANCELED` / `EXPIRED`.

## 9. 결정·정정 사항

- 성사 알림 방식: 폴링 정본 + STOMP 푸시 보조.
- 타임아웃 5분(확정값). 만료 시 이용권 환불·무료 기회 미차감은 티켓에 없어 이번에 정한 정책.
- 진입 조건에 "매칭 참여 여부(선호 조건)" 를 추가(`MATCH_006`). 추천 목록과 같은 기준.
- 매칭 이력이 있는 상대(종료 포함)는 대기열에서 다시 맺지 않는다. 재회를 허용하려면 제외 집합에서 종료 매칭을 빼면 된다.
- 정밀매칭 가중치는 "공통 관심사 최다" 로 시작했다. 가중치 조정 UI 는 미정.

## 10. 검증

- 단위: `PreferenceMatcherTest`(성별·나이·지역 규칙)
- 통합: `backend/scripts/verify-queue.sh` (17 단계, 타임아웃·환불은 `DB_EXEC_CMD` 설정 시) / Postman `docs/postman/BMA-66-matching-queue.postman_collection.json`
