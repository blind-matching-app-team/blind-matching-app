#!/usr/bin/env bash
# BMA-84 Postman 컬렉션(docs/postman/BMA-84-payment.postman_collection.json)을 curl 로 재현한다.
# 공통컴포넌트 CM-13~17: 상품 목록, 소모형 결제(결제창/저장카드), 구독 등록·상태·해지, 이용권 잔여,
# 일일 무료 매칭 기회, 재매칭권, 구독 청구 배치(이월·소멸), 웹훅.
# 스텁 게이트웨이(PAYMENT_GATEWAY=stub) 기준. 토스 샌드박스는 verify-payment-toss.sh 참고.
#   BASE=http://localhost:8080 bash backend/scripts/verify-payment.sh
# 청구 배치 단계(관리자)는 ADMIN_PROMOTE_CMD 가 있을 때만 실행한다. 예)
#   ADMIN_PROMOTE_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e"
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma84-a-$TS@example.com"; EMAIL_B="bma84-b-$TS@example.com"
PW="Passw0rd!23"; NICK_A="결제A$((TS % 100000))"; NICK_B="결제B$((TS % 100000))"
ORD1="ord-a-$TS"; PK1="pk-$TS"
PASS=0; FAIL=0; SKIP=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
hasF() { echo "$J" | grep -qF -- "$1"; }
item_bal() { echo "$J" | grep -oE "\{\"itemType\":\"$1\"[^}]*\}" | grep -oE "\"$2\":[0-9]+" | head -1 | cut -d: -f2; }
free() { echo "$J" | grep -oE '"freeChancesToday":\{[^}]*\}' | grep -oE "\"$1\":[0-9]+" | head -1 | cut -d: -f2; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
login() { req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"; }
# S15 본인인증(BMA-79): 매칭 대기열 진입 전 필수. 스텁 인증사에 성인 결과를 보내 완료시킨다.
verify_id() { local t=$1 tag=$2; req POST /api/v1/verification/identity/request "$t"; local tx; tx=$(field transactionId); req POST /api/v1/verification/identity/confirm "$t" "{\"transactionId\":\"$tx\",\"providerPayload\":{\"result\":{\"name\":\"홍길동\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"phoneNumber\":\"01000000000\",\"ci\":\"ci-$tag\"}}}"; }
PAY=/api/v1/payments; ITEMS=$PAY/me/items; Q=/api/v1/matching/queue

step "1. A 가입/로그인/프로필 → 상품 목록 (CM-14/15/16): 소모형 2 + 구독 1, 가격·월 지급 구성"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; USER_A=$(field userId); login "$EMAIL_A" "$PW"; TOK_A=$(field accessToken)
req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\"}"; verify_id "$TOK_A" "$EMAIL_A"
req GET $PAY/products "$TOK_A"
check "200, 상품 3개, MATCH_CHANCE/REMATCH_TICKET=ITEM, PREMIUM_MONTHLY=SUBSCRIPTION 9900, monthlyGrants 3/2, carryOver 2" "" "$([ "$CODE" = 200 ] && [ "$(echo "$J" | grep -o '"productCode"' | wc -l | tr -d ' ')" = 3 ] && has '"productCode":"MATCH_CHANCE","productName":"매칭기회 추가 1회","productType":"ITEM","price":1000' && has '"productCode":"REMATCH_TICKET"' && has '"productCode":"PREMIUM_MONTHLY","productName":"정밀매칭 구독","productType":"SUBSCRIPTION","price":9900' && has '"monthlyGrants":{"MATCH_CHANCE":3,"REMATCH_TICKET":2}' && has '"carryOverMonths":2' && echo true)"

step "2. 내 이용권 현황 초기값: 잔여 0/0, 무료 기회 3/0/3, 구독 없음"
req GET $ITEMS "$TOK_A"
check "MATCH_CHANCE 0, REMATCH_TICKET 0, free limit=3 used=0 remaining=3, subscribed=false" "" "$([ "$CODE" = 200 ] && [ "$(item_bal MATCH_CHANCE total)" = 0 ] && [ "$(item_bal REMATCH_TICKET total)" = 0 ] && [ "$(free limit)" = 3 ] && [ "$(free used)" = 0 ] && [ "$(free remaining)" = 3 ] && [ "$(field subscribed)" = false ] && echo true)"

step "3. 대기열 진입 → entrySource=FREE, 대기 중 재진입은 같은 항목(무소모)"
req POST $Q "$TOK_A"; Q1=$(field queueId); S1=$(field entrySource); req POST $Q "$TOK_A"; Q1B=$(field queueId); req GET $ITEMS "$TOK_A"
check "FREE, 같은 queueId, used=1 remaining=2" "" "$([ -n "$Q1" ] && [ "$S1" = FREE ] && [ "$Q1B" = "$Q1" ] && [ "$(free used)" = 1 ] && [ "$(free remaining)" = 2 ] && echo true)"

step "4. 취소 후 재진입 ×2 → 무료 3회 모두 사용"
req DELETE $Q "$TOK_A"; req POST $Q "$TOK_A"; S2=$(field entrySource); req DELETE $Q "$TOK_A"; req POST $Q "$TOK_A"; S3=$(field entrySource); req DELETE $Q "$TOK_A"; req GET $ITEMS "$TOK_A"
check "FREE·FREE, used=3 remaining=0" "" "$([ "$S2" = FREE ] && [ "$S3" = FREE ] && [ "$(free used)" = 3 ] && [ "$(free remaining)" = 0 ] && echo true)"

step "5. 4번째 진입 → 409 PAY_006 (매칭 기회 소진 → 구매 모달 소모형 탭)"
req POST $Q "$TOK_A"
check "409 PAY_006" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = PAY_006 ] && echo true)"

