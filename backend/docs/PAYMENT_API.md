# 이용권 구매/구독 결제 API — BMA-84

공통컴포넌트 사양서 v1.7 의 이용권 구매 모달(CM-13~17)과 BMA-17/BMA-19 안건4(소모형·구독형, 이월 최대 2개월)를 구현한다.
공통 응답 형식은 `ApiResponse{success, code, message, data, timestamp}` 이며 아래는 `data` 만 적는다.
웹훅을 제외한 모든 엔드포인트는 인증(Bearer 액세스 토큰)이 필요하다. **가격은 전부 임의치**이며 실제 가격 정책 확정 시 `PY_PRODUCT` 만 바꾼다.

| 화면/오브젝트 | 엔드포인트 | 비고 |
|---|---|---|
| CM-14 탭, CM-15/16 카드 | `GET /api/v1/payments/products` | `productType` ITEM=소모형 탭, SUBSCRIPTION=구독형 탭 |
| CM-15 '구매' | `POST /api/v1/payments/consumable` | 결제창 승인 또는 저장 카드 청구. 성공 시 "결제가 완료됐어요" 토스트, 모달 유지 |
| CM-17 '구독하기' | `POST /api/v1/payments/subscription` | 빌링키 발급 + 첫 달 청구. 성공 시 "구독이 시작됐어요" 토스트, 모달 닫힘 |
| S8-19 구독 상태 | `GET /api/v1/payments/subscription` | |
| 구독 해지 | `DELETE /api/v1/payments/subscription` | 남은 기간 유지, 환불 없음 |
| S5-12/S9/S10-07 소진 판정 | `GET /api/v1/payments/me/items` | 종류별 잔여, 오늘의 무료 매칭 기회, 구독 여부 |
| 매칭 기회 소모 | `POST /api/v1/matching/queue` (기존) | 무료 3회/일 → 매칭기회 이용권 → `409 PAY_006` |
| S5-12/S10-19 재매칭 | `POST /api/v1/matches/{matchId}/rematch` | 재매칭권 1개 소모, 현재 매칭 종료 + 즉시 대기열 |
| 결제 내역 | `GET /api/v1/payments/me` | |
| 웹훅 | `POST /api/v1/payments/webhook` | 인증 없음, 서명 검증 |
| 운영: 청구 배치 수동 실행 | `POST /api/v1/admin/payments/subscriptions/renew?asOf=` | ROLE_ADMIN |

---

## 1. 상품 목록

```
GET /api/v1/payments/products
```

```json
[
  {"productId":1,"productCode":"MATCH_CHANCE","productName":"매칭기회 추가 1회","productType":"ITEM","price":1000.00,"currency":"KRW",
   "benefit":{"itemType":"MATCH_CHANCE","quantity":1}},
  {"productId":2,"productCode":"REMATCH_TICKET","productName":"재매칭권 1회","productType":"ITEM","price":2000.00,"currency":"KRW",
   "benefit":{"itemType":"REMATCH_TICKET","quantity":1}},
  {"productId":3,"productCode":"PREMIUM_MONTHLY","productName":"정밀매칭 구독","productType":"SUBSCRIPTION","price":9900.00,"currency":"KRW",
   "benefit":{"periodMonths":1,"monthlyGrants":{"MATCH_CHANCE":3,"REMATCH_TICKET":2},"carryOverMonths":2,"precisionMatching":true,"revealWaitSkip":true}}
]
```

CM-16 카드의 혜택 4줄은 `benefit` 으로 그린다: 정밀도 향상(`precisionMatching`), 24시간 스킵(`revealWaitSkip`), 매월 지급(`monthlyGrants`), 이월 최대 `carryOverMonths` 개월.
빠른매칭권은 폐기되어 상품에 없다(v1.5).

---

## 2. 소모형 이용권 결제 (CM-15)

```
POST /api/v1/payments/consumable
```

두 가지 결제 방식 중 하나를 쓴다.

| 방식 | 요청 본문 | 흐름 |
|---|---|---|
| 결제창 승인 | `{"productCode":"MATCH_CHANCE","orderId":"ord-...","paymentKey":"..."}` | 프론트가 토스 결제위젯으로 결제 → 돌려받은 `orderId`/`paymentKey` 를 보냄 → 서버가 승인(`/v1/payments/confirm`) |
| 저장 카드 청구 | `{"productCode":"MATCH_CHANCE"}` | 구독 등록 때 저장된 빌링키로 서버가 바로 청구. 저장 카드가 없으면 `409 PAY_008` |

