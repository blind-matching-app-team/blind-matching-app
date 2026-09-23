#!/usr/bin/env bash
# BMA-66 Postman 컬렉션(docs/postman/BMA-66-matching-queue.postman_collection.json)을 curl 로 재현한다.
# S9 매칭대기: 진입 → 대기 상태 폴링 → 상호 선호 조건·차단·매칭 참여 여부로 짝 찾기(FIFO) → 즉시 성사 →
# 타임아웃(5분) 시 TIMEOUT + 이용권 환불 + 무료 기회 미차감 → 다시 시도/취소 → 재매칭권 즉시 재배정.
#   BASE=http://localhost:8080 bash backend/scripts/verify-queue.sh
# 타임아웃 시나리오는 DB_EXEC_CMD 가 있을 때만 실행한다(만료 시각을 과거로 돌린다). 예)
#   DB_EXEC_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e"
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
PW="Passw0rd!23"
PASS=0; FAIL=0; SKIP=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
free() { echo "$J" | grep -oE '"freeChancesToday":\{[^}]*\}' | grep -oE "\"$1\":[0-9]+" | head -1 | cut -d: -f2; }
item_bal() { echo "$J" | grep -oE "\{\"itemType\":\"$1\"[^}]*\}" | grep -oE "\"$2\":[0-9]+" | head -1 | cut -d: -f2; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
Q=/api/v1/matching/queue
# 가입 + 로그인 + 프로필 + 선호조건. 인자: 이름 성별 생년 지역 선호JSON → TOK_<이름>, USER_<이름>
mk() { local n=$1 g=$2 y=$3 r=$4 p=$5; local e="bma66-$n-$TS@example.com"
  req POST /api/v1/auth/signup "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; eval "USER_$n=$(field userId)"
  req POST /api/v1/auth/login "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; local t; t=$(field accessToken); eval "TOK_$n=$t"
  req PUT /api/v1/users/me/profile "$t" "{\"nickname\":\"대기$n$((TS % 10000))\",\"birthDate\":\"$y-05-05\",\"genderCode\":\"$g\",\"regionCode\":\"$r\"}"
  req PUT /api/v1/users/me/preference "$t" "$p"; [ "$CODE" = 200 ] || PREF_FAIL=$((PREF_FAIL+1)); }
PREF_FAIL=0
now_iso() { date +%s; }

step "1. 계정 준비: A(남,31,강남,선호 여) C(남,36,강남,선호 여) → 대기 상태 없음(NONE)"
mk A M 1995 SEOUL_GANGNAM '{"preferredRegionCode":"SEOUL","preferredGenderCode":"F"}'; mk C M 1990 SEOUL_GANGNAM '{"preferredRegionCode":"SEOUL","preferredGenderCode":"F"}'
req GET $Q "$TOK_A"
check "선호조건 저장 200, status=NONE, queueId=null" "" "$([ "$PREF_FAIL" = 0 ] && [ "$CODE" = 200 ] && [ "$(field status)" = NONE ] && has '"queueId":null' && echo true)"

step "2. A 진입 (S9) → WAITING, FREE, 남은 시간 ≤ 300초, 만료 예정 시각 존재"
req POST $Q "$TOK_A"; QA=$(field queueId); REM=$(field remainingSeconds)
check "200 WAITING, entrySource=FREE, remainingSeconds 1~300, expiresAt, matchId=null" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = WAITING ] && [ "$(field entrySource)" = FREE ] && [ "$REM" -ge 1 ] && [ "$REM" -le 300 ] && [ -n "$(field expiresAt)" ] && has '"matchId":null' && echo true)"

step "3. 대기 상태 폴링 → WAITING, 같은 queueId, 경과 시간 ≥ 0"
req GET $Q "$TOK_A"
check "200 WAITING queueId 동일, waitedSeconds≥0" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = WAITING ] && [ "$(field queueId)" = "$QA" ] && [ "$(field waitedSeconds)" -ge 0 ] && echo true)"