step "6. 소모형 결제 - 결제창 승인 (CM-15): MATCH_CHANCE, orderId+paymentKey → DONE, 잔여 +1"
req POST $PAY/consumable "$TOK_A" "{\"productCode\":\"MATCH_CHANCE\",\"orderId\":\"$ORD1\",\"paymentKey\":\"$PK1\"}"; PAY1=$(field paymentId)
check "200, status=DONE, payMethod=WIDGET, kind=CONSUMABLE, amount 1000, MATCH_CHANCE purchased=1 total=1" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = DONE ] && [ "$(field payMethod)" = WIDGET ] && [ "$(field paymentKind)" = CONSUMABLE ] && has '"amount":1000' && [ "$(item_bal MATCH_CHANCE purchased)" = 1 ] && [ "$(item_bal MATCH_CHANCE total)" = 1 ] && echo true)"

step "7. 같은 orderId 재요청 → 멱등(같은 결제, 이용권 중복 지급 없음)"
req POST $PAY/consumable "$TOK_A" "{\"productCode\":\"MATCH_CHANCE\",\"orderId\":\"$ORD1\",\"paymentKey\":\"$PK1\"}"
check "200, paymentId 동일, MATCH_CHANCE total=1" "" "$([ "$CODE" = 200 ] && [ "$(field paymentId)" = "$PAY1" ] && [ "$(item_bal MATCH_CHANCE total)" = 1 ] && echo true)"

step "8. B 가입 → B 가 A 의 orderId 로 요청 → 404 PAY_005"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; USER_B=$(field userId); login "$EMAIL_B" "$PW"; TOK_B=$(field accessToken)
req POST $PAY/consumable "$TOK_B" "{\"productCode\":\"MATCH_CHANCE\",\"orderId\":\"$ORD1\",\"paymentKey\":\"$PK1\"}"
check "404 PAY_005" "" "$([ "$CODE" = 404 ] && [ "$(field code)" = PAY_005 ] && echo true)"

step "9. 결제창 승인 실패(스텁: fail- 키) → 400 PAY_002, 결제 내역에 FAILED 로 남음"
req POST $PAY/consumable "$TOK_A" "{\"productCode\":\"REMATCH_TICKET\",\"orderId\":\"ord-fail-$TS\",\"paymentKey\":\"fail-$TS\"}"; C1=$CODE; RC=$(field code); req GET $PAY/me "$TOK_A"
check "400 PAY_002, 최신 내역 status=FAILED, REMATCH 지급 없음" "" "$([ "$C1" = 400 ] && [ "$RC" = PAY_002 ] && [ "$CODE" = 200 ] && [ "$(field status)" = FAILED ] && echo true)"

step "10. 무료 소진 상태에서 진입 → entrySource=ITEM (매칭기회 이용권 1개 소모)"
req POST $Q "$TOK_A"; S4=$(field entrySource); req GET $ITEMS "$TOK_A"
check "ITEM, MATCH_CHANCE total=0" "" "$([ "$S4" = ITEM ] && [ "$(item_bal MATCH_CHANCE total)" = 0 ] && echo true)"

