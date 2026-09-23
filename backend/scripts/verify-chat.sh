#!/usr/bin/env bash
# BMA-72 Postman 컬렉션(docs/postman/BMA-72-chat.postman_collection.json)을 curl 로 재현한다.
# S11 채팅 REST: 메시지 이력(페이징·참여자 검증) → 신고 5종(BMA-30: 자동 반영/관리자 검토/즉시검토) →
# 매칭 1건당 1회 중복 규칙 → 즉시검토 대기자의 새 매칭 진입 차단(MATCH_007) → 차단 시 S11-08(숨김 메시지·차단자만 퇴장).
#   BASE=http://localhost:8080 bash backend/scripts/verify-chat.sh
# 실시간(WebSocket) 검증은 backend/scripts/verify-ws-chat.py 가 담당한다.
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
cnt() { echo "$J" | grep -o "\"$1\"" | wc -l | tr -d ' '; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
# S15 본인인증(BMA-79): 매칭 대기열 진입 전 필수. 스텁 인증사에 성인 결과를 보내 완료시킨다.
verify_id() { local t=$1 tag=$2; req POST /api/v1/verification/identity/request "$t"; local tx; tx=$(field transactionId); req POST /api/v1/verification/identity/confirm "$t" "{\"transactionId\":\"$tx\",\"providerPayload\":{\"result\":{\"name\":\"홍길동\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"phoneNumber\":\"01000000000\",\"ci\":\"ci-$tag\"}}}"; }
mk() { local n=$1 g=$2 y=$3 r=$4; local e="bma72-$n-$TS@example.com"
  req POST /api/v1/auth/signup "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; eval "USER_$n=$(field userId)"
  req POST /api/v1/auth/login "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; local t; t=$(field accessToken); eval "TOK_$n=$t"
  req PUT /api/v1/users/me/profile "$t" "{\"nickname\":\"챗$n$N\",\"birthDate\":\"$y-05-05\",\"genderCode\":\"$g\",\"regionCode\":\"$r\"}"; verify_id "$t" "$e"; }
match() { req POST /api/v1/matching/actions "$2" "{\"targetUserId\":$3,\"actionType\":\"LIKE\"}"; req POST /api/v1/matching/actions "$1" "{\"targetUserId\":$4,\"actionType\":\"LIKE\"}"; }
send() { req POST "/api/v1/chat/rooms/$2/messages" "$1" "{\"messageType\":\"TEXT\",\"content\":\"$3\"}"; }
report() { req POST /api/v1/reports "$1" "$2"; }

step "1. A(남)·B(여) 매칭 → 채팅방, B 2건·A 3건 전송 (총 5)"
mk A M 1995 SEOUL_GANGNAM; mk B F 1997 SEOUL_JUNG
match "$TOK_A" "$TOK_B" "$USER_A" "$USER_B"; MATCH1=$(field matchId); ROOM1=$(field chatRoomId)
send "$TOK_B" "$ROOM1" "안녕하세요"; send "$TOK_B" "$ROOM1" "반가워요"; send "$TOK_A" "$ROOM1" "네 안녕하세요"; MSG_A1=$(field messageId); send "$TOK_A" "$ROOM1" "오늘 날씨 좋네요"; send "$TOK_A" "$ROOM1" "주말에 뭐 하세요?"; MSG_A3=$(field messageId)
check "매칭·방 생성, 5건 전송 200" "" "$([ -n "$ROOM1" ] && [ "$CODE" = 200 ] && [ -n "$MSG_A3" ] && echo true)"

step "2. 이력 조회 page=0&size=2 → 최신순 2건, totalElements 5, totalPages 3, last false"
req GET "/api/v1/chat/rooms/$ROOM1/messages?page=0&size=2" "$TOK_A"; FIRST=$(field messageId); SECOND=$(echo "$J" | grep -oE '"messageId":[0-9]+' | sed -n 2p | cut -d: -f2)
check "200, content 2건, 첫 항목이 마지막 전송(ID 큰 순), totalElements 5, totalPages 3, last false, senderUserId 포함" "" "$([ "$CODE" = 200 ] && [ "$(cnt messageId)" = 2 ] && [ "$FIRST" = "$MSG_A3" ] && [ "$FIRST" -gt "$SECOND" ] && [ "$(field totalElements)" = 5 ] && [ "$(field totalPages)" = 3 ] && [ "$(field last)" = false ] && has '"senderUserId"' && echo true)"

step "3. 마지막 페이지 page=2 → 1건·last true / size=500 은 100 으로 절삭 / 기본값(page 0, size 30)"
req GET "/api/v1/chat/rooms/$ROOM1/messages?page=2&size=2" "$TOK_A"; C1=$CODE; N1=$(cnt messageId); L1=$(field last); req GET "/api/v1/chat/rooms/$ROOM1/messages?size=500" "$TOK_B"; S2=$(field size); req GET "/api/v1/chat/rooms/$ROOM1/messages" "$TOK_B"
check "page 2: 1건 last true / size 100 / 기본 size 30 에 5건" "" "$([ "$C1" = 200 ] && [ "$N1" = 1 ] && [ "$L1" = true ] && [ "$S2" = 100 ] && [ "$CODE" = 200 ] && [ "$(field size)" = 30 ] && [ "$(cnt messageId)" = 5 ] && echo true)"

step "4. 비참여자 C → 403 CHAT_002 / 인증 없이 → 401 / 없는 방 → 403 CHAT_002 (존재 여부 비노출)"
mk C M 1990 SEOUL_GANGNAM; req GET "/api/v1/chat/rooms/$ROOM1/messages" "$TOK_C"; C1=$CODE; R1=$(field code); req GET "/api/v1/chat/rooms/$ROOM1/messages" ""; C2=$CODE; req GET "/api/v1/chat/rooms/999999999/messages" "$TOK_A"
check "403 CHAT_002, 401, 403 CHAT_002" "" "$([ "$C1" = 403 ] && [ "$R1" = CHAT_002 ] && [ "$C2" = 401 ] && [ "$CODE" = 403 ] && [ "$(field code)" = CHAT_002 ] && echo true)"

step "5. B 가 A 를 욕설(ABUSE)로 신고 (POST /reports, matchId·messageId 지정) → COUNTED·NORMAL, 신고자만 채팅방 퇴장"
report "$TOK_B" "{\"targetUserId\":$USER_A,\"reportType\":\"ABUSE\",\"description\":\"욕설을 했어요\",\"matchId\":$MATCH1,\"messageId\":$MSG_A1}"; REPORT1=$(field reportId)
R_OK=$([ "$CODE" = 200 ] && [ -n "$REPORT1" ] && [ "$(field status)" = COUNTED ] && [ "$(field severity)" = NORMAL ] && [ "$(field immediateReview)" = false ] && [ "$(field matchId)" = "$MATCH1" ] && [ "$(field chatRoomLeft)" = true ] && echo true)
req GET /api/v1/chat/rooms "$TOK_B"; NB=$(cnt chatRoomId); req GET /api/v1/chat/rooms "$TOK_A"; NA=$(cnt chatRoomId); SA=$(field status); req GET /api/v1/matches "$TOK_A"
check "200 COUNTED/NORMAL/immediateReview false/chatRoomLeft true, B 방 목록 0, A 방 목록 1(ACTIVE), A 매칭 목록 유지" "" "$([ "$R_OK" = true ] && [ "$NB" = 0 ] && [ "$NA" = 1 ] && [ "$SA" = ACTIVE ] && [ "$(field matchId)" = "$MATCH1" ] && echo true)"

step "6. 같은 매칭 재신고 → 409 SAFE_001 (matchId 생략해도 두 사람의 매칭으로 해석)"
report "$TOK_B" "{\"targetUserId\":$USER_A,\"reportType\":\"FAKE\"}"
check "409 SAFE_001" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = SAFE_001 ] && echo true)"

