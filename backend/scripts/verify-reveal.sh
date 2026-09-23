#!/usr/bin/env bash
# BMA-69 Postman 컬렉션(docs/postman/BMA-69-reveal.postman_collection.json)을 curl 로 재현한다.
# S10 Reveal: 매칭 상세(GET /matches/{id}) → 조건(24시간 + 각자 10개) → 다음 단계 요청 → 상대 동의 모달(나중에/동의) →
# 단계 상승(부분 공개: 키·이름 일부) → 2단계(전체 공개) → 구독자 24시간 스킵 → 구 동의 API 호환.
#   BASE=http://localhost:8080 bash backend/scripts/verify-reveal.sh
# 24시간 경과는 DB 에서 매칭 일시를 과거로 돌려 재현한다(DB_EXEC_CMD 필수). 예)
#   DB_EXEC_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e"
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
PW="Passw0rd!23"; N=$((TS % 10000))
PASS=0; FAIL=0; SKIP=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
hasF() { echo "$J" | grep -qF -- "$1"; }
rv() { echo "$J" | grep -oE '"reveal":\{[^}]*\}' | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
pt() { echo "$J" | grep -oE '"partner":\{[^}]*\}' | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
mk() { local n=$1 g=$2 y=$3 r=$4 extra=$5; local e="bma69-$n-$TS@example.com"
  req POST /api/v1/auth/signup "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; eval "USER_$n=$(field userId)"
  req POST /api/v1/auth/login "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; local t; t=$(field accessToken); eval "TOK_$n=$t"
  req PUT /api/v1/users/me/profile "$t" "{\"nickname\":\"리빌$n$N\",\"birthDate\":\"$y-05-05\",\"genderCode\":\"$g\",\"regionCode\":\"$r\"$extra}"; }
match() { req POST /api/v1/matching/actions "$2" "{\"targetUserId\":$3,\"actionType\":\"LIKE\"}"; req POST /api/v1/matching/actions "$1" "{\"targetUserId\":$4,\"actionType\":\"LIKE\"}"; }
send_n() { local t=$1 room=$2 n=$3 i; for i in $(seq 1 "$n"); do req POST "/api/v1/chat/rooms/$room/messages" "$t" "{\"messageType\":\"TEXT\",\"content\":\"msg $i\"}"; done; }
mask_expect() { local s=$1 len=${#1} vis; vis=$((len / 2)); [ $vis -lt 1 ] && vis=1; printf '%s' "${s:0:vis}"; printf '?%.0s' $(seq 1 $((len - vis))); }

step "1. A(남)·B(여, 키 165) 매칭 성사 → 매칭 상세: 실루엣(0), 다음 1단계, 24시간·각자 10개 미충족, 키·이름 비노출, 재매칭권 0"
mk A M 1995 SEOUL_GANGNAM ''; mk B F 1997 SEOUL_JUNG ',"heightCm":165,"mbtiCode":"INFP"'; NICK_B="리빌B$N"
match "$TOK_A" "$TOK_B" "$USER_A" "$USER_B"; MATCH1=$(field matchId); ROOM1=$(field chatRoomId)
req GET "/api/v1/matches/$MATCH1" "$TOK_A"; REM=$(rv hoursRemainingMinutes)
check "200 match.matchId, reveal 0·실루엣·next 1·requiredHours 24·hoursSatisfied false·남은 분 1380~1440·requiredPerUser 10·canRequest false, partner nickname/nicknameMasked/heightCm null, rematchTickets 0" "" "$([ "$CODE" = 200 ] && [ "$(field matchId)" = "$MATCH1" ] && [ "$(rv currentLevel)" = 0 ] && [ "$(rv currentLevelName)" = 실루엣 ] && [ "$(rv nextLevel)" = 1 ] && [ "$(rv requiredHours)" = 24 ] && [ "$(rv hoursSatisfied)" = false ] && [ "$REM" -ge 1380 ] && [ "$REM" -le 1440 ] && [ "$(rv requiredMessagesPerUser)" = 10 ] && [ "$(rv canRequest)" = false ] && [ "$(pt nickname)" = null ] && [ "$(pt nicknameMasked)" = null ] && [ "$(pt heightCm)" = null ] && [ "$(field rematchTickets)" = 0 ] && echo true)"

step "2. 비참여자 조회 → 404 MATCH_003 / 인증 없이 → 401"
mk C M 1990 SEOUL_GANGNAM ''; req GET "/api/v1/matches/$MATCH1" "$TOK_C"; C1=$CODE; R1=$(field code); req GET "/api/v1/matches/$MATCH1/reveal" ""
check "404 MATCH_003, 401" "" "$([ "$C1" = 404 ] && [ "$R1" = MATCH_003 ] && [ "$CODE" = 401 ] && echo true)"

step "3. A 10개·B 9개 전송 → 각자 기준: A 충족·B 1개 부족, messagesSatisfied false (합산 19/20)"
send_n "$TOK_A" "$ROOM1" 10; send_n "$TOK_B" "$ROOM1" 9; req GET "/api/v1/matches/$MATCH1/reveal" "$TOK_A"
check "myMessages 10, partnerMessages 9, messagesSatisfied false, myRemaining 0, partnerRemaining 1, total 19/20" "" "$([ "$CODE" = 200 ] && [ "$(field myMessages)" = 10 ] && [ "$(field partnerMessages)" = 9 ] && [ "$(field messagesSatisfied)" = false ] && [ "$(field myMessagesRemaining)" = 0 ] && [ "$(field partnerMessagesRemaining)" = 1 ] && [ "$(field totalMessages)" = 19 ] && [ "$(field requiredTotalMessages)" = 20 ] && echo true)"

step "4. 메시지 미충족 상태 요청 → 409 REVEAL_002"
req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_A"
check "409 REVEAL_002 (각자 10개)" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = REVEAL_002 ] && echo true)"

step "5. B 1개 더 → 메시지 충족, 그러나 24시간 미경과 → 요청 409 REVEAL_002 (시간)"
send_n "$TOK_B" "$ROOM1" 1; req GET "/api/v1/matches/$MATCH1/reveal" "$TOK_A"; MS=$(field messagesSatisfied); CR=$(field canRequest); req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_A"
check "messagesSatisfied true, canRequest false, 요청 409 REVEAL_002 '24시간'" "" "$([ "$MS" = true ] && [ "$CR" = false ] && [ "$CODE" = 409 ] && [ "$(field code)" = REVEAL_002 ] && hasF "24시간" && echo true)"

if [ -z "$DB_EXEC_CMD" ]; then for n in 6 7 8 9 10 11 12 13 14 15 16 17; do skip "$n." "DB_EXEC_CMD 미설정(매칭 일시 조정 필요)"; done; rm -f /tmp/body.$$ /tmp/req.$$; echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; exit 1; fi

step "6. 매칭 일시를 25시간 전으로 → hoursSatisfied true, 남은 0, canRequest true, progressRate 100"
eval "$DB_EXEC_CMD \"UPDATE MT_MATCH SET MATCH_DATE = MATCH_DATE - INTERVAL 25 HOUR WHERE MATCH_ID=$MATCH1\"" >/dev/null 2>&1
req GET "/api/v1/matches/$MATCH1/reveal" "$TOK_A"
check "hoursSatisfied true, hoursRemainingMinutes 0, canRequest true, progressRate 100" "" "$([ "$CODE" = 200 ] && [ "$(field hoursSatisfied)" = true ] && [ "$(field hoursRemainingMinutes)" = 0 ] && [ "$(field canRequest)" = true ] && [ "$(field progressRate)" = 100 ] && echo true)"

step "7. A 다음 단계 요청 (S10-12) → 내 동의 ACCEPTED·상대 PENDING·단계 그대로 / B 에 REVEAL_REQUESTED 알림(S7-12, '???님이…')"
req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_A"; A_OK=$([ "$CODE" = 200 ] && [ "$(field leveledUp)" = false ] && [ "$(field myConsent)" = ACCEPTED ] && [ "$(field partnerConsent)" = PENDING ] && [ "$(field canRequest)" = false ] && [ "$(field currentLevel)" = 0 ] && echo true); req GET "/api/v1/notifications?page=0&size=3" "$TOK_B"
check "200 leveledUp=false, ACCEPTED/PENDING, B 최신 알림 REVEAL_REQUESTED·target S10·'???님이 다음 단계를 요청했어요'" "" "$([ "$A_OK" = true ] && [ "$(field eventCode)" = REVEAL_REQUESTED ] && has "\"target\":{\"screen\":\"S10\",\"matchId\":$MATCH1," && hasF '"title":"???님이 다음 단계를 요청했어요"' && echo true)"

step "8. A 재요청 → 멱등(알림 중복 없음)"
req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_A"; C1=$CODE; req GET "/api/v1/notifications?page=0&size=10" "$TOK_B"
check "200, REVEAL_REQUESTED 알림 1건" "" "$([ "$C1" = 200 ] && [ "$(echo "$J" | grep -o '"eventCode":"REVEAL_REQUESTED"' | wc -l | tr -d ' ')" = 1 ] && echo true)"

step "9. B 상태 → incomingRequest true (동의 모달 S10-14), 상대 ACCEPTED·나 PENDING, requestedAt"
req GET "/api/v1/matches/$MATCH1/reveal" "$TOK_B"
check "incomingRequest true, partnerConsent ACCEPTED, myConsent PENDING, requestedAt" "" "$([ "$CODE" = 200 ] && [ "$(field incomingRequest)" = true ] && [ "$(field partnerConsent)" = ACCEPTED ] && [ "$(field myConsent)" = PENDING ] && [ "$(field requestedAt)" != null ] && echo true)"

step "10. B '나중에'(S10-17, consent=false) → 아무 변화 없음, A 에게 거절 비노출(상대 PENDING 유지), 모달 조건 유지"
req POST "/api/v1/matches/$MATCH1/reveal-consent" "$TOK_B" '{"consent":false}'; C1=$CODE; L1=$(field leveledUp); req GET "/api/v1/matches/$MATCH1/reveal" "$TOK_A"; PC=$(field partnerConsent); req GET "/api/v1/matches/$MATCH1/reveal" "$TOK_B"
check "200 leveledUp=false, A 가 보는 partnerConsent PENDING, B incomingRequest 여전히 true" "" "$([ "$C1" = 200 ] && [ "$L1" = false ] && [ "$PC" = PENDING ] && [ "$(field incomingRequest)" = true ] && echo true)"

step "11. B '동의하고 열기'(S10-16) → 단계 1(부분 공개), 양쪽 REVEAL_LEVEL_UP 알림"
req POST "/api/v1/matches/$MATCH1/reveal-consent" "$TOK_B" '{"consent":true}'; B_OK=$([ "$CODE" = 200 ] && [ "$(field leveledUp)" = true ] && [ "$(field currentLevel)" = 1 ] && [ "$(field currentLevelName)" = "부분 공개" ] && [ "$(field nextLevel)" = 2 ] && [ "$(field myConsent)" = PENDING ] && echo true); req GET "/api/v1/notifications?page=0&size=3" "$TOK_A"
check "leveledUp true, level 1 부분 공개, next 2, 새 단계 동의 PENDING, A 최신 알림 REVEAL_LEVEL_UP" "" "$([ "$B_OK" = true ] && [ "$(field eventCode)" = REVEAL_LEVEL_UP ] && echo true)"

step "12. 부분 공개 상대 프로필 (S10-09/10/18): 이름 앞 절반 + ?, 키 165 노출, 원문 이름은 아직 null"
req GET "/api/v1/matches/$MATCH1/reveal/partner" "$TOK_A"; EXP=$(mask_expect "$NICK_B")
check "revealLevel 1, nicknameMasked=$EXP, nickname null, heightCm 165, age 존재" "" "$([ "$CODE" = 200 ] && [ "$(field revealLevel)" = 1 ] && [ "$(field nicknameMasked)" = "$EXP" ] && [ "$(field nickname)" = null ] && [ "$(field heightCm)" = 165 ] && [ "$(field age)" != null ] && echo true)"

step "13. 2단계 조건: 각자 25개 → A·B 15개씩 더 → A 요청, B 요청(=동의) → 전체 공개(2)"
send_n "$TOK_A" "$ROOM1" 15; send_n "$TOK_B" "$ROOM1" 15; req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_A"; L1=$(field leveledUp); req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_B"
check "A 요청 leveledUp=false → B 요청 leveledUp=true, level 2 전체 공개, maxLevelReached true" "" "$([ "$L1" = false ] && [ "$CODE" = 200 ] && [ "$(field leveledUp)" = true ] && [ "$(field currentLevel)" = 2 ] && [ "$(field maxLevelReached)" = true ] && echo true)"

step "14. 전체 공개 상대 프로필: 원문 이름·시군구 지역 / 최고 단계에서 요청 → 400 REVEAL_003 / 상세 progressRate 100·nextLevel null"
req GET "/api/v1/matches/$MATCH1/reveal/partner" "$TOK_A"; P_OK=$([ "$(field nickname)" = "$NICK_B" ] && [ "$(field nicknameMasked)" = "$NICK_B" ] && [ "$(field regionName)" = 중구 ] && echo true); req POST "/api/v1/matches/$MATCH1/reveal-request" "$TOK_A"; C1=$CODE; R1=$(field code); req GET "/api/v1/matches/$MATCH1" "$TOK_A"
check "nickname 원문·중구, 400 REVEAL_003, reveal.progressRate 100 nextLevel null" "" "$([ "$P_OK" = true ] && [ "$C1" = 400 ] && [ "$R1" = REVEAL_003 ] && [ "$(rv progressRate)" = 100 ] && [ "$(rv nextLevel)" = null ] && echo true)"

step "15. 구독자 24시간 스킵: C·D 매칭 + 각자 10개, D 구독 → D canRequest true(hoursSatisfied false), 비구독 C 는 canRequest false·요청 409"
mk D F 1998 SEOUL_JUNG ''; match "$TOK_C" "$TOK_D" "$USER_C" "$USER_D"; MATCH2=$(field matchId); ROOM2=$(field chatRoomId); send_n "$TOK_C" "$ROOM2" 10; send_n "$TOK_D" "$ROOM2" 10
req POST /api/v1/payments/subscription "$TOK_D" '{"card":{"cardNumber":"4854797481503803","expiryYear":"30","expiryMonth":"12","identityNumber":"900101","password":"00"}}'; SUB=$CODE
req GET "/api/v1/matches/$MATCH2/reveal" "$TOK_D"; D_OK=$([ "$(field canRequest)" = true ] && [ "$(field hoursSatisfied)" = false ] && echo true); req GET "/api/v1/matches/$MATCH2/reveal" "$TOK_C"; C_OK=$([ "$(field canRequest)" = false ] && echo true); req POST "/api/v1/matches/$MATCH2/reveal-request" "$TOK_C"
check "구독 200, D canRequest true·hoursSatisfied false(구독 비노출), C canRequest false, C 요청 409" "" "$([ "$SUB" = 200 ] && [ "$D_OK" = true ] && [ "$C_OK" = true ] && [ "$CODE" = 409 ] && echo true)"

step "16. D 요청 → C 에게 일반 요청과 같은 모양(incomingRequest) → C 동의 → 단계 1 (상대 구독으로 시간 조건 충족)"
req POST "/api/v1/matches/$MATCH2/reveal-request" "$TOK_D"; C1=$CODE; req GET "/api/v1/matches/$MATCH2/reveal" "$TOK_C"; IN=$(field incomingRequest); req POST "/api/v1/matches/$MATCH2/reveal-consent" "$TOK_C" '{"consent":true}'
check "D 요청 200, C incomingRequest true, C 동의 → leveledUp true level 1" "" "$([ "$C1" = 200 ] && [ "$IN" = true ] && [ "$CODE" = 200 ] && [ "$(field leveledUp)" = true ] && [ "$(field currentLevel)" = 1 ] && echo true)"

step "17. 구 동의 API 호환: 단계 건너뛰기/되돌리기 → 400 REVEAL_003, 조건 미충족 → 409 REVEAL_002 / 정리(구독 해지)"
req POST "/api/v1/matches/$MATCH2/reveal/consent" "$TOK_C" '{"revealLevel":1,"consent":true}'; C1=$CODE; R1=$(field code); req POST "/api/v1/matches/$MATCH2/reveal/consent" "$TOK_C" '{"revealLevel":2,"consent":true}'; C2=$CODE; R2=$(field code); req DELETE /api/v1/payments/subscription "$TOK_D"
check "400 REVEAL_003, 409 REVEAL_002, 해지 200" "" "$([ "$C1" = 400 ] && [ "$R1" = REVEAL_003 ] && [ "$C2" = 409 ] && [ "$R2" = REVEAL_002 ] && [ "$CODE" = 200 ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
