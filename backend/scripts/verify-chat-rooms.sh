#!/usr/bin/env bash
# BMA-50 Postman 컬렉션(docs/postman/BMA-50-chat-rooms.postman_collection.json) 20단계를
# curl 로 재현한다. newman/node 가 없는 환경용. 앱이 떠 있는 상태에서 실행한다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-chat-rooms.sh
# Windows Git Bash 에서는 LANG=C.UTF-8 을 앞에 붙여야 한글 본문이 깨지지 않는다.
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma50-a-$TS@example.com"; EMAIL_B="bma50-b-$TS@example.com"; EMAIL_C="bma50-c-$TS@example.com"
PW="Passw0rd!23"; NICK_A="채팅A$((TS % 100000))"; NICK_B="채팅B$((TS % 100000))"
PASS=0; FAIL=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
rooms_n() { echo "$J" | grep -o '"chatRoomId"' | wc -l | tr -d ' '; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
step() { echo "[$1]"; }
signup_login() { req POST /api/v1/auth/signup "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; field accessToken; }
send() { req POST "/api/v1/chat/rooms/$2/messages" "$1" "{\"messageType\":\"TEXT\",\"content\":\"$3\"}"; }

step "1. A·B 가입/로그인/프로필 (M/F, 서울) → 상호 좋아요로 매칭·채팅방 생성"
TOK_A=$(signup_login "$EMAIL_A"); TOK_B=$(signup_login "$EMAIL_B")
req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\",\"mbtiCode\":\"ENFP\"}"
req PUT /api/v1/users/me/profile "$TOK_B" "{\"nickname\":\"$NICK_B\",\"birthDate\":\"1997-07-07\",\"genderCode\":\"F\",\"regionCode\":\"SEOUL_JUNG\",\"mbtiCode\":\"INFP\"}"
USER_A=$(req GET /api/v1/users/me "$TOK_A"; field userId); USER_B=$(req GET /api/v1/users/me "$TOK_B"; field userId)
req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"
req POST /api/v1/matching/actions "$TOK_B" "{\"targetUserId\":$USER_A,\"actionType\":\"LIKE\"}"; MATCH_ID=$(field matchId); ROOM_ID=$(field chatRoomId)
check "matched=true, chatRoomId 발급" "" "$([ "$CODE" = 200 ] && [ "$(field matched)" = true ] && [ -n "$ROOM_ID" ] && echo true)"
step "2. A 목록 - 방 1개, ACTIVE, 상대 마스킹(???), 메시지 없음"; req GET /api/v1/chat/rooms "$TOK_A"
check "1개, status=ACTIVE, partner nickname null·서울특별시·INFP, lastMessage null, unread 0" "" "$([ "$CODE" = 200 ] && [ "$(rooms_n)" = 1 ] && [ "$(field chatRoomId)" = "$ROOM_ID" ] && [ "$(field status)" = ACTIVE ] && has '"nickname":null' && [ "$(field regionName)" = 서울특별시 ] && [ "$(field mbtiCode)" = INFP ] && has '"lastMessage":null' && [ "$(field unreadCount)" = 0 ] && echo true)"
step "3. A 안읽음 합계 - 0"; req GET /api/v1/chat/rooms/unread-count "$TOK_A"
check "unreadCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "4. B 가 메시지 2건 전송 (REST)"; send "$TOK_B" "$ROOM_ID" "안녕하세요 반가워요"; C1=$CODE; send "$TOK_B" "$ROOM_ID" "오늘 저녁에 시간 괜찮으세요?"; MSG2=$(field messageId)
check "200 ×2, messageId 발급, chatRoomId 일치" "" "$([ "$C1" = 200 ] && [ "$CODE" = 200 ] && [ -n "$MSG2" ] && [ "$(field chatRoomId)" = "$ROOM_ID" ] && echo true)"
step "5. A 목록 - unread 2, 마지막 메시지 미리보기·시간"; req GET /api/v1/chat/rooms "$TOK_A"
check "unreadCount=2, lastMessage='오늘 저녁에 시간 괜찮으세요?', TEXT, lastMessageDate 존재" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 2 ] && [ "$(field lastMessage)" = "오늘 저녁에 시간 괜찮으세요?" ] && [ "$(field lastMessageType)" = TEXT ] && ! has '"lastMessageDate":null' && echo true)"
step "6. A 안읽음 합계 = 목록 합 (2)"; req GET /api/v1/chat/rooms/unread-count "$TOK_A"
check "unreadCount=2" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 2 ] && echo true)"
step "7. B 목록 - 본인 메시지는 안읽음 아님"; req GET /api/v1/chat/rooms "$TOK_B"
check "unreadCount=0, partner ENFP" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 0 ] && [ "$(field mbtiCode)" = ENFP ] && echo true)"
step "8. A 읽음 처리 → unread 0"; req PUT "/api/v1/chat/rooms/$ROOM_ID/read?messageId=$MSG2" "$TOK_A"; req GET /api/v1/chat/rooms/unread-count "$TOK_A"
check "unreadCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "9. A 가 긴 메시지(60자) 전송 → B 목록 미리보기는 50자 + ..."; LONG=$(printf '가%.0s' $(seq 1 60)); send "$TOK_A" "$ROOM_ID" "$LONG"; req GET /api/v1/chat/rooms "$TOK_B"
PREV=$(field lastMessage)
check "unread 1, 미리보기 '...' 로 끝나고 53자 이하" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 1 ] && [ "${PREV: -3}" = "..." ] && [ ${#PREV} -le 53 ] && echo true)"
step "10. 인증 없이 목록 → 401 / 비참여자 C 목록 → 빈 배열(S6-10)"; req GET /api/v1/chat/rooms ""; C401=$CODE; TOK_C=$(signup_login "$EMAIL_C"); req GET /api/v1/chat/rooms "$TOK_C"
check "401, C 는 data=[]" "" "$([ "$C401" = 401 ] && [ "$CODE" = 200 ] && has '"data":\[\]' && echo true)"
step "11. C 가 남의 방 나가기/전송 → 403 CHAT_002"; req DELETE "/api/v1/chat/rooms/$ROOM_ID" "$TOK_C"; C1=$CODE; send "$TOK_C" "$ROOM_ID" "끼어들기"
check "403 ×2, CHAT_002" "" "$([ "$C1" = 403 ] && [ "$CODE" = 403 ] && [ "$(field code)" = CHAT_002 ] && echo true)"
step "12. A 나가기 (S6-11) → A 목록 비고, B 목록은 유지"; req DELETE "/api/v1/chat/rooms/$ROOM_ID" "$TOK_A"; C1=$CODE; req GET /api/v1/chat/rooms "$TOK_A"; NA=$(rooms_n); req GET /api/v1/chat/rooms "$TOK_B"
check "200, A 0개, B 1개" "" "$([ "$C1" = 200 ] && [ "$NA" = 0 ] && [ "$(rooms_n)" = 1 ] && echo true)"
step "13. B 가 새 메시지 → A 목록에 방 재등장, 안읽음은 새 메시지 1건만"; send "$TOK_B" "$ROOM_ID" "다시 연락드려요"; req GET /api/v1/chat/rooms "$TOK_A"
check "A 1개, unreadCount=1, lastMessage='다시 연락드려요'" "" "$([ "$CODE" = 200 ] && [ "$(rooms_n)" = 1 ] && [ "$(field unreadCount)" = 1 ] && [ "$(field lastMessage)" = "다시 연락드려요" ] && echo true)"
step "14. B 매칭 그만두기 (S5-16) → 방은 남고 ENDED, 안읽음 0"; req DELETE "/api/v1/matches/$MATCH_ID" "$TOK_B"; req GET /api/v1/chat/rooms "$TOK_A"
check "A 목록 1개, status=ENDED, unreadCount=0 (직전 1건이었음)" "" "$([ "$CODE" = 200 ] && [ "$(rooms_n)" = 1 ] && [ "$(field status)" = ENDED ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "15. A 안읽음 합계 - ENDED 제외 → 0"; req GET /api/v1/chat/rooms/unread-count "$TOK_A"
check "unreadCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "16. ENDED 방에 전송 → 409 CHAT_003 (읽기 전용)"; send "$TOK_A" "$ROOM_ID" "아직 계세요?"
check "409 CHAT_003" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = CHAT_003 ] && echo true)"
step "17. ENDED 방 이력은 읽을 수 있다"; req GET "/api/v1/chat/rooms/$ROOM_ID/messages?page=0&size=30" "$TOK_A"
check "200, 메시지 4건" "" "$([ "$CODE" = 200 ] && [ "$(echo "$J" | grep -o '"messageId"' | wc -l | tr -d ' ')" = 4 ] && echo true)"
step "18. B 목록 - ENDED, 상대 여전히 표시"; req GET /api/v1/chat/rooms "$TOK_B"
check "status=ENDED, partnerUserId=A" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = ENDED ] && [ "$(field partnerUserId)" = "$USER_A" ] && echo true)"
step "19. ENDED 방도 나갈 수 있다 → B 목록 비고 다시 나타나지 않음"; req DELETE "/api/v1/chat/rooms/$ROOM_ID" "$TOK_B"; C1=$CODE; req GET /api/v1/chat/rooms "$TOK_B"
check "200, B data=[]" "" "$([ "$C1" = 200 ] && [ "$CODE" = 200 ] && has '"data":\[\]' && echo true)"
step "20. 없는 방 나가기 → 403 CHAT_002 (존재 여부 비노출)"; req DELETE /api/v1/chat/rooms/999999999 "$TOK_A"
check "403 CHAT_002" "" "$([ "$CODE" = 403 ] && [ "$(field code)" = CHAT_002 ] && echo true)"
rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
