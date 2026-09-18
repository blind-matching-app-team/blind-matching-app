#!/usr/bin/env bash
# BMA-47 Postman 컬렉션(docs/postman/BMA-47-match.postman_collection.json) 20단계를
# curl 로 재현한다. newman/node 가 없는 환경용. 앱이 떠 있는 상태에서 실행한다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-match.sh
# Windows Git Bash 에서는 LANG=C.UTF-8 을 앞에 붙여야 한글 본문이 깨지지 않는다.
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma47-a-$TS@example.com"; EMAIL_B="bma47-b-$TS@example.com"
PW="Passw0rd!23"; NICK_A="허브A$((TS % 100000))"; NICK_B="허브B$((TS % 100000))"
PASS=0; FAIL=0; J=""; CODE=""
req() { # method path [token] [body]
  local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$)
}
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
step() { echo "[$1]"; }
signup_login() { # email → 토큰 echo
  req POST /api/v1/auth/signup "" "{\"email\":\"$1\",\"password\":\"$PW\"}"
  req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; field accessToken; }

step "1. 사용자 A 가입/로그인"; TOK_A=$(signup_login "$EMAIL_A")
check "토큰 발급" "" "$([ -n "$TOK_A" ] && echo true)"
step "2. 프로필 A (서울 강남, M, ENFP)"; req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\",\"mbtiCode\":\"ENFP\"}"
check "200 COMPLETE" "" "$([ "$CODE" = 200 ] && [ "$(field profileStatus)" = COMPLETE ] && echo true)"
step "3. 온보딩 질문 조회 - 관심사 문항·보기 ID 확보"; req GET /api/v1/onboarding/questions "$TOK_A"
QID=$(echo "$J" | grep -oE '"questionId":[0-9]+,"questionText":"[^"]*","questionType":"MULTI","categoryCode":"INTEREST"' | grep -oE '[0-9]+' | head -1)
OPT_CULTURE=$(echo "$J" | grep -oE '"optionId":[0-9]+,"optionCode":"CULTURE"' | grep -oE '[0-9]+'); OPT_TRAVEL=$(echo "$J" | grep -oE '"optionId":[0-9]+,"optionCode":"TRAVEL"' | grep -oE '[0-9]+'); OPT_FITNESS=$(echo "$J" | grep -oE '"optionId":[0-9]+,"optionCode":"FITNESS"' | grep -oE '[0-9]+')
check "INTEREST 문항과 CULTURE/TRAVEL/FITNESS 보기 존재" "" "$([ "$CODE" = 200 ] && [ -n "$QID" ] && [ -n "$OPT_CULTURE" ] && [ -n "$OPT_TRAVEL" ] && [ -n "$OPT_FITNESS" ] && echo true)"
step "4. A 관심사 답변: 문화생활 + 여행"; req POST /api/v1/onboarding/answers "$TOK_A" "{\"answers\":[{\"questionId\":$QID,\"optionId\":$OPT_CULTURE,\"rank\":1},{\"questionId\":$QID,\"optionId\":$OPT_TRAVEL,\"rank\":2}]}"
check "200, savedCount=2" "" "$([ "$CODE" = 200 ] && [ "$(field savedCount)" = 2 ] && echo true)"
step "5. 현재 매칭 조회 - 매칭 없음"; req GET /api/v1/matches/current "$TOK_A"
check "200, hasMatch=false, match 키는 null 로 존재" "" "$([ "$CODE" = 200 ] && [ "$(field hasMatch)" = false ] && has '"match":null' && echo true)"
step "6. 매칭 목록 - 빈 배열"; req GET /api/v1/matches "$TOK_A"
check "200, data=[]" "" "$([ "$CODE" = 200 ] && has '"data":\[\]' && echo true)"
step "7. 사용자 B 가입/로그인/프로필 (서울 중구, F, INFP)/관심사: 문화생활 + 운동"; TOK_B=$(signup_login "$EMAIL_B"); req PUT /api/v1/users/me/profile "$TOK_B" "{\"nickname\":\"$NICK_B\",\"birthDate\":\"1997-07-07\",\"genderCode\":\"F\",\"regionCode\":\"SEOUL_JUNG\",\"mbtiCode\":\"INFP\"}"; req POST /api/v1/onboarding/answers "$TOK_B" "{\"answers\":[{\"questionId\":$QID,\"optionId\":$OPT_CULTURE,\"rank\":1},{\"questionId\":$QID,\"optionId\":$OPT_FITNESS,\"rank\":2}]}"
check "B 답변 저장 200" "" "$([ "$CODE" = 200 ] && echo true)"
USER_B=$(req GET /api/v1/users/me "$TOK_B"; field userId); USER_A=$(req GET /api/v1/users/me "$TOK_A"; field userId)
step "8. A → B 좋아요 (한쪽만)"; req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"
check "200, matched=false" "" "$([ "$CODE" = 200 ] && [ "$(field matched)" = false ] && echo true)"
step "9. 여전히 매칭 없음"; req GET /api/v1/matches/current "$TOK_A"
check "hasMatch=false" "" "$([ "$CODE" = 200 ] && [ "$(field hasMatch)" = false ] && echo true)"
step "10. B → A 좋아요 → 상호 매칭 성사"; req POST /api/v1/matching/actions "$TOK_B" "{\"targetUserId\":$USER_A,\"actionType\":\"LIKE\"}"; MATCH_ID=$(field matchId); ROOM_ID=$(field chatRoomId)
check "200, matched=true, matchId·chatRoomId 발급" "" "$([ "$CODE" = 200 ] && [ "$(field matched)" = true ] && [ -n "$MATCH_ID" ] && [ -n "$ROOM_ID" ] && echo true)"
step "11. A 현재 매칭 - 카드 데이터"; req GET /api/v1/matches/current "$TOK_A"
check "hasMatch=true, matchId/partnerUserId/chatRoomId 일치" "" "$([ "$CODE" = 200 ] && [ "$(field hasMatch)" = true ] && [ "$(field matchId)" = "$MATCH_ID" ] && [ "$(field partnerUserId)" = "$USER_B" ] && [ "$(field chatRoomId)" = "$ROOM_ID" ] && echo true)"
check "partner: nickname null(???), 시/도 서울특별시, MBTI INFP, 20대, imageKeys []" "" "$(has '"nickname":null' && [ "$(field regionName)" = 서울특별시 ] && [ "$(field regionCode)" = SEOUL ] && [ "$(field mbtiCode)" = INFP ] && has '"ageGroup":"20대' && has '"imageKeys":\[\]' && echo true)"
check "commonInterests = [문화생활] (여행·운동은 한쪽만 골라 제외)" "" "$(has '"commonInterests":\["문화생활"\]' && echo true)"
check "reveal: 0단계 미공개, 다음 1단계, 0/20 메시지, progressRate 0" "" "$([ "$(field currentLevel)" = 0 ] && [ "$(field currentLevelName)" = 미공개 ] && [ "$(field nextLevel)" = 1 ] && [ "$(field messageCount)" = 0 ] && [ "$(field requiredMessageCount)" = 20 ] && [ "$(field progressRate)" = 0 ] && echo true)"
step "12. B 현재 매칭 - 상대는 A, 공통관심사 동일"; req GET /api/v1/matches/current "$TOK_B"
check "partnerUserId=A, ENFP, commonInterests=[문화생활]" "" "$([ "$CODE" = 200 ] && [ "$(field partnerUserId)" = "$USER_A" ] && [ "$(field mbtiCode)" = ENFP ] && has '"commonInterests":\["문화생활"\]' && echo true)"
step "13. A 매칭 목록 - 1건, 같은 모양"; req GET /api/v1/matches "$TOK_A"
check "200, matchId 포함, partner·reveal 포함" "" "$([ "$CODE" = 200 ] && [ "$(field matchId)" = "$MATCH_ID" ] && has '"partner":{' && has '"reveal":{' && echo true)"
step "14. 인증 없이 현재 매칭"; req GET /api/v1/matches/current ""
check "401" "" "$([ "$CODE" = 401 ] && echo true)"
step "15. B 가 없는 매칭 ID 로 그만두기"; req DELETE /api/v1/matches/999999999 "$TOK_B"
check "404 MATCH_003" "" "$([ "$CODE" = 404 ] && [ "$(field code)" = MATCH_003 ] && echo true)"
step "16. B 매칭 그만두기 (S5-16)"; req DELETE "/api/v1/matches/$MATCH_ID" "$TOK_B"
check "200" "" "$([ "$CODE" = 200 ] && echo true)"
step "17. A 현재 매칭 - 빈 상태로 전환"; req GET /api/v1/matches/current "$TOK_A"
check "hasMatch=false" "" "$([ "$CODE" = 200 ] && [ "$(field hasMatch)" = false ] && echo true)"
step "18. A 알림 - 종료 알림 1건 (사유·주체 없음)"; req GET "/api/v1/notifications?page=0&size=20" "$TOK_A"
check "'매칭이 종료되었어요' 포함, 상대 닉네임 미포함" "" "$([ "$CODE" = 200 ] && has '매칭이 종료되었어요' && ! has "$NICK_B" && echo true)"
step "19. B 같은 매칭 다시 그만두기 - 멱등"; req DELETE "/api/v1/matches/$MATCH_ID" "$TOK_B"; req GET "/api/v1/notifications?page=0&size=20" "$TOK_A"
check "200, A 종료 알림은 여전히 1건" "" "$([ "$CODE" = 200 ] && [ "$(echo "$J" | grep -o '매칭이 종료되었어요' | wc -l | tr -d ' ')" = 1 ] && echo true)"
step "20. B 매칭 목록 - 빈 배열"; req GET /api/v1/matches "$TOK_B"
check "200, data=[]" "" "$([ "$CODE" = 200 ] && has '"data":\[\]' && echo true)"
rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
