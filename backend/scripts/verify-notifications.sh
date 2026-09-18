#!/usr/bin/env bash
# BMA-53 Postman 컬렉션(docs/postman/BMA-53-notifications.postman_collection.json) 20단계를
# curl 로 재현한다. newman/node 가 없는 환경용. 앱이 떠 있는 상태에서 실행한다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-notifications.sh
# Windows Git Bash 에서는 LANG=C.UTF-8 을 앞에 붙여야 한글 본문이 깨지지 않는다.
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma53-a-$TS@example.com"; EMAIL_B="bma53-b-$TS@example.com"
PW="Passw0rd!23"; NICK_A="알림A$((TS % 100000))"; NICK_B="알림B$((TS % 100000))"
PASS=0; FAIL=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
items_n() { echo "$J" | grep -o '"notificationId"' | wc -l | tr -d ' '; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
step() { echo "[$1]"; }
signup_login() { req POST /api/v1/auth/signup "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; field accessToken; }
NOTI=/api/v1/notifications

step "1. A 가입/로그인 → 알림 목록 비어 있음 (S7-10)"; TOK_A=$(signup_login "$EMAIL_A"); req GET "$NOTI?page=0&size=20" "$TOK_A"
check "200, content=[], totalElements=0" "" "$([ "$CODE" = 200 ] && has '"content":\[\]' && [ "$(field totalElements)" = 0 ] && echo true)"
step "2. A 안읽음 수 0"; req GET $NOTI/unread-count "$TOK_A"
check "unreadCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "3. B 가입/로그인, A·B 프로필"; TOK_B=$(signup_login "$EMAIL_B")
req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\"}"
req PUT /api/v1/users/me/profile "$TOK_B" "{\"nickname\":\"$NICK_B\",\"birthDate\":\"1997-07-07\",\"genderCode\":\"F\",\"regionCode\":\"SEOUL_JUNG\"}"
USER_A=$(req GET /api/v1/users/me "$TOK_A"; field userId); USER_B=$(req GET /api/v1/users/me "$TOK_B"; field userId)
check "프로필 저장 200" "" "$([ "$CODE" = 200 ] && [ -n "$USER_A" ] && [ -n "$USER_B" ] && echo true)"
step "4. B → A 좋아요 → A 에 MATCH_LIKED (S5, userId=B)"; req POST /api/v1/matching/actions "$TOK_B" "{\"targetUserId\":$USER_A,\"actionType\":\"LIKE\"}"; req GET "$NOTI?page=0&size=20" "$TOK_A"; LIKED_ID=$(field notificationId)
check "1건, eventCode=MATCH_LIKED, type=MATCH, target S5/userId=B, read=false" "" "$([ "$CODE" = 200 ] && [ "$(items_n)" = 1 ] && [ "$(field eventCode)" = MATCH_LIKED ] && [ "$(field type)" = MATCH ] && has "\"target\":{\"screen\":\"S5\",\"matchId\":null,\"chatRoomId\":null,\"userId\":$USER_B}" && [ "$(field read)" = false ] && echo true)"
step "5. A → B 좋아요 → 매칭 성사 → 양쪽 MATCH_CREATED (S10, matchId)"; req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"; MATCH_ID=$(field matchId); ROOM_ID=$(field chatRoomId); req GET "$NOTI?page=0&size=20" "$TOK_A"; A_OK=$([ "$(field eventCode)" = MATCH_CREATED ] && has "\"target\":{\"screen\":\"S10\",\"matchId\":$MATCH_ID," && echo true); req GET "$NOTI?page=0&size=20" "$TOK_B"
check "A·B 최신 알림 MATCH_CREATED, target S10 matchId" "" "$([ "$A_OK" = true ] && [ "$(field eventCode)" = MATCH_CREATED ] && has "\"matchId\":$MATCH_ID" && echo true)"
step "6. B 메시지 전송 → A 에 MESSAGE_RECEIVED (S11, chatRoomId, 미리보기)"; req POST "/api/v1/chat/rooms/$ROOM_ID/messages" "$TOK_B" '{"messageType":"TEXT","content":"안녕하세요 반가워요"}'; req GET "$NOTI?page=0&size=20" "$TOK_A"
check "최신 MESSAGE_RECEIVED, type=MESSAGE, target S11/chatRoomId, content=미리보기" "" "$([ "$CODE" = 200 ] && [ "$(field eventCode)" = MESSAGE_RECEIVED ] && [ "$(field type)" = MESSAGE ] && has "\"target\":{\"screen\":\"S11\",\"matchId\":null,\"chatRoomId\":$ROOM_ID," && [ "$(field content)" = "안녕하세요 반가워요" ] && echo true)"
step "7. A 안읽음 3 / unreadOnly=true 3건"; req GET $NOTI/unread-count "$TOK_A"; U=$(field unreadCount); req GET "$NOTI?page=0&size=20&unreadOnly=true" "$TOK_A"
check "unreadCount=3, unreadOnly 3건" "" "$([ "$U" = 3 ] && [ "$CODE" = 200 ] && [ "$(items_n)" = 3 ] && echo true)"
step "8. A 개별 읽음 (MATCH_LIKED) → readAt, unreadCount 2"; req PUT "$NOTI/$LIKED_ID/read" "$TOK_A"
check "200, notificationId 일치, readAt 존재, unreadCount=2" "" "$([ "$CODE" = 200 ] && [ "$(field notificationId)" = "$LIKED_ID" ] && ! has '"readAt":null' && [ "$(field unreadCount)" = 2 ] && echo true)"
step "9. 같은 알림 재읽음 → 멱등"; READ_AT=$(field readAt); req PUT "$NOTI/$LIKED_ID/read" "$TOK_A"
check "200, readAt 그대로, unreadCount=2" "" "$([ "$CODE" = 200 ] && [ "$(field readAt)" = "$READ_AT" ] && [ "$(field unreadCount)" = 2 ] && echo true)"
step "10. unreadOnly 2건, 전체 3건(읽은 항목 read=true)"; req GET "$NOTI?page=0&size=20&unreadOnly=true" "$TOK_A"; N=$(items_n); req GET "$NOTI?page=0&size=20" "$TOK_A"
check "2건 / 3건, MATCH_LIKED read=true" "" "$([ "$N" = 2 ] && [ "$(items_n)" = 3 ] && has "\"notificationId\":$LIKED_ID,\"type\":\"MATCH\",\"eventCode\":\"MATCH_LIKED\"" && echo "$J" | grep -oE "\"notificationId\":$LIKED_ID,[^}]*\"read\":true" | grep -q . && echo true)"
step "11. B 가 A 의 알림 읽음 시도 / 없는 ID → 404"; req PUT "$NOTI/$LIKED_ID/read" "$TOK_B"; C1=$CODE; req PUT "$NOTI/999999999/read" "$TOK_A"
check "404 ×2 COMMON_404" "" "$([ "$C1" = 404 ] && [ "$CODE" = 404 ] && [ "$(field code)" = COMMON_404 ] && echo true)"
step "12. B 가 다음 단계 동의 → A 에 REVEAL_REQUESTED (S7-12, S10)"; req POST "/api/v1/matches/$MATCH_ID/reveal/consent" "$TOK_B" '{"revealLevel":1,"consent":true}'; C1=$CODE; req GET "$NOTI?page=0&size=20" "$TOK_A"
check "동의 200, 최신 REVEAL_REQUESTED, 제목 '???님이 다음 단계를 요청했어요', type REVEAL, target S10" "" "$([ "$C1" = 200 ] && [ "$(field eventCode)" = REVEAL_REQUESTED ] && [ "$(field title)" = "???님이 다음 단계를 요청했어요" ] && [ "$(field type)" = REVEAL ] && has "\"target\":{\"screen\":\"S10\",\"matchId\":$MATCH_ID," && echo true)"
step "13. B 매칭 그만두기 → A 에 MATCH_ENDED (S7-14, S5)"; req DELETE "/api/v1/matches/$MATCH_ID" "$TOK_B"; req GET "$NOTI?page=0&size=20" "$TOK_A"
check "최신 MATCH_ENDED, 제목 '매칭이 종료됐어요', target S5, 상대 닉네임 없음" "" "$([ "$CODE" = 200 ] && [ "$(field eventCode)" = MATCH_ENDED ] && [ "$(field title)" = "매칭이 종료됐어요" ] && has "\"target\":{\"screen\":\"S5\",\"matchId\":$MATCH_ID," && ! has "$NICK_B" && echo true)"
step "14. A 안읽음 4 (CREATED·MESSAGE·REQUESTED·ENDED)"; req GET $NOTI/unread-count "$TOK_A"
check "unreadCount=4" "" "$([ "$CODE" = 200 ] && [ "$(field unreadCount)" = 4 ] && echo true)"
step "15. A 전체 읽음 (S7-08) → updatedCount 4, unreadCount 0"; req PUT $NOTI/read-all "$TOK_A"
check "updatedCount=4, unreadCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field updatedCount)" = 4 ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "16. 전체 읽음 재호출 → updatedCount 0"; req PUT $NOTI/read-all "$TOK_A"
check "updatedCount=0" "" "$([ "$CODE" = 200 ] && [ "$(field updatedCount)" = 0 ] && echo true)"
step "17. unreadOnly=true → [] / 안읽음 0"; req GET "$NOTI?page=0&size=20&unreadOnly=true" "$TOK_A"; E=$(has '"content":\[\]' && echo true); req GET $NOTI/unread-count "$TOK_A"
check "빈 목록, unreadCount=0" "" "$([ "$E" = true ] && [ "$(field unreadCount)" = 0 ] && echo true)"
step "18. 페이지: size=2 → 2건/총 5/3페이지, 마지막 페이지 1건"; req GET "$NOTI?page=0&size=2" "$TOK_A"; P0=$([ "$(items_n)" = 2 ] && [ "$(field totalElements)" = 5 ] && [ "$(field totalPages)" = 3 ] && [ "$(field last)" = false ] && echo true); req GET "$NOTI?page=2&size=2" "$TOK_A"
check "page0: 2건·5·3·last=false, page2: 1건·last=true" "" "$([ "$P0" = true ] && [ "$(items_n)" = 1 ] && [ "$(field last)" = true ] && echo true)"
step "19. 인증 없이 목록 → 401"; req GET "$NOTI?page=0&size=20" ""
check "401" "" "$([ "$CODE" = 401 ] && echo true)"
step "20. B 목록: MATCH_CREATED 있음, 본인이 끝낸 MATCH_ENDED 없음, 안읽음 1"; req GET "$NOTI?page=0&size=20" "$TOK_B"; B_OK=$(has MATCH_CREATED && ! has MATCH_ENDED && echo true); req GET $NOTI/unread-count "$TOK_B"
check "B: CREATED 포함·ENDED 없음, unreadCount=1" "" "$([ "$B_OK" = true ] && [ "$(field unreadCount)" = 1 ] && echo true)"
rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