- `orderId` 는 6~64자, 영문/숫자/`-`/`_` (토스 규칙). 프론트가 만들며 결제위젯에 넘긴 값과 같아야 한다.
- 금액은 클라이언트가 보내지 않는다. 서버가 상품 가격으로 승인하고, PG 가 실제 승인한 금액과 대조한다(`PAY_003`).
- **멱등**: 같은 `orderId` 재요청은 기존 결제를 그대로 돌려주고 이용권을 다시 주지 않는다. 네트워크 재시도·중복 클릭 대비(BMA-59 예외케이스 2).
- 다른 사용자의 `orderId` 는 `404 PAY_005`.

```json
{
  "payment": {"paymentId":12,"orderId":"ord-a-1","orderName":"매칭기회 추가 1회","productId":1,"paymentKind":"CONSUMABLE",
              "payMethod":"WIDGET","status":"DONE","amount":1000.00,"currency":"KRW","approvedDate":"2026-09-23T17:00:00"},
  "items": [{"itemType":"MATCH_CHANCE","purchased":1,"granted":0,"total":1},{"itemType":"REMATCH_TICKET","purchased":0,"granted":0,"total":0}]
}
```

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | PAY_002 | PG 승인 거부/실패. 메시지에 PG 오류 코드가 괄호로 붙는다 → "결제에 실패했어요" 토스트, 모달 유지 |
| 400 | PAY_003 | 승인 금액이 상품 가격과 다름 |
| 404 | PAY_001 | 상품 없음/판매 중지/유형 불일치 |
| 404 | PAY_005 | 타인의 주문 ID |
| 409 | PAY_008 | 저장 카드 없음(저장 카드 청구 방식) |

---

## 3. 이용권 잔여와 무료 매칭 기회

```
GET /api/v1/payments/me/items
```

```json
{
  "items": [
    {"itemType":"MATCH_CHANCE","purchased":1,"granted":3,"total":4},
    {"itemType":"REMATCH_TICKET","purchased":0,"granted":2,"total":2}
  ],
  "freeChancesToday": {"limit":3,"used":1,"remaining":2},
  "subscribed": true
}
```

- `purchased` 는 구매분(소멸 없음), `granted` 는 구독 지급분(최대 2개월치까지만 누적). 사용은 지급분부터 한다.
- 두 종류 모두 항상 포함된다(없으면 0).

### 매칭 기회 (S5-12 / S9)

`POST /api/v1/matching/queue` 진입 재원은 다음 순서로 정해지고 응답 `entrySource` 에 남는다.

| 순서 | entrySource | 조건 |
|---|---|---|
| 1 | `FREE` | 오늘 무료 진입 횟수 < `app.matching.daily-free-chances`(기본 3) |
| 2 | `ITEM` | 매칭기회 이용권 잔여 ≥ 1 → 1개 소모 |
| 3 | — | `409 PAY_006` "오늘의 매칭 기회를 모두 사용했어요" → 구매 모달(소모형 탭) |

이미 대기 중이면 새로 소모하지 않고 그 항목을 돌려준다. 만료된 항목을 갱신하는 새 진입은 기회를 다시 쓴다.

### 재매칭권 (S5-12 / S10-19)

```
POST /api/v1/matches/{matchId}/rematch
```

```json
{"endedMatchId":31,"queue":{"queueId":9,"queueStatus":"WAITING","entrySource":"REMATCH","enterDate":"...","expireDate":"..."},"remainingRematchTickets":1}
```

재매칭권 1개를 소모해 현재 매칭을 종료하고(상대에게는 S7-14 "매칭이 종료됐어요" 알림만, 사유 비노출) 대기 없이 즉시 대기열에 넣는다.
이 진입은 무료 일일 기회를 쓰지 않는다. 실제 짝 배정은 대기열 매칭(BMA-66)이 담당한다.
S5 와 S10 은 같은 엔드포인트를 쓴다(BMA-14 댓글: 사용처 통일).