step "11. 취소 후 진입 → 다시 409 PAY_006"
req DELETE $Q "$TOK_A"; req POST $Q "$TOK_A"
check "409 PAY_006" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = PAY_006 ] && echo true)"

step "12. 저장 카드 없이 소모형 청구 → 409 PAY_008 / 결제 수단 없이 구독 → 400 PAY_011"
req POST $PAY/consumable "$TOK_A" '{"productCode":"MATCH_CHANCE"}'; C1=$CODE; R1=$(field code); req POST $PAY/subscription "$TOK_A" '{}'
check "409 PAY_008, 400 PAY_011" "" "$([ "$C1" = 409 ] && [ "$R1" = PAY_008 ] && [ "$CODE" = 400 ] && [ "$(field code)" = PAY_011 ] && echo true)"

step "13. 구독 등록 - 카드 거부(스텁: 0000 시작) → 400 PAY_002, 구독 없음"
req POST $PAY/subscription "$TOK_A" '{"card":{"cardNumber":"0000000000000000","expiryYear":"30","expiryMonth":"12","identityNumber":"900101","password":"00"}}'; C1=$CODE; R1=$(field code); req GET $PAY/subscription "$TOK_A"
check "400 PAY_002, subscribed=false subscription=null" "" "$([ "$C1" = 400 ] && [ "$R1" = PAY_002 ] && [ "$(field subscribed)" = false ] && has '"subscription":null' && echo true)"

step "14. 구독 등록 (CM-17): 카드 직접 등록 → ACTIVE, 첫 달 청구, 다음 청구일"
req POST $PAY/subscription "$TOK_A" '{"card":{"cardNumber":"4854797481503803","expiryYear":"30","expiryMonth":"12","identityNumber":"900101","password":"00"}}'; SUB_ID=$(field subscriptionId); NBD=$(field nextBillingDate); PEND=$(field currentPeriodEnd); PSTART=$(field currentPeriodStart)
check "200, ACTIVE, PREMIUM_MONTHLY 9900, 카드 ****3803, 기간 오늘~, nextBillingDate=종료+1일, monthlyGrants" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = ACTIVE ] && [ "$(field productCode)" = PREMIUM_MONTHLY ] && has '"price":9900' && hasF '"numberMasked":"****-****-****-3803"' && [ "$PSTART" = "$(date +%F)" ] && [ "$NBD" = "$(date -d "$PEND + 1 day" +%F)" ] && has '"monthlyGrants":{"MATCH_CHANCE":3,"REMATCH_TICKET":2}' && echo true)"

step "15. 구독 직후 지급: MATCH_CHANCE 지급 3, REMATCH_TICKET 지급 2, subscribed=true / 구독 상태 조회"
req GET $ITEMS "$TOK_A"; I_OK=$([ "$(item_bal MATCH_CHANCE granted)" = 3 ] && [ "$(item_bal MATCH_CHANCE total)" = 3 ] && [ "$(item_bal REMATCH_TICKET granted)" = 2 ] && [ "$(field subscribed)" = true ] && echo true); req GET $PAY/subscription "$TOK_A"
check "지급 3/2, subscribed=true, GET subscription ACTIVE·id 일치" "" "$([ "$I_OK" = true ] && [ "$CODE" = 200 ] && [ "$(field subscribed)" = true ] && [ "$(field subscriptionId)" = "$SUB_ID" ] && [ "$(field status)" = ACTIVE ] && echo true)"

step "16. 이미 구독 중 재등록 → 409 PAY_009"
req POST $PAY/subscription "$TOK_A" '{}'
check "409 PAY_009" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = PAY_009 ] && echo true)"

step "17. 소모형 - 저장 카드 청구 (paymentKey 없이): REMATCH_TICKET → DONE BILLING, 구매분 +1"
req POST $PAY/consumable "$TOK_A" '{"productCode":"REMATCH_TICKET"}'
check "200, DONE, payMethod=BILLING, amount 2000, REMATCH purchased=1 granted=2 total=3" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = DONE ] && [ "$(field payMethod)" = BILLING ] && has '"amount":2000' && [ "$(item_bal REMATCH_TICKET purchased)" = 1 ] && [ "$(item_bal REMATCH_TICKET granted)" = 2 ] && [ "$(item_bal REMATCH_TICKET total)" = 3 ] && echo true)"