step "7. 근거 검증: 신고자 본인 메시지 ID → 400 / 남의 매칭 ID → 400 / 유형 오타 → 400 / 자기 신고 → 400 MATCH_001 / 없는 사용자 → 404 USER_001 / targetUserId 누락 → 400"
req POST /api/v1/reports "$TOK_A" "{\"targetUserId\":$USER_B,\"reportType\":\"ABUSE\",\"messageId\":$MSG_A1}"; C1=$CODE
req POST /api/v1/reports "$TOK_C" "{\"targetUserId\":$USER_A,\"reportType\":\"ABUSE\",\"matchId\":$MATCH1}"; C2=$CODE
req POST /api/v1/reports "$TOK_C" "{\"targetUserId\":$USER_A,\"reportType\":\"SPAM\"}"; C3=$CODE
req POST /api/v1/reports "$TOK_C" "{\"targetUserId\":$USER_C,\"reportType\":\"ETC\"}"; C4=$CODE; R4=$(field code)
req POST /api/v1/reports "$TOK_C" "{\"targetUserId\":999999999,\"reportType\":\"ETC\"}"; C5=$CODE; R5=$(field code)
req POST /api/v1/reports "$TOK_C" "{\"reportType\":\"ETC\"}"
check "400, 400, 400, 400 MATCH_001, 404 USER_001, 400" "" "$([ "$C1" = 400 ] && [ "$C2" = 400 ] && [ "$C3" = 400 ] && [ "$C4" = 400 ] && [ "$R4" = MATCH_001 ] && [ "$C5" = 404 ] && [ "$R5" = USER_001 ] && [ "$CODE" = 400 ] && echo true)"

