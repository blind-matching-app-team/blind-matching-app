# 토스페이먼츠 샌드박스 검증 절차 (BMA-23)

결제 로직을 만들기 **전에** 발급받은 테스트 키 조합이 실제로 동작하는지 확인한다.
빌링키 발급 → 자동결제 승인 → 취소 3단계를 애플리케이션 코드 없이 API 만으로 검증한다.

---

## 1. 먼저 알아둘 것

토스 문서에 **"자동결제는 리스크 검토 및 추가 계약 후 사용할 수 있습니다"** 라고 명시돼 있다.

개발자센터의 **API 개별 연동 키** 항목에 `자동결제(빌링)` 태그가 붙어 있으면
테스트 환경에서는 빌링 API 를 쓸 수 있다. 우리 계정은 태그가 확인됐다.
**단, 라이브 전환에는 별도 계약이 필요하다.** 이 문서의 검증은 테스트 환경 한정이다.

스크립트가 `NOT_SUPPORTED_METHOD` / `UNAUTHORIZED_KEY` / `FORBIDDEN_REQUEST` 를 받으면
계약 문의 안내를 함께 출력한다. 문의처는 1544-7772 다.

---

## 2. 키 확인

[개발자센터 > API 키](https://developers.tosspayments.com/my/api-keys)에서
**API 개별 연동 키**의 클라이언트 키와 시크릿 키를 확인한다.

| 구분 | 클라이언트 키 | 시크릿 키 |
| --- | --- | --- |
| 테스트 | `test_ck_...` | `test_sk_...` |
| 라이브 | `live_ck_...` | `live_sk_...` |

> 결제위젯 키는 중간이 `gck` / `gsk` 다. 이 프로젝트는 API 개별 연동(`ck`/`sk`)을 쓴다.

**두 키는 세트로 발급된다.** 세트가 아니거나 테스트와 라이브를 섞으면 `INVALID_API_KEY` 가 난다.

---

## 3. .env 설정

저장소 루트의 `.env.example` 을 복사해 `.env` 를 만들고 값을 채운다.
`.env` 는 `.gitignore` 대상이라 커밋되지 않는다. **시크릿 키는 절대 저장소에 넣지 않는다.**

```bash
PAYMENT_GATEWAY=stub
TOSS_CLIENT_KEY=test_ck_...
TOSS_SECRET_KEY=test_sk_...

# 카드 정보는 아래 "테스트 카드는 어떻게 준비하나" 참고
TOSS_TEST_CARD_NUMBER=5327120000000000
TOSS_TEST_CARD_EXPIRY_YEAR=30
TOSS_TEST_CARD_EXPIRY_MONTH=12
TOSS_TEST_CARD_IDENTITY=900101
TOSS_TEST_CARD_PASSWORD=00
TOSS_TEST_AMOUNT=1000
```

### 테스트 카드는 어떻게 준비하나

**토스는 테스트용 카드번호를 제공하지 않는다.** 문서에 "테스트용 국내 카드번호는 없어요"라고
명시돼 있다. 테스트 환경에서는 실제 카드 정보를 넣어도 **가상 승인이라 출금되지 않는다.**

다만 빌링키 발급은 요건이 더 느슨하다. **테스트 환경에서는 카드번호 앞 여섯 자리(BIN)만
유효하면 자동결제가 등록된다.** (라이브는 전체 번호가 유효해야 한다.)

그래서 전체 카드번호를 파일에 적을 필요가 없다. **본인 카드 앞 6자리 + 나머지 임의 숫자**를 쓴다.

| 항목 | 넣을 값 |
| --- | --- |
| `TOSS_TEST_CARD_NUMBER` | 본인 카드 앞 6자리(BIN) + 임의 숫자 10자리 |
| `TOSS_TEST_CARD_EXPIRY_YEAR` | 미래의 아무 두 자리 연도 (예: `30`) |
| `TOSS_TEST_CARD_EXPIRY_MONTH` | 두 자리 월 (예: `12`) |
| `TOSS_TEST_CARD_IDENTITY` | 생년월일 6자리(YYMMDD) 또는 사업자번호 10자리 |
| `TOSS_TEST_CARD_PASSWORD` | 아무 두 자리 (예: `00`) |

본인인증창이 뜨는 경우 인증번호는 `000000` 이다.

---

## 4. 실행

```bash
node backend/scripts/verify-toss-sandbox.js
```

의존성 설치가 필요 없다. Node 14 이상이면 그대로 돈다.

성공하면 이렇게 나온다.

```
[1/3] 빌링키 발급  POST /v1/billing/authorizations/card
  성공 (HTTP 200)
  billingKey : test_bil***abcd
[2/3] 자동결제 승인  POST /v1/billing/{billingKey}
  성공 (HTTP 200)
  상태       : DONE
  승인 금액  : 1,000원
[3/3] 결제 취소  POST /v1/payments/{paymentKey}/cancel
  성공 (HTTP 200)
  상태       : CANCELED

3단계 모두 성공했습니다. 테스트 키 조합이 정상 동작합니다.
```

승인 상태가 `DONE` 이 아니거나 승인 금액이 요청 금액과 다르면 실패로 처리한다.
개발자센터 > **테스트 결제내역**에서 출력된 `orderId` 로 대조할 수 있다.
테스트 환경이므로 실제 금액은 차감되지 않는다.

---

## 5. 실결제 방지 장치

기술적 제약사항("라이브 키와 혼용 금지")을 두 군데에서 강제한다.

**검증 스크립트** — `TOSS_SECRET_KEY` 가 `live_` 로 시작하면 **아무 요청도 보내지 않고** 즉시 중단한다.
`test_` 로 시작하지 않아도 중단한다. `TOSS_CLIENT_KEY` 가 테스트 키가 아니면 세트 불일치로 보고 중단한다.

**애플리케이션** — [TossApiKeyGuard](../src/main/java/com/bma/payment/config/TossApiKeyGuard.java)
가 기동 시점에 검증하고, 아래 경우 예외를 던져 기동을 막는다.

| 상황 | 결과 |
| --- | --- |
| `gateway=toss` 인데 키가 비어 있음 | 기동 중단 |
| 키가 `test_`/`live_` 둘 다 아님 | 기동 중단 |
| 클라이언트 키와 시크릿 키의 환경이 다름 | 기동 중단 |
| 운영 프로파일이 아닌데 `live_` 키가 설정됨 | 기동 중단 |
| 운영 프로파일인데 `test_` 키 | 경고 로그 |

키가 로그나 예외 메시지에 실릴 때는 앞 8자와 뒤 4자만 남기고 가린다.

> 참고: 테스트/라이브를 섞으면 결제가 되는 게 아니라 `INVALID_API_KEY` 로 실패한다.
> 즉 실질적 위험은 "혼용으로 인한 실결제"보다 **라이브 시크릿 키가 저장소나 로그에 남는 것**이다.

---

## 6. API 요약

인증은 전부 동일하다. **시크릿 키 뒤에 콜론을 붙여** Base64 로 인코딩한다.

```
Authorization: Basic {Base64(secretKey + ":")}
```

BOM 이 섞이면 값이 `77u/` 로 시작하며 인증에 실패한다.

| 단계 | 메서드 | 경로 | 필수 본문 |
| --- | --- | --- | --- |
| 빌링키 발급(카드 직접) | POST | `/v1/billing/authorizations/card` | `customerKey`, `cardNumber`, `cardExpirationYear`, `cardExpirationMonth`, `customerIdentityNumber`, `cardPassword` |
| 빌링키 발급(결제창) | POST | `/v1/billing/authorizations/issue` | `authKey`, `customerKey` |
| 자동결제 승인 | POST | `/v1/billing/{billingKey}` | `customerKey`, `amount`, `orderId`, `orderName` |
| 일반 결제 승인 | POST | `/v1/payments/confirm` | `paymentKey`, `orderId`, `amount` |
| 취소 | POST | `/v1/payments/{paymentKey}/cancel` | `cancelReason` (`cancelAmount` 생략 시 전액) |

호스트는 `https://api.tosspayments.com` 다.

**기타 헤더**

- `Idempotency-Key` — UUID, 300자 제한, 15일 유효. POST 에만 적용된다. 취소에 권장.
- 에러 시뮬레이션 헤더 — 테스트 키로 실패 케이스를 재현할 수 있다.
  문서에 `TossPayments-Test-Code` 와 `X-Toss-Topaz` 두 이름이 모두 등장하므로,
  쓰기 전에 [API 테스트](https://docs.tosspayments.com/reference/test) 문서에서 현재 이름을 확인한다.

`orderId` 는 6~64자이며 영문/숫자/`-`/`_` 만 쓸 수 있다.
`customerKey` 는 2~300자이며 UUID 가 권장된다.

---

## 7. 검증 후 남은 일 (Phase 3)

이 문서는 **키 검증**까지만 다룬다. 실제 결제 연동에는 다음이 더 필요하다.

- `PaymentGateway` 인터페이스에 빌링키 발급과 취소 메서드 추가
  (현재는 `approve()` 와 `verifyWebhookSignature()` 뿐이다)
- `app.payment.gateway=toss` 일 때 등록되는 `TossPaymentGateway` 구현체
- 결제 취소 엔드포인트 (`PaymentController` 에 없다)
- 정기 청구 스케줄러 — 토스는 자체 스케줄링을 제공하지 않는다

### 스키마 선결 과제

현재 스키마로는 자동결제를 구현할 수 없다. 연동 전에 마이그레이션이 필요하다.

1. **`customerKey` 를 저장할 컬럼이 없다.** 자동결제 승인에 필수인데 32개 테이블 어디에도 없다.
   빌링키가 노출돼도 짝이 되는 `customerKey` 를 모르면 결제가 불가능하므로 둘은 쌍으로 보관해야 한다.
2. **빌링키가 결제 원장(`PY_PAYMENT.BILLING_KEY_ENC`)에 붙어 있다.**
   빌링키는 결제 1건이 아니라 사용자 1명에 귀속되어 매 청구 주기마다 재사용하는 값이다.
   `PY_PRODUCT.PRODUCT_TYPE` 에 `SUBSCRIPTION` 이 있는 만큼 사용자 단위 보관으로 옮겨야 한다.

스키마 변경은 반드시 Flyway 마이그레이션으로만 한다. [DB 마이그레이션 가이드](DB_MIGRATION.md) 참고.

---

## 8. 출처

- [코어 API 레퍼런스](https://docs.tosspayments.com/reference)
- [API 키](https://docs.tosspayments.com/reference/using-api/api-keys)
- [인증 및 기타 헤더 설정](https://docs.tosspayments.com/reference/using-api/authorization)
- [자동결제(빌링) API로 연동하기](https://docs.tosspayments.com/guides/v2/billing/integration-api)
- [자동결제(빌링) 결제창 연동하기](https://docs.tosspayments.com/guides/v2/billing/integration)