step "4. C 진입 (남, 여 선호) → A 와는 서로 조건 불일치 → 둘 다 WAITING"
req POST $Q "$TOK_C"; QC=$(field queueId); S1=$(field status); req GET $Q "$TOK_A"
check "C WAITING, A WAITING" "" "$([ "$S1" = WAITING ] && [ "$(field status)" = WAITING ] && echo true)"

step "5. D 진입 (여, 선호 남 ≤34세) → 36세 C 제외, 먼저 들어온 31세 A 와 즉시 성사 (상호 조건 + FIFO)"
mk D F 1996 SEOUL_JUNG '{"preferredRegionCode":"SEOUL","preferredGenderCode":"M","maxAge":34}'; req POST $Q "$TOK_D"; MATCH1=$(field matchId); ROOM1=$(field chatRoomId)
check "200 MATCHED, partnerUserId=A, matchId·chatRoomId 존재" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = MATCHED ] && [ "$(field partnerUserId)" = "$USER_A" ] && [ -n "$MATCH1" ] && [ "$MATCH1" != null ] && [ -n "$ROOM1" ] && [ "$ROOM1" != null ] && echo true)"

step "6. A 폴링 → MATCHED 같은 매칭, remaining 0 / C 폴링 → 여전히 WAITING"
req GET $Q "$TOK_A"; A_OK=$([ "$(field status)" = MATCHED ] && [ "$(field matchId)" = "$MATCH1" ] && [ "$(field chatRoomId)" = "$ROOM1" ] && [ "$(field partnerUserId)" = "$USER_D" ] && [ "$(field remainingSeconds)" = 0 ] && echo true); req GET $Q "$TOK_C"
check "A MATCHED(matchId/chatRoomId/partner=D), C WAITING" "" "$([ "$A_OK" = true ] && [ "$(field status)" = WAITING ] && echo true)"

step "7. 성사 부수효과: A 현재 매칭 hasMatch=true·상대 D, 최신 알림 MATCH_CREATED (S7-11 → S10)"
req GET /api/v1/matches/current "$TOK_A"; M_OK=$([ "$(field hasMatch)" = true ] && [ "$(field matchId)" = "$MATCH1" ] && [ "$(field partnerUserId)" = "$USER_D" ] && echo true); req GET "/api/v1/notifications?page=0&size=3" "$TOK_A"
check "hasMatch=true partner=D, MATCH_CREATED" "" "$([ "$M_OK" = true ] && [ "$(field eventCode)" = MATCH_CREATED ] && echo true)"

step "8. 차단 관계 제외: B(여, 선호 남)가 C 를 차단하고 진입 → C 와 맺어지지 않음 → WAITING"
mk B F 1997 SEOUL_JUNG '{"preferredRegionCode":"SEOUL","preferredGenderCode":"M"}'; req POST "/api/v1/users/$USER_C/block" "$TOK_B" '{"reason":"test"}'; C1=$CODE; req POST $Q "$TOK_B"; QB=$(field queueId)
check "차단 200, B WAITING" "" "$([ "$C1" = 200 ] && [ "$CODE" = 200 ] && [ "$(field status)" = WAITING ] && echo true)"

step "9. 지역 조건: E(여, 중구, 선호 남 + 서울 전체) 진입 → 강남(서울 하위)의 C 와 성사 (B 는 여라 제외)"
mk E F 1998 SEOUL_JUNG '{"preferredRegionCode":"SEOUL","preferredGenderCode":"M"}'; req POST $Q "$TOK_E"
check "200 MATCHED partner=C" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = MATCHED ] && [ "$(field partnerUserId)" = "$USER_C" ] && echo true)"

step "10. 매칭 참여 꺼진 사용자 진입 → 409 MATCH_006, 켜면 B 와 성사"
mk F M 1999 SEOUL_JUNG '{"preferredRegionCode":"SEOUL","preferredGenderCode":"F","matchingEnabled":false}'; req POST $Q "$TOK_F"; C1=$CODE; R1=$(field code)
req PUT /api/v1/users/me/preference "$TOK_F" '{"preferredRegionCode":"SEOUL","preferredGenderCode":"F","matchingEnabled":true}'; req POST $Q "$TOK_F"
check "409 MATCH_006 → 200 MATCHED partner=B" "" "$([ "$C1" = 409 ] && [ "$R1" = MATCH_006 ] && [ "$CODE" = 200 ] && [ "$(field status)" = MATCHED ] && [ "$(field partnerUserId)" = "$USER_B" ] && echo true)"