step "8. 일반 유형 누적: D 를 C(허위프로필)·E(기타)가 신고 → 1·2회 COUNTED, A(욕설) 3회째 → PENDING_REVIEW(관리자 검토), 즉시검토는 아님"
mk D F 1996 SEOUL_JUNG; mk E M 1992 SEOUL_GANGNAM
report "$TOK_C" "{\"targetUserId\":$USER_D,\"reportType\":\"FAKE\",\"description\":\"사진이 달라요\"}"; S1=$(field status)
report "$TOK_E" "{\"targetUserId\":$USER_D,\"reportType\":\"ETC\"}"; S2=$(field status)
report "$TOK_A" "{\"targetUserId\":$USER_D,\"reportType\":\"ABUSE\"}"
check "COUNTED, COUNTED, 3회째 PENDING_REVIEW·severity NORMAL·immediateReview false·matchId null" "" "$([ "$S1" = COUNTED ] && [ "$S2" = COUNTED ] && [ "$CODE" = 200 ] && [ "$(field status)" = PENDING_REVIEW ] && [ "$(field severity)" = NORMAL ] && [ "$(field immediateReview)" = false ] && has '"matchId":null' && echo true)"

step "9. 중대 유형: C 가 H 를 사기(FRAUD), E 가 J 를 부적절한 콘텐츠(SEXUAL)로 신고 → 1회로 즉시 관리자 검토(PENDING_REVIEW·SEVERE·immediateReview true)"
mk H M 1993 SEOUL_GANGNAM; mk J F 1994 SEOUL_JUNG
report "$TOK_C" "{\"targetUserId\":$USER_H,\"reportType\":\"FRAUD\",\"description\":\"돈을 요구했어요\"}"; F_OK=$([ "$CODE" = 200 ] && [ "$(field status)" = PENDING_REVIEW ] && [ "$(field severity)" = SEVERE ] && [ "$(field immediateReview)" = true ] && echo true)
report "$TOK_E" "{\"targetUserId\":$USER_J,\"reportType\":\"SEXUAL\"}"
check "FRAUD·SEXUAL 모두 PENDING_REVIEW/SEVERE/immediateReview true" "" "$([ "$F_OK" = true ] && [ "$CODE" = 200 ] && [ "$(field status)" = PENDING_REVIEW ] && [ "$(field severity)" = SEVERE ] && [ "$(field immediateReview)" = true ] && echo true)"

step "10. 즉시검토 대기 H: 대기열 진입 → 409 MATCH_007 / 좋아요 → 409 MATCH_007 / 앱은 평소처럼(내 정보·채팅 목록 200)"
req POST /api/v1/matching/queue "$TOK_H"; C1=$CODE; R1=$(field code); req POST /api/v1/matching/actions "$TOK_H" "{\"targetUserId\":$USER_J,\"actionType\":\"LIKE\"}"; C2=$CODE; R2=$(field code)
req GET /api/v1/users/me "$TOK_H"; C3=$CODE; req GET /api/v1/chat/rooms "$TOK_H"
check "409 MATCH_007 ×2, 200 ×2" "" "$([ "$C1" = 409 ] && [ "$R1" = MATCH_007 ] && [ "$C2" = 409 ] && [ "$R2" = MATCH_007 ] && [ "$C3" = 200 ] && [ "$CODE" = 200 ] && echo true)"

step "11. 일반 3회 검토 대기 D 는 새 매칭 가능(즉시검토 아님): 대기열 진입 → 200 (MATCH_007 아님) → 정리(취소)"
req POST /api/v1/matching/queue "$TOK_D"; C1=$CODE; R1=$(field code); req DELETE /api/v1/matching/queue "$TOK_D"
check "200 (MATCH_007 아님), 취소 200" "" "$([ "$C1" = 200 ] && [ "$R1" != MATCH_007 ] && [ "$CODE" = 200 ] && echo true)"

step "12. (구) 경로 POST /users/{id}/report 호환: E 가 H 를 기타로 신고 → 200 COUNTED (H 의 누적은 사기 검토와 별개로 1회)"
req POST "/api/v1/users/$USER_H/report" "$TOK_E" '{"reportType":"ETC","description":"구 경로"}'
check "200, status COUNTED, targetUserId=H" "" "$([ "$CODE" = 200 ] && [ "$(field status)" = COUNTED ] && [ "$(field targetUserId)" = "$USER_H" ] && echo true)"