step "18. 매칭 성사 후 A 재매칭권 사용 (S5-12): 매칭 종료 + 즉시 대기열(REMATCH) + 지급분부터 차감, B 에게 종료 알림"
req PUT /api/v1/users/me/profile "$TOK_B" "{\"nickname\":\"$NICK_B\",\"birthDate\":\"1997-07-07\",\"genderCode\":\"F\",\"regionCode\":\"SEOUL_JUNG\"}"
req POST /api/v1/matching/actions "$TOK_B" "{\"targetUserId\":$USER_A,\"actionType\":\"LIKE\"}"; req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"; MATCH_ID=$(field matchId)
req POST "/api/v1/matches/$MATCH_ID/rematch" "$TOK_A"; C1=$CODE; R_OK=$([ "$(field endedMatchId)" = "$MATCH_ID" ] && [ "$(field entrySource)" = REMATCH ] && [ "$(field remainingRematchTickets)" = 2 ] && echo true)
req GET $ITEMS "$TOK_A"; I_OK=$([ "$(item_bal REMATCH_TICKET granted)" = 1 ] && [ "$(item_bal REMATCH_TICKET purchased)" = 1 ] && echo true); req GET /api/v1/matches/current "$TOK_B"; B_OK=$([ "$(field hasMatch)" = false ] && echo true); req GET "/api/v1/notifications?page=0&size=3" "$TOK_B"
check "200, endedMatchId 일치, queue REMATCH, 남은 재매칭권 2(지급 1·구매 1), B hasMatch=false, B 최신 알림 MATCH_ENDED" "" "$([ -n "$MATCH_ID" ] && [ "$C1" = 200 ] && [ "$R_OK" = true ] && [ "$I_OK" = true ] && [ "$B_OK" = true ] && [ "$(field eventCode)" = MATCH_ENDED ] && echo true)"

step "19. 끝난 매칭에 재매칭 → 404 MATCH_003 / 재매칭권 없는 B 가 재성사된 매칭에 재매칭 → 409 PAY_007"
req POST "/api/v1/matches/$MATCH_ID/rematch" "$TOK_A"; C1=$CODE; R1=$(field code)
req DELETE $Q "$TOK_A"; req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"; MATCH2=$(field matchId); req POST "/api/v1/matches/${MATCH2:-$MATCH_ID}/rematch" "$TOK_B"
check "404 MATCH_003, 409 PAY_007" "" "$([ "$C1" = 404 ] && [ "$R1" = MATCH_003 ] && [ "$CODE" = 409 ] && [ "$(field code)" = PAY_007 ] && echo true)"