| 상태 | 코드 | 상황 |
|---|---|---|
| 404 | MATCH_003 | 매칭 없음/이미 종료/참여자 아님 |
| 409 | PAY_007 | 재매칭권 없음 → 구매 모달(소모형 탭) |

---

## 4. 구독 (CM-16/17, S8-19)

### 등록

```
POST /api/v1/payments/subscription
```

| 요청 본문 | 결제 수단 |
|---|---|
| `{"authKey":"..."}` | 토스 결제창(빌링 인증)이 준 `authKey` 로 빌링키 발급 — **기본 방식** |
| `{"card":{"cardNumber":"...","expiryYear":"30","expiryMonth":"12","identityNumber":"900101","password":"00"}}` | 카드 직접 입력(API 개별 연동·테스트) |
| `{}` | 이미 저장된 카드 재사용. 없으면 `400 PAY_011` |

`productCode` 를 생략하면 `PREMIUM_MONTHLY`. 처리 순서: 빌링키 발급·저장(AES-GCM 암호화, 사용자당 1행) → 첫 달 청구 → 구독 ACTIVE → 이번 달 이용권 지급.
첫 달 청구가 실패하면 구독은 만들어지지 않고 결제 원장에 FAILED 만 남는다.

```json
{
  "subscriptionId":5,"status":"ACTIVE","productCode":"PREMIUM_MONTHLY","price":9900.00,"currency":"KRW",
  "startedDate":"2026-09-23T17:00:00","currentPeriodStart":"2026-09-23","currentPeriodEnd":"2026-10-22","nextBillingDate":"2026-10-23",
  "canceledDate":null,"endedDate":null,
  "card":{"company":"신한","numberMasked":"485479******3803","registeredAt":"2026-09-23T17:00:00"},
  "monthlyGrants":{"MATCH_CHANCE":3,"REMATCH_TICKET":2},"carryOverMonths":2,"lastPaymentId":13
}
```

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | PAY_002 | 빌링키 발급 거부 또는 첫 달 청구 실패 → "결제에 실패했어요" 토스트, 모달 유지 |
| 400 | PAY_011 | 결제 수단 없음 |
| 409 | PAY_009 | 이미 이용 중(해지했지만 기간이 남은 경우 포함) |

### 상태 / 해지

```
GET    /api/v1/payments/subscription   → {"subscribed":true|false, "subscription":{...}|null}
DELETE /api/v1/payments/subscription   → 구독 상세 (status=CANCELED, nextBillingDate=null)
```

- `subscribed` 는 혜택을 받는 중인지다. 해지해도 `currentPeriodEnd` 까지는 `true`. 한 번도 구독한 적 없으면 `subscription=null`.
- 해지는 다음 청구만 멈춘다. 재호출은 멱등. 이용 중인 구독이 없으면 `404 PAY_010`.

### 상태 전이

| 상태 | 의미 | 전이 |
|---|---|---|
| ACTIVE | 이용 중, 청구 예정 | 청구 성공 → ACTIVE(기간 연장) / 실패 → PAST_DUE / 해지 → CANCELED |
| PAST_DUE | 청구 실패, 유예 중(매일 재시도) | 성공 → ACTIVE / 실패 횟수 > `grace-days`(기본 3) → EXPIRED |
| CANCELED | 해지 요청, 기간 종료까지 이용 | 기간 종료 다음 날 배치 → EXPIRED |
| EXPIRED | 이용 종료 | 재구독 시 새 행 |

### 매월 자동 지급과 이월

매일 `app.payment.subscription.renew-cron`(기본 04:00)에 청구일이 지난 구독을 빌링키로 청구한다. 성공하면 다음 달 기간으로 넘기고 이용권을 지급한다.

- 지급량: 매칭기회추가 3, 재매칭권 2 (v1.5 확정)
- 이월: 지급분 잔여는 `월 지급량 × carryOverMonths(2)` 를 넘지 못한다. 넘는 만큼 소멸하고 `PY_ITEM_LEDGER` 에 `EXPIRE` 로 남는다. 구매분은 소멸하지 않는다.
- 멱등: 같은 구독·같은 달·같은 종류는 `GRANT_KEY` 유니크 제약으로 한 번만 지급된다. 배치가 두 번 돌아도 안전하다.
- 청구 실패 시 사용자에게 SYSTEM 알림("구독 결제에 실패했어요"), 유예 초과 종료 시 "구독이 종료됐어요".