step "13. S11-08 차단: K(남)·L(여) 매칭 → K 가 L 차단 → K 만 퇴장(K 방 0·매칭 0), L 은 무변화(매칭 ACTIVE·현재 매칭 유지)"
mk K M 1991 SEOUL_GANGNAM; mk L F 1998 SEOUL_JUNG
match "$TOK_K" "$TOK_L" "$USER_K" "$USER_L"; MATCH2=$(field matchId); ROOM2=$(field chatRoomId)
send "$TOK_K" "$ROOM2" "차단 전 메시지"
req POST "/api/v1/users/$USER_L/block" "$TOK_K" '{"reason":"불편해서"}'; B_OK=$([ "$CODE" = 200 ] && [ "$(field blocked)" = true ] && [ "$(field chatRoomLeft)" = true ] && echo true)
req GET /api/v1/chat/rooms "$TOK_K"; NK=$(cnt chatRoomId); req GET /api/v1/matches "$TOK_K"; MK=$(cnt matchId)
req GET /api/v1/matches "$TOK_L"; ML=$(cnt matchId); SL=$(field matchStatus); req GET /api/v1/matches/current "$TOK_L"
check "차단 200 chatRoomLeft true, K 방 0·매칭 0, L 매칭 1 ACTIVE, L 현재 매칭 hasMatch true" "" "$([ "$B_OK" = true ] && [ "$NK" = 0 ] && [ "$MK" = 0 ] && [ "$ML" = 1 ] && [ "$SL" = ACTIVE ] && [ "$(field hasMatch)" = true ] && [ "$(field matchId)" = "$MATCH2" ] && echo true)"

step "14. 차단당한 L 이 전송 → 200(정상처럼 보임), L 이력·미리보기엔 있음, K 는 방에 다시 나타나지 않고 안읽음 0·알림 없음, K 전송은 403(퇴장)"
send "$TOK_L" "$ROOM2" "잘 지내세요?"; C1=$CODE; HID=$(field messageId)
req GET "/api/v1/chat/rooms/$ROOM2/messages" "$TOK_L"; TL=$(field totalElements); req GET /api/v1/chat/rooms "$TOK_L"; PL=$(field lastMessage)
req GET /api/v1/chat/rooms "$TOK_K"; NK=$(cnt chatRoomId); req GET /api/v1/chat/rooms/unread-count "$TOK_K"; UK=$(field unreadCount)
req GET "/api/v1/notifications?page=0&size=1" "$TOK_K"; EV=$(field eventCode); send "$TOK_K" "$ROOM2" "나는 못 보냄"
check "L 전송 200, L 이력 2건·미리보기 '잘 지내세요?', K 방 0·안읽음 0·최신 알림 MESSAGE_RECEIVED 아님, K 전송 403 CHAT_002" "" "$([ "$C1" = 200 ] && [ -n "$HID" ] && [ "$TL" = 2 ] && [ "$PL" = "잘 지내세요?" ] && [ "$NK" = 0 ] && [ "$UK" = 0 ] && [ "$EV" != MESSAGE_RECEIVED ] && [ "$CODE" = 403 ] && [ "$(field code)" = CHAT_002 ] && echo true)"

step "15. 차단 해제는 방을 되돌리지 않음 → L 이 다시 전송하면 K 재입장(카카오 패턴), K 이력엔 숨김 메시지가 빠져 2건(차단 전 1 + 해제 후 1), 안읽음 1"
req DELETE "/api/v1/users/$USER_L/block" "$TOK_K"; C1=$CODE; req GET /api/v1/chat/rooms "$TOK_K"; NK=$(cnt chatRoomId)
send "$TOK_L" "$ROOM2" "다시 연락드려요"; req GET /api/v1/chat/rooms "$TOK_K"; NK2=$(cnt chatRoomId); UK=$(field unreadCount)
req GET "/api/v1/chat/rooms/$ROOM2/messages" "$TOK_K"; TK=$(field totalElements); HAS_HIDDEN=$(echo "$J" | grep -c "\"messageId\":$HID,"); req GET "/api/v1/chat/rooms/$ROOM2/messages" "$TOK_L"
check "해제 200, K 방 0 → 재전송 후 1·unread 1, K 이력 2건(숨김 제외), L 이력 3건(본인 숨김 포함)" "" "$([ "$C1" = 200 ] && [ "$NK" = 0 ] && [ "$NK2" = 1 ] && [ "$UK" = 1 ] && [ "$TK" = 2 ] && [ "$HAS_HIDDEN" = 0 ] && [ "$(field totalElements)" = 3 ] && echo true)"

step "16. 신고 후에도 상대는 그대로 대화 가능: A 가 B 에게 전송 → 200, B 는 방 재등장(나가기와 같은 규칙), A 는 여전히 B 를 볼 수 있음"
send "$TOK_A" "$ROOM1" "혹시 계세요?"; C1=$CODE; req GET /api/v1/chat/rooms "$TOK_B"
check "A 전송 200, B 방 1·unread 1" "" "$([ "$C1" = 200 ] && [ "$CODE" = 200 ] && [ "$(cnt chatRoomId)" = 1 ] && [ "$(field unreadCount)" = 1 ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