if [ -n "$ADMIN_PROMOTE_CMD" ]; then
  eval "$ADMIN_PROMOTE_CMD \"UPDATE US_USER SET USER_ROLE='ADMIN' WHERE EMAIL='$EMAIL_A'\"" >/dev/null 2>&1
  login "$EMAIL_A" "$PW"; TOK_ADM=$(field accessToken)
  step "20. 청구 배치 1회차 (asOf=다음 청구일): 갱신 1, 기간 이월, 지급분 누적(3+3=6, 1+2=3)"
  req POST "/api/v1/admin/payments/subscriptions/renew?asOf=$NBD" "$TOK_ADM"; C1=$CODE; RN=$(field renewed); DUE=$(field due)
  req GET $PAY/subscription "$TOK_A"; NBD2=$(field nextBillingDate); PEND2=$(field currentPeriodEnd); S_OK=$([ "$(field currentPeriodStart)" = "$NBD" ] && [ "$NBD2" \> "$NBD" ] && [ "$(field status)" = ACTIVE ] && echo true)
  req GET $ITEMS "$TOK_A"
  check "200 due≥1 renewed≥1(배치는 전체 구독 대상), 기간 시작=옛 청구일, MATCH_CHANCE granted=6, REMATCH granted=3 total=4" "" "$([ "$C1" = 200 ] && [ "${DUE:-0}" -ge 1 ] && [ "${RN:-0}" -ge 1 ] && [ "$S_OK" = true ] && [ "$(item_bal MATCH_CHANCE granted)" = 6 ] && [ "$(item_bal REMATCH_TICKET granted)" = 3 ] && [ "$(item_bal REMATCH_TICKET total)" = 4 ] && echo true)"

  step "21. 청구 배치 2회차: 이월 상한(2개월치) 초과분 소멸 → MATCH_CHANCE 6 유지, REMATCH 4 (각 3·1 소멸)"
  req POST "/api/v1/admin/payments/subscriptions/renew?asOf=$NBD2" "$TOK_ADM"; RN=$(field renewed); req GET $ITEMS "$TOK_A"
  check "renewed≥1, MATCH_CHANCE granted=6 total=6, REMATCH granted=4 total=5" "" "$([ "${RN:-0}" -ge 1 ] && [ "$(item_bal MATCH_CHANCE granted)" = 6 ] && [ "$(item_bal MATCH_CHANCE total)" = 6 ] && [ "$(item_bal REMATCH_TICKET granted)" = 4 ] && [ "$(item_bal REMATCH_TICKET total)" = 5 ] && echo true)"

  step "22. 같은 기준일로 배치 재실행 → 청구 대상 0 (멱등), 결제 내역에 구독 청구 3건(첫 달+2회)"
  req POST "/api/v1/admin/payments/subscriptions/renew?asOf=$NBD2" "$TOK_ADM"; D2=$(field due); req GET $PAY/me "$TOK_A"
  check "due=0, SUBSCRIPTION 결제 3건" "" "$([ "$D2" = 0 ] && [ "$(echo "$J" | grep -o '"paymentKind":"SUBSCRIPTION"' | wc -l | tr -d ' ')" = 3 ] && echo true)"

  step "23. 해지 → CANCELED, nextBillingDate=null, 기간 내 subscribed 유지 / 재해지 멱등"
  req DELETE $PAY/subscription "$TOK_A"; C1=$CODE; ST=$(field status); N1=$(has '"nextBillingDate":null' && echo true); req DELETE $PAY/subscription "$TOK_A"; C2=$CODE; req GET $PAY/subscription "$TOK_A"; PEND3=$(field currentPeriodEnd)
  check "200 CANCELED nextBillingDate=null, 재해지 200, subscribed=true" "" "$([ "$C1" = 200 ] && [ "$ST" = CANCELED ] && [ "$N1" = true ] && [ "$C2" = 200 ] && [ "$(field subscribed)" = true ] && [ "$(field status)" = CANCELED ] && echo true)"

  step "24. 기간 종료 다음 날 배치 → EXPIRED, subscribed=false / 해지 재요청 → 404 PAY_010"
  req POST "/api/v1/admin/payments/subscriptions/renew?asOf=$(date -d "$PEND3 + 1 day" +%F)" "$TOK_ADM"; EX=$(field expired); req GET $PAY/subscription "$TOK_A"; E_OK=$([ "$(field subscribed)" = false ] && [ "$(field status)" = EXPIRED ] && echo true); req DELETE $PAY/subscription "$TOK_A"
  check "expired=1, EXPIRED·subscribed=false, 404 PAY_010" "" "$([ "$EX" = 1 ] && [ "$E_OK" = true ] && [ "$CODE" = 404 ] && [ "$(field code)" = PAY_010 ] && echo true)"

  step "25. 일반 사용자의 배치 실행 → 403"
  req POST "/api/v1/admin/payments/subscriptions/renew" "$TOK_B"
  check "403" "" "$([ "$CODE" = 403 ] && echo true)"
else
  for n in 20 21 22 23 24 25; do skip "$n. 청구 배치(관리자)" "ADMIN_PROMOTE_CMD 미설정"; done
fi

step "26. 웹훅: 결제 취소 통보 → 결제 내역에 CANCELED 반영 (서명 시크릿 미설정 시 검증 생략)"
req POST $PAY/webhook "" "{\"eventId\":\"evt-$TS\",\"paymentKey\":\"$PK1\",\"status\":\"CANCELED\"}"; C1=$CODE; req GET $PAY/me "$TOK_A"
check "200, orderId=$ORD1 결제 status=CANCELED" "" "$([ "$C1" = 200 ] && echo "$J" | grep -o "{[^{}]*\"orderId\":\"$ORD1\"[^{}]*}" | grep -q '"status":"CANCELED"' && echo true)"

step "27. 인증 없이 상품/이용권/구독 → 401"
req GET $PAY/products ""; C1=$CODE; req GET $ITEMS ""; C2=$CODE; req POST $PAY/subscription "" '{}'
check "401 ×3" "" "$([ "$C1" = 401 ] && [ "$C2" = 401 ] && [ "$CODE" = 401 ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