step "11. 대기 중 재진입은 같은 항목(무소모) / 취소 → NONE (S9-05 대기 취소)"
mk G M 1994 SEOUL_GANGNAM '{"preferredRegionCode":"SEOUL","preferredGenderCode":"F"}'; req POST $Q "$TOK_G"; QG=$(field queueId); req POST $Q "$TOK_G"; QG2=$(field queueId); req DELETE $Q "$TOK_G"; C1=$CODE; req GET $Q "$TOK_G"
check "같은 queueId, 취소 200, 조회 NONE" "" "$([ -n "$QG" ] && [ "$QG2" = "$QG" ] && [ "$C1" = 200 ] && [ "$(field status)" = NONE ] && echo true)"

if [ -n "$DB_EXEC_CMD" ]; then
  step "12. 타임아웃(S9-06~09): G 재진입 후 만료 시각을 과거로 → 폴링 TIMEOUT, 만료 항목은 무료 기회에서 제외(used 2→1)"
  req POST $Q "$TOK_G"; QG3=$(field queueId); req GET /api/v1/payments/me/items "$TOK_G"; U1=$(free used)
  eval "$DB_EXEC_CMD \"UPDATE MT_MATCH_QUEUE SET EXPIRE_DATE = NOW() - INTERVAL 1 MINUTE WHERE QUEUE_ID=$QG3\"" >/dev/null 2>&1
  req GET $Q "$TOK_G"; S1=$(field status); Q1=$(field queueId); req GET /api/v1/payments/me/items "$TOK_G"
  check "used 2(취소 1+대기 1) → TIMEOUT(같은 queueId) → used 1 remaining 2" "" "$([ "$U1" = 2 ] && [ "$S1" = TIMEOUT ] && [ "$Q1" = "$QG3" ] && [ "$(free used)" = 1 ] && [ "$(free remaining)" = 2 ] && echo true)"

  step "13. 다시 시도(S9-08) → 새 항목 WAITING, 무료 기회 다시 2"
  req POST $Q "$TOK_G"; QG4=$(field queueId); S1=$(field status); req GET /api/v1/payments/me/items "$TOK_G"
  check "새 queueId, WAITING, used=2" "" "$([ "$S1" = WAITING ] && [ "$QG4" != "$QG3" ] && [ "$(free used)" = 2 ] && echo true)"
  req DELETE $Q "$TOK_G"

  step "14. 이용권 환불: H 무료 3회 소진 → 매칭기회 구매 → ITEM 진입 → 타임아웃 → 이용권 환불(total 0→1)"
  mk H M 1993 SEOUL_GANGNAM '{"preferredRegionCode":"SEOUL","preferredGenderCode":"F"}'; for i in 1 2 3; do req POST $Q "$TOK_H"; req DELETE $Q "$TOK_H"; done
  req POST /api/v1/payments/consumable "$TOK_H" "{\"productCode\":\"MATCH_CHANCE\",\"orderId\":\"ord-q-$TS\",\"paymentKey\":\"pk-q-$TS\"}"
  req POST $Q "$TOK_H"; QH=$(field queueId); S1=$(field entrySource); req GET /api/v1/payments/me/items "$TOK_H"; B1=$(item_bal MATCH_CHANCE total)
  eval "$DB_EXEC_CMD \"UPDATE MT_MATCH_QUEUE SET EXPIRE_DATE = NOW() - INTERVAL 1 MINUTE WHERE QUEUE_ID=$QH\"" >/dev/null 2>&1
  req GET $Q "$TOK_H"; S2=$(field status); req GET /api/v1/payments/me/items "$TOK_H"
  check "ITEM 진입(잔여 0) → TIMEOUT → 잔여 1(REFUND)" "" "$([ "$S1" = ITEM ] && [ "$B1" = 0 ] && [ "$S2" = TIMEOUT ] && [ "$(item_bal MATCH_CHANCE total)" = 1 ] && [ "$(item_bal MATCH_CHANCE purchased)" = 1 ] && echo true)"