수동 실행(운영·검증): `POST /api/v1/admin/payments/subscriptions/renew?asOf=2026-10-23` → `{"asOf":"2026-10-23","due":1,"renewed":1,"failed":0,"expired":0}`.

---

## 5. 토스페이먼츠 연동

| 설정 | 값 |
|---|---|
| `PAYMENT_GATEWAY` | `stub`(기본, 항상 성공) / `toss` |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | API 개별 연동 키(`test_ck_`/`test_sk_`). 세트 불일치·비운영 라이브 키는 기동 중단(`TossApiKeyGuard`) |
| `BILLING_KEY_SECRET` | 빌링키 암호화 키. 비우면 JWT 시크릿에서 파생(경고). 운영에서는 반드시 별도 설정 |
| `PAYMENT_WEBHOOK_SECRET` | 웹훅 HMAC 시크릿. 비우면 검증 생략(경고) |

토스 API 매핑: 결제창 승인 `/v1/payments/confirm`, 빌링키 발급 `/v1/billing/authorizations/issue`(authKey) · `/card`(직접 입력), 자동결제 `/v1/billing/{billingKey}`(Idempotency-Key = orderId), 취소 `/v1/payments/{paymentKey}/cancel`.
자동결제는 테스트 환경에서만 열려 있고 **라이브 전환에는 별도 계약**이 필요하다(docs/TOSS_SANDBOX_VERIFICATION.md).

스텁 규칙(통합 테스트용): 카드번호가 `0000` 으로 시작하면 발급 거부, `paymentKey` 가 `fail-` 로 시작하면 승인 거부.

---

## 6. 스키마 (V10)

| 테이블 | 용도 |
|---|---|
| `PY_PRODUCT` (시드) | 상품 3종, `BENEFIT_JSON` 에 혜택 구성 |
| `PY_BILLING_KEY` | 사용자당 1행. `CUSTOMER_KEY` + `BILLING_KEY_ENC`(AES-GCM) 쌍, 카드사·마스킹 번호 |
| `PY_SUBSCRIPTION` | 상태, 이용 기간, 다음 청구일, 실패 횟수 |
| `PY_USER_ITEM` | 사용자 × 종류당 1행. 구매분/지급분 분리 |
| `PY_ITEM_LEDGER` | 지급/사용/소멸 이력, 월 지급 멱등 키 |
| `PY_PAYMENT` (+컬럼) | `PAYMENT_KIND`, `PAY_METHOD`, `ORDER_NAME`, `SUBSCRIPTION_ID` |
| `MT_MATCH_QUEUE` (+컬럼) | `ENTRY_SOURCE` FREE/ITEM/REMATCH |

---

## 7. 남은 일 / 다른 티켓과의 경계

- 정밀매칭 가중치 조정(`precisionMatching`)과 Reveal 24시간 대기 스킵(`revealWaitSkip`)은 구독 여부(`SubscriptionService.isSubscribed`)만 제공한다. 실제 반영은 BMA-66(대기열 매칭)·BMA-69(Reveal)에서 한다.
- 재매칭의 "즉시 재배정"은 대기열 진입까지다. 짝을 찾는 로직은 BMA-66.
- 환불/부분취소 엔드포인트는 없다(정책 미정). 게이트웨이에는 취소 메서드가 있다.
- 다중 인스턴스 배포 전에는 청구 배치에 분산 락(ShedLock 등)이 필요하다.
- 결제창(authKey) 흐름은 브라우저가 필요해 자동 검증에서 제외했다. 카드 직접 입력 경로로 샌드박스를 검증했다.

## 8. 검증

- 단위: `UserItemTest`(이월 상한·사용 순서), `SubscriptionTest`(기간·유예·해지), `BillingKeyCipherTest`
- 통합(스텁): `backend/scripts/verify-payment.sh` (27 단계, 관리자 배치는 `ADMIN_PROMOTE_CMD` 설정 시) / Postman `docs/postman/BMA-84-payment.postman_collection.json`
- 토스 샌드박스: `backend/scripts/verify-payment-toss.sh` (`PAYMENT_GATEWAY=toss` 서버 대상)
