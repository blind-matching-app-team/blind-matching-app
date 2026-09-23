#!/usr/bin/env bash
# BMA-84 토스페이먼츠 샌드박스 연동 검증. 서버가 PAYMENT_GATEWAY=toss + 테스트 키(test_ck/test_sk)로 떠 있어야 한다.
# 테스트 환경은 가상 승인이라 출금이 없다. 빌링키 발급은 BIN 6자리만 유효하면 되므로 실제 카드가 필요 없다
# (docs/TOSS_SANDBOX_VERIFICATION.md 3절). 결과는 개발자센터 > 테스트 결제내역에서 orderId 로 대조할 수 있다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-payment-toss.sh
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
CARD=${TOSS_TEST_CARD_NUMBER:-4854797481503803}
EMAIL_A="bma84t-$TS@example.com"; PW="Passw0rd!23"; NICK_A="토스$((TS % 100000))"
PASS=0; FAIL=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
item_bal() { echo "$J" | grep -oE "\{\"itemType\":\"$1\"[^}]*\}" | grep -oE "\"$2\":[0-9]+" | head -1 | cut -d: -f2; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
step() { echo "[$1]"; }
PAY=/api/v1/payments

step "1. 가입/로그인/프로필"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; USER_A=$(field userId)
req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; TOK_A=$(field accessToken)
req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\"}"
check "프로필 200" "" "$([ "$CODE" = 200 ] && [ -n "$TOK_A" ] && echo true)"

step "2. 빌링키 발급 거부: 존재하지 않는 BIN → 400 PAY_002 (토스 오류 코드 포함), 구독 없음"
req POST $PAY/subscription "$TOK_A" '{"card":{"cardNumber":"1234567890123456","expiryYear":"30","expiryMonth":"12","identityNumber":"900101","password":"00"}}'; C1=$CODE; R1=$(field code); MSG=$(field message); req GET $PAY/subscription "$TOK_A"
check "400 PAY_002, 메시지에 토스 코드, subscription=null  [$MSG]" "" "$([ "$C1" = 400 ] && [ "$R1" = PAY_002 ] && echo "$MSG" | grep -q '(' && has '"subscription":null' && echo true)"

step "3. 구독 등록: 카드 BIN 으로 빌링키 발급 → 첫 달 9,900원 자동결제 승인 (토스 샌드박스)"
req POST $PAY/subscription "$TOK_A" "{\"card\":{\"cardNumber\":\"$CARD\",\"expiryYear\":\"30\",\"expiryMonth\":\"12\",\"identityNumber\":\"900101\",\"password\":\"00\"}}"; SUB_ID=$(field subscriptionId); CO=$(field company); NM=$(field numberMasked)
check "200 ACTIVE, 카드사·마스킹 번호 존재  [$CO $NM]" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = ACTIVE ] && [ -n "$SUB_ID" ] && [ -n "$NM" ] && [ "$NM" != null ] && echo true)"

step "4. 결제 내역: 구독 첫 달 DONE, BILLING, 토스 paymentKey(orderId 로 개발자센터 대조)"
req GET $PAY/me "$TOK_A"; OID1=$(field orderId)
check "SUBSCRIPTION DONE BILLING 9900  [orderId=$OID1]" "" "$([ "$CODE" = 200 ] && [ "$(field paymentKind)" = SUBSCRIPTION ] && [ "$(field status)" = DONE ] && [ "$(field payMethod)" = BILLING ] && has '"amount":9900' && echo true)"

step "5. 소모형 저장카드 청구: REMATCH_TICKET 2,000원 자동결제 → 잔여 반영"
req POST $PAY/consumable "$TOK_A" '{"productCode":"REMATCH_TICKET"}'; OID2=$(field orderId)
check "200 DONE BILLING 2000, REMATCH total=3(지급 2+구매 1)  [orderId=$OID2]" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = DONE ] && [ "$(field payMethod)" = BILLING ] && has '"amount":2000' && [ "$(item_bal REMATCH_TICKET total)" = 3 ] && echo true)"

step "6. 소모형 결제창 승인: 위조 paymentKey → 토스 거부 → 400 PAY_002, 이용권 미지급"
req POST $PAY/consumable "$TOK_A" "{\"productCode\":\"MATCH_CHANCE\",\"orderId\":\"ord-forged-$TS\",\"paymentKey\":\"forged_$TS\"}"; C1=$CODE; R1=$(field code); req GET $PAY/me/items "$TOK_A"
check "400 PAY_002, MATCH_CHANCE total=3(지급분만)" "" "$([ "$C1" = 400 ] && [ "$R1" = PAY_002 ] && [ "$(item_bal MATCH_CHANCE total)" = 3 ] && echo true)"

step "7. 구독 해지 → CANCELED, 다음 청구 없음"
req DELETE $PAY/subscription "$TOK_A"
check "200 CANCELED nextBillingDate=null" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = CANCELED ] && has '"nextBillingDate":null' && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