else
  for n in 12 13 14; do skip "$n. 타임아웃/환불" "DB_EXEC_CMD 미설정"; done
fi

step "15. 재매칭권 즉시 재배정: K(여, 선호 남) 대기 중 → A 가 D 와의 매칭에 재매칭권 사용 → 즉시 K 와 성사"
mk K F 1995 SEOUL_GANGNAM '{"preferredRegionCode":"SEOUL","preferredGenderCode":"M"}'; req POST $Q "$TOK_K"; SK=$(field status)
req POST /api/v1/payments/subscription "$TOK_A" '{"card":{"cardNumber":"4854797481503803","expiryYear":"30","expiryMonth":"12","identityNumber":"900101","password":"00"}}'
req POST "/api/v1/matches/$MATCH1/rematch" "$TOK_A"; C1=$CODE; RS=$(echo "$J" | grep -oE '"queue":\{[^}]*\}' | grep -oE '"status":"[A-Z]+"' | cut -d'"' -f4); RP=$(echo "$J" | grep -oE '"queue":\{[^}]*\}' | grep -oE '"partnerUserId":[0-9]+' | cut -d: -f2); MATCH2=$(echo "$J" | grep -oE '"queue":\{[^}]*\}' | grep -oE '"matchId":[0-9]+' | cut -d: -f2)
check "K WAITING → 재매칭 200, queue.status=MATCHED, partner=K, 새 matchId≠이전" "" "$([ "$SK" = WAITING ] && [ "$C1" = 200 ] && [ "$RS" = MATCHED ] && [ "$RP" = "$USER_K" ] && [ -n "$MATCH2" ] && [ "$MATCH2" != "$MATCH1" ] && echo true)"

step "16. 재배정 부수효과: K 폴링 MATCHED·상대 A / D 는 MATCH_ENDED 알림·현재 매칭 없음"
req GET $Q "$TOK_K"; K_OK=$([ "$(field status)" = MATCHED ] && [ "$(field partnerUserId)" = "$USER_A" ] && [ "$(field matchId)" = "$MATCH2" ] && echo true); req GET "/api/v1/notifications?page=0&size=3" "$TOK_D"; N_OK=$([ "$(field eventCode)" = MATCH_ENDED ] && echo true); req GET /api/v1/matches/current "$TOK_D"
check "K MATCHED partner=A, D MATCH_ENDED·hasMatch=false" "" "$([ "$K_OK" = true ] && [ "$N_OK" = true ] && [ "$(field hasMatch)" = false ] && echo true)"

step "17. 프로필 미완성 사용자 진입 → 400 MATCH_005 / 인증 없이 조회 → 401"
req POST /api/v1/auth/signup "" "{\"email\":\"bma66-x-$TS@example.com\",\"password\":\"$PW\"}"; req POST /api/v1/auth/login "" "{\"email\":\"bma66-x-$TS@example.com\",\"password\":\"$PW\"}"; TOK_X=$(field accessToken); req POST $Q "$TOK_X"; C1=$CODE; R1=$(field code); req GET $Q ""
check "400 MATCH_005, 401" "" "$([ "$C1" = 400 ] && [ "$R1" = MATCH_005 ] && [ "$CODE" = 401 ] && echo true)"

step "18. 정리: 남은 대기 항목 취소 (다른 스위트와 섞이지 않게)"
for t in "$TOK_A" "$TOK_B" "$TOK_C" "$TOK_D" "$TOK_E" "$TOK_F" "$TOK_G" "$TOK_H" "$TOK_K"; do [ -n "$t" ] && req DELETE $Q "$t"; done; req GET $Q "$TOK_K"
check "K 조회 200 (WAITING 아님)" "" "$([ "$CODE" = 200 ] && [ "$(field status)" != WAITING ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
