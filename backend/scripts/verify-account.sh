#!/usr/bin/env bash
# BMA-56 Postman 컬렉션(docs/postman/BMA-56-account.postman_collection.json)을 curl 로 재현한다.
# S8 마이페이지: 프로필 요약(S8-08), 로그아웃(S8-14), 회원탈퇴(S8-15), 비밀번호 변경(S8-16).
# newman/node 가 없는 환경용. 앱이 떠 있는 상태에서 실행한다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-account.sh
# Windows Git Bash 에서는 LANG=C.UTF-8 을 앞에 붙여야 한글 본문이 깨지지 않는다.
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma56-a-$TS@example.com"; EMAIL_B="bma56-b-$TS@example.com"
PW="Passw0rd!23"; PW2="NewPassw0rd!45"; NICK_A="계정A$((TS % 100000))"; NICK_B="계정B$((TS % 100000))"
PASS=0; FAIL=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
rooms_n() { echo "$J" | grep -o '"chatRoomId"' | wc -l | tr -d ' '; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
step() { echo "[$1]"; }
urlenc() { printf %s "$1" | od -An -tx1 -v | tr -d '[:space:]' | sed 's/../%&/g'; }
login() { req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"; }
ME=/api/v1/users/me

step "1. A 가입/로그인 → 내 계정 (S8-08): 이메일, 닉네임 null, LOCAL, passwordSet=true"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; USER_A=$(field userId); login "$EMAIL_A" "$PW"; TOK_A=$(field accessToken); RT_A=$(field refreshToken); req GET $ME "$TOK_A"
check "200, email·nickname=null·loginProvider=LOCAL·passwordSet=true·ACTIVE" "" "$([ "$CODE" = 200 ] && [ "$(field email)" = "$EMAIL_A" ] && has '"nickname":null' && [ "$(field loginProvider)" = LOCAL ] && [ "$(field passwordSet)" = true ] && [ "$(field userStatus)" = ACTIVE ] && echo true)"

step "2. 프로필 저장 → 내 계정에 닉네임 반영 (S8-08 요약 카드)"
req PUT $ME/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\"}"; req GET $ME "$TOK_A"
check "nickname=$NICK_A" "" "$([ "$CODE" = 200 ] && [ "$(field nickname)" = "$NICK_A" ] && echo true)"

step "3. 비밀번호 변경 (S8-16) - 현재 비밀번호 틀림 → 400 AUTH_015"
req PUT $ME/password "$TOK_A" "{\"currentPassword\":\"WrongPass99\",\"newPassword\":\"$PW2\"}"
check "400 AUTH_015" "" "$([ "$CODE" = 400 ] && [ "$(field code)" = AUTH_015 ] && echo true)"

step "4. 새 비밀번호 = 현재 비밀번호 → 400 COMMON_001"
req PUT $ME/password "$TOK_A" "{\"currentPassword\":\"$PW\",\"newPassword\":\"$PW\"}"
check "400 COMMON_001" "" "$([ "$CODE" = 400 ] && [ "$(field code)" = COMMON_001 ] && echo true)"

step "5. 새 비밀번호 규칙 위반(숫자만·7자) → 400 / 본문 없음 → 400"
req PUT $ME/password "$TOK_A" "{\"currentPassword\":\"$PW\",\"newPassword\":\"1234567\"}"; C1=$CODE; req PUT $ME/password "$TOK_A" '{}'
check "400 ×2" "" "$([ "$C1" = 400 ] && [ "$CODE" = 400 ] && echo true)"

step "6. 비밀번호 변경 성공 → sessionsEnded≥1, 옛 리프레시 토큰 재발급 불가"
req PUT $ME/password "$TOK_A" "{\"currentPassword\":\"$PW\",\"newPassword\":\"$PW2\"}"; C1=$CODE; SE=$(field sessionsEnded); CA=$(field changedAt); req POST /api/v1/auth/refresh "" "{\"refreshToken\":\"$RT_A\"}"
check "200, sessionsEnded=1, changedAt 존재, 옛 RT refresh → 401" "" "$([ "$C1" = 200 ] && [ "$SE" = 1 ] && [ -n "$CA" ] && [ "$CODE" = 401 ] && echo true)"

step "7. 옛 비밀번호 로그인 → 401 / 새 비밀번호 로그인 → 200"
login "$EMAIL_A" "$PW"; C1=$CODE; login "$EMAIL_A" "$PW2"; TOK_A=$(field accessToken); RT_A=$(field refreshToken)
check "401 → 200, 새 토큰 발급" "" "$([ "$C1" = 401 ] && [ "$CODE" = 200 ] && [ -n "$TOK_A" ] && echo true)"

step "8. 인증 없이 비밀번호 변경 / 탈퇴 → 401"
req PUT $ME/password "" "{\"currentPassword\":\"$PW2\",\"newPassword\":\"$PW\"}"; C1=$CODE; req DELETE $ME ""
check "401 ×2" "" "$([ "$C1" = 401 ] && [ "$CODE" = 401 ] && echo true)"

step "9. B 가입/프로필, A↔B 좋아요 → 매칭 성사, B 메시지 1건"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; USER_B=$(field userId); login "$EMAIL_B" "$PW"; TOK_B=$(field accessToken)
req PUT $ME/profile "$TOK_B" "{\"nickname\":\"$NICK_B\",\"birthDate\":\"1997-07-07\",\"genderCode\":\"F\",\"regionCode\":\"SEOUL_JUNG\"}"
req POST /api/v1/matching/actions "$TOK_B" "{\"targetUserId\":$USER_A,\"actionType\":\"LIKE\"}"; req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"; MATCH_ID=$(field matchId); ROOM_ID=$(field chatRoomId)
req POST "/api/v1/chat/rooms/$ROOM_ID/messages" "$TOK_B" '{"messageType":"TEXT","content":"안녕하세요"}'; C1=$CODE; req GET /api/v1/matches/current "$TOK_B"
check "매칭 성사(matchId/chatRoomId), 메시지 200, B hasMatch=true" "" "$([ -n "$MATCH_ID" ] && [ -n "$ROOM_ID" ] && [ "$C1" = 200 ] && [ "$(field hasMatch)" = true ] && echo true)"

step "10. 로그아웃 (S8-14): 로그아웃 200 → 그 리프레시 토큰 재발급 401 → 액세스 토큰은 만료 전까지 유효"
login "$EMAIL_A" "$PW2"; AT2=$(field accessToken); RT2=$(field refreshToken); req POST /api/v1/auth/logout "$AT2" "{\"refreshToken\":\"$RT2\"}"; C1=$CODE; req POST /api/v1/auth/refresh "" "{\"refreshToken\":\"$RT2\"}"; C2=$CODE; req GET $ME "$AT2"
check "200 → 401 → 200 (액세스 토큰은 서버 상태와 무관, 30분 만료)" "" "$([ "$C1" = 200 ] && [ "$C2" = 401 ] && [ "$CODE" = 200 ] && echo true)"

step "11. 회원 탈퇴 (S8-15) → 200 WITHDRAWN, matchesEnded=1"
req DELETE $ME "$TOK_A"
check "200, userId=A, status=WITHDRAWN, matchesEnded=1, withdrawnAt" "" "$([ "$CODE" = 200 ] && [ "$(field userId)" = "$USER_A" ] && [ "$(field status)" = WITHDRAWN ] && [ "$(field matchesEnded)" = 1 ] && [ -n "$(field withdrawnAt)" ] && echo true)"

step "12. 탈퇴 후 옛 이메일/비밀번호 로그인 → 401, 리프레시 토큰 재발급 → 401"
login "$EMAIL_A" "$PW2"; C1=$CODE; req POST /api/v1/auth/refresh "" "{\"refreshToken\":\"$RT_A\"}"
check "401 ×2" "" "$([ "$C1" = 401 ] && [ "$CODE" = 401 ] && echo true)"

step "13. 남은 액세스 토큰으로 내 계정 조회 / 재탈퇴 → 404 USER_001 (계정 없음)"
req GET $ME "$TOK_A"; C1=$CODE; req DELETE $ME "$TOK_A"
check "404 ×2 USER_001" "" "$([ "$C1" = 404 ] && [ "$CODE" = 404 ] && [ "$(field code)" = USER_001 ] && echo true)"

step "14. B 현재 매칭 → hasMatch=false (탈퇴로 매칭 종료)"
req GET /api/v1/matches/current "$TOK_B"
check "hasMatch=false, match=null" "" "$([ "$CODE" = 200 ] && [ "$(field hasMatch)" = false ] && has '"match":null' && echo true)"

step "15. B 알림 최신 → MATCH_ENDED (S7-14, S5), 탈퇴 사유·닉네임 비노출"
req GET "/api/v1/notifications?page=0&size=5" "$TOK_B"
check "eventCode=MATCH_ENDED, 제목 '매칭이 종료됐어요', target S5 matchId, '탈퇴'·닉네임 없음" "" "$([ "$CODE" = 200 ] && [ "$(field eventCode)" = MATCH_ENDED ] && [ "$(field title)" = "매칭이 종료됐어요" ] && has "\"target\":{\"screen\":\"S5\",\"matchId\":$MATCH_ID," && ! has 탈퇴 && ! has "$NICK_A" && echo true)"

step "16. B 채팅방 목록 → 방 유지, ENDED, partner=null(탈퇴한 사용자) / ENDED 방 전송 → 409 CHAT_003"
req GET /api/v1/chat/rooms "$TOK_B"; R_OK=$([ "$CODE" = 200 ] && [ "$(rooms_n)" = 1 ] && [ "$(field status)" = ENDED ] && has '"partner":null' && echo true); req POST "/api/v1/chat/rooms/$ROOM_ID/messages" "$TOK_B" '{"messageType":"TEXT","content":"계세요?"}'
check "ENDED·partner null, 전송 409 CHAT_003" "" "$([ "$R_OK" = true ] && [ "$CODE" = 409 ] && [ "$(field code)" = CHAT_003 ] && echo true)"

step "17. B 채팅 안읽음 합계 → 0 (ENDED 제외)"
req GET /api/v1/chat/rooms/unread-count "$TOK_B"
check "unreadCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 0 ] && echo true)"

step "18. 같은 이메일로 재가입 → 200, 새 userId (익명화로 이메일 해제)"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; USER_A2=$(field userId)
check "200, userId≠이전" "" "$([ "$CODE" = 200 ] && [ -n "$USER_A2" ] && [ "$USER_A2" != "$USER_A" ] && echo true)"

step "19. 새 계정에서 옛 닉네임 사용 가능 (익명화로 닉네임 해제)"
login "$EMAIL_A" "$PW"; TOK_A2=$(field accessToken); req GET "$ME/profile/nickname-check?nickname=$(urlenc "$NICK_A")" "$TOK_A2"
check "available=true" "" "$([ "$CODE" = 200 ] && [ "$(field available)" = true ] && echo true)"

step "20. 새 계정 내 계정 → 이전 프로필·매칭 미승계 (nickname=null, hasMatch=false)"
req GET $ME "$TOK_A2"; M_OK=$([ "$CODE" = 200 ] && has '"nickname":null' && [ "$(field passwordSet)" = true ] && echo true); req GET /api/v1/matches/current "$TOK_A2"
check "nickname=null·passwordSet=true, hasMatch=false" "" "$([ "$M_OK" = true ] && [ "$(field hasMatch)" = false ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
