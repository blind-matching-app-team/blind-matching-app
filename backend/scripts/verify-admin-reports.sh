#!/usr/bin/env bash
# BMA-76 Postman 컬렉션(docs/postman/BMA-76-admin-reports.postman_collection.json)을 curl 로 재현한다.
# S12 관리자 신고검토: 목록(대기/완료) → 반려 → 승인 → BMA-30 단계(3회 경고·5회 7일 제한·7회 영구 차단) 실제 실행 →
# 대화 종료 안내 → 감형(첫 제한 종료 시 2, 1회 한정) → 직권 제재/해제 → 감사 로그.
#   ADMIN_PROMOTE_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e" \
#   DB_EXEC_CMD="$ADMIN_PROMOTE_CMD" BASE=http://localhost:8080 bash backend/scripts/verify-admin-reports.sh
# 관리자 승격(USER_ROLE=ADMIN)과 7일 제한 종료 재현은 DB 로 한다. 둘 다 없으면 해당 단계는 건너뛴다.
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
tg() { echo "$J" | grep -oE '"target":\{[^}]*\}' | head -1 | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
mk() { local n=$1 g=$2 y=$3 r=$4; local e="bma76-$n-$TS@example.com"; eval "EMAIL_$n=$e"
  req POST /api/v1/auth/signup "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; eval "USER_$n=$(field userId)"
  req POST /api/v1/auth/login "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; local t; t=$(field accessToken); eval "TOK_$n=$t"; eval "RT_$n=$(field refreshToken)"
  req PUT /api/v1/users/me/profile "$t" "{\"nickname\":\"검토$n$N\",\"birthDate\":\"$y-05-05\",\"genderCode\":\"$g\",\"regionCode\":\"$r\"}"; }
login() { req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; }
report() { req POST /api/v1/reports "$1" "{\"targetUserId\":$2,\"reportType\":\"$3\",\"description\":\"$4\"}"; }
review() { req POST "/api/v1/admin/reports/$2/review" "$1" "$3"; }
A=/api/v1/admin

step "1. 관리자 계정(DB 승격 후 재로그인) → 목록 200 / 일반 사용자 T → 403 AUTH_005"
mk ADM M 1988 SEOUL_GANGNAM; mk T F 1996 SEOUL_JUNG
if [ -z "$ADMIN_PROMOTE_CMD" ]; then skip "관리자 승격" "ADMIN_PROMOTE_CMD 없음"; echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; exit 0; fi
eval "$ADMIN_PROMOTE_CMD \"UPDATE US_USER SET USER_ROLE='ADMIN' WHERE EMAIL='$EMAIL_ADM'\"" >/dev/null 2>&1
login "$EMAIL_ADM"; TOK_ADM=$(field accessToken); req GET "$A/reports" "$TOK_ADM"; C1=$CODE; req GET "$A/reports" "$TOK_T"
check "관리자 목록 200, 일반 사용자 403 AUTH_005" "" "$([ "$C1" = 200 ] && [ "$CODE" = 403 ] && [ "$(field code)" = AUTH_005 ] && echo true)"

step "2. R1 이 T 를 사기(FRAUD) 신고 → 즉시검토 대기 → T 새 매칭(좋아요) 409 MATCH_007 → 대기 목록에 노출(신고자·피신고자·누적 0)"
mk R1 M 1990 SEOUL_GANGNAM; report "$TOK_R1" "$USER_T" FRAUD "돈을 요구했어요"; REP1=$(field reportId); S1=$(field status)
req POST /api/v1/matching/actions "$TOK_T" "{\"targetUserId\":$USER_R1,\"actionType\":\"LIKE\"}"; C1=$CODE; R1C=$(field code)
req GET "$A/reports?status=PENDING&size=50" "$TOK_ADM"; ITEM=$(echo "$J" | perl -ne 'print $1 if /("reportId":'"$REP1"',.*?)(?:\{"reportId"|$)/')
check "PENDING_REVIEW, T 좋아요 409 MATCH_007, 목록에 reportId·immediateReview true·reporter email R1·target reportCount 0·activeSanction null" "" "$([ "$S1" = PENDING_REVIEW ] && [ "$C1" = 409 ] && [ "$R1C" = MATCH_007 ] && [ "$CODE" = 200 ] && echo "$ITEM" | grep -q '"immediateReview":true' && echo "$ITEM" | grep -q "\"email\":\"$EMAIL_R1\"" && echo "$ITEM" | grep -q '"reportCount":0' && echo "$ITEM" | grep -q '"activeSanction":null' && echo true)"

step "3. 상세 조회 → 신고 정보, 피신고자 이력 1건, 제재 없음, 감사 로그 없음 / 없는 신고 → 404 SAFE_003"
req GET "$A/reports/$REP1" "$TOK_ADM"; D_OK=$([ "$CODE" = 200 ] && [ "$(field reportId)" = "$REP1" ] && [ "$(field reportTypeName)" = 사기 ] && has '"targetReportHistory":\[{' && has '"targetSanctions":\[\]' && has '"auditLogs":\[\]' && echo true); req GET "$A/reports/999999999" "$TOK_ADM"
check "200 상세(사기, 이력 1, 제재 0, 감사 로그 0), 404 SAFE_003" "" "$([ "$D_OK" = true ] && [ "$CODE" = 404 ] && [ "$(field code)" = SAFE_003 ] && echo true)"

step "4. 반려(S12-07) → REJECTED·조치 없음·누적 0 → T 매칭 제한 해제(좋아요 200) → 대기 목록에서 빠지고 완료 탭에 노출 → 감사 로그 REPORT_REJECT"
review "$TOK_ADM" "$REP1" '{"decision":"REJECT","note":"근거 부족"}'; RV_OK=$([ "$CODE" = 200 ] && [ "$(field status)" = REJECTED ] && [ "$(field actionTaken)" = NONE ] && [ "$(field reportCountAfter)" = 0 ] && has '"sanction":null' && echo true)
req POST /api/v1/matching/actions "$TOK_T" "{\"targetUserId\":$USER_R1,\"actionType\":\"LIKE\"}"; C2=$CODE
req GET "$A/reports?status=PENDING&size=50" "$TOK_ADM"; P_HAS=$(echo "$J" | grep -c "\"reportId\":$REP1,"); req GET "$A/reports?status=DONE&size=50" "$TOK_ADM"; D_HAS=$(echo "$J" | grep -c "\"reportId\":$REP1,")
req GET "$A/audit-logs?reportId=$REP1" "$TOK_ADM"
check "REJECTED/NONE/0, 좋아요 200, PENDING 제외·DONE 포함, 감사 로그 1건 REPORT_REJECT(adminEmail)" "" "$([ "$RV_OK" = true ] && [ "$C2" = 200 ] && [ "$P_HAS" = 0 ] && [ "$D_HAS" = 1 ] && [ "$CODE" = 200 ] && [ "$(field totalElements)" = 1 ] && [ "$(field actionCode)" = REPORT_REJECT ] && [ "$(field adminEmail)" = "$EMAIL_ADM" ] && echo true)"

step "5. 일반 누적: R2 욕설·R3 허위프로필(자동 반영 2회) → R4 기타 3회째 검토 대기(목록 reportCount 2) → 승인 → 3회 경고 자동 실행"
mk R2 M 1991 SEOUL_GANGNAM; mk R3 M 1992 SEOUL_GANGNAM; mk R4 M 1993 SEOUL_GANGNAM
report "$TOK_R2" "$USER_T" ABUSE "욕설"; report "$TOK_R3" "$USER_T" FAKE "사진 다름"; report "$TOK_R4" "$USER_T" ETC "기타 사유"; REP4=$(field reportId); S4=$(field status)
req GET "$A/reports/$REP4" "$TOK_ADM"; RC=$(tg reportCount)
review "$TOK_ADM" "$REP4" '{"decision":"APPROVE","note":"누적 확인"}'
check "3회째 PENDING_REVIEW, 상세 reportCount 2, 승인 → actionTaken WARNING·sanction.type WARNING·reportCountAfter 3·conversationEnded false" "" "$([ "$S4" = PENDING_REVIEW ] && [ "$RC" = 2 ] && [ "$CODE" = 200 ] && [ "$(field actionTaken)" = WARNING ] && has '"type":"WARNING"' && [ "$(field reportCountAfter)" = 3 ] && [ "$(field conversationEnded)" = false ] && echo true)"

step "6. 경고 실행 확인: T 알림 WARNING_ISSUED, T 제재 이력 1건(WARNING, effective), 감사 로그 REPORT_APPROVE + SANCTION_WARNING"
req GET "/api/v1/notifications?page=0&size=1" "$TOK_T"; EV=$(field eventCode); req GET "$A/users/$USER_T/sanctions" "$TOK_ADM"; SN=$(cnt sanctionId); ST=$(field type); req GET "$A/audit-logs?reportId=$REP4" "$TOK_ADM"
check "WARNING_ISSUED, 제재 1건 WARNING effective true, 감사 로그 2건(APPROVE·SANCTION_WARNING)" "" "$([ "$EV" = WARNING_ISSUED ] && [ "$SN" = 1 ] && [ "$ST" = WARNING ] && has '"effective":true' && [ "$(field totalElements)" = 2 ] && has '"actionCode":"SANCTION_WARNING"' && has '"actionCode":"REPORT_APPROVE"' && echo true)"

step "7. 검증: 처리된 신고 재검토 → 409 SAFE_002 / 자동 반영(COUNTED) 신고 검토 → 409 / decision 오류 → 400 / 일반 사용자 검토 → 403"
review "$TOK_ADM" "$REP4" '{"decision":"APPROVE"}'; C1=$CODE; R1C=$(field code)
req GET "$A/reports?status=ALL&size=50" "$TOK_ADM"; REP2=$(echo "$J" | grep -oE "\\{\"reportId\":[0-9]+,\"reportType\":\"ABUSE\"[^}]*\"status\":\"COUNTED\"" | head -1 | grep -oE "[0-9]+" | head -1)
review "$TOK_ADM" "${REP2:-0}" '{"decision":"APPROVE"}'; C2=$CODE; review "$TOK_ADM" "$REP4" '{"decision":"MAYBE"}'; C3=$CODE; review "$TOK_T" "$REP4" '{"decision":"APPROVE"}'
check "409 SAFE_002, 409, 400, 403" "" "$([ "$C1" = 409 ] && [ "$R1C" = SAFE_002 ] && [ "$C2" = 409 ] && [ "$C3" = 400 ] && [ "$CODE" = 403 ] && echo true)"

step "8. 매칭 중 신고 승인 → '관리자에 의해 대화 종료': R5·T 매칭 → R5 메시지·신고(4회째) → 승인(조치 없음) → 매칭 종료·방 ENDED·시스템 메시지·T 알림 MATCH_ENDED"
mk R5 M 1994 SEOUL_GANGNAM
req POST /api/v1/matching/actions "$TOK_T" "{\"targetUserId\":$USER_R5,\"actionType\":\"LIKE\"}"; req POST /api/v1/matching/actions "$TOK_R5" "{\"targetUserId\":$USER_T,\"actionType\":\"LIKE\"}"; MATCH=$(field matchId); ROOM=$(field chatRoomId)
req POST "/api/v1/chat/rooms/$ROOM/messages" "$TOK_R5" '{"messageType":"TEXT","content":"안녕하세요"}'; req POST "/api/v1/chat/rooms/$ROOM/messages" "$TOK_T" '{"messageType":"TEXT","content":"돈 좀 빌려줘"}'; MSG=$(field messageId)
req POST /api/v1/reports "$TOK_R5" "{\"targetUserId\":$USER_T,\"reportType\":\"ABUSE\",\"matchId\":$MATCH,\"messageId\":$MSG}"; REP5=$(field reportId); S5=$(field status)
review "$TOK_ADM" "$REP5" '{"decision":"APPROVE"}'; RV_OK=$([ "$CODE" = 200 ] && [ "$(field actionTaken)" = NONE ] && [ "$(field conversationEnded)" = true ] && [ "$(field reportCountAfter)" = 4 ] && [ "$(field messagePreview)" = "돈 좀 빌려줘" ] && echo true)
req GET /api/v1/matches "$TOK_T"; NM=$(cnt matchId); req GET /api/v1/chat/rooms "$TOK_T"; RS=$(field status); LM=$(field lastMessage); LT=$(field lastMessageType)
req GET "/api/v1/notifications?page=0&size=1" "$TOK_T"; EV=$(field eventCode); req GET "$A/audit-logs?reportId=$REP5" "$TOK_ADM"
check "PENDING(4회째), 승인 NONE·conversationEnded true·누적 4·messagePreview, T 매칭 0, 방 ENDED·마지막 SYSTEM '관리자에 의해 대화가 종료되었습니다.', MATCH_ENDED, 감사 CONVERSATION_END" "" "$([ "$S5" = PENDING_REVIEW ] && [ "$RV_OK" = true ] && [ "$NM" = 0 ] && [ "$RS" = ENDED ] && [ "$LM" = "관리자에 의해 대화가 종료되었습니다." ] && [ "$LT" = SYSTEM ] && [ "$EV" = MATCH_ENDED ] && has '"actionCode":"CONVERSATION_END"' && echo true)"

step "9. 5회째 승인 → 7일 이용 제한: sanction SUSPEND(endDate ≈ +7일), T 로그인 403 AUTH_009 TEMPORARY, 옛 리프레시 토큰 401, 완료 목록 target.userStatus SUSPENDED"
mk R6 M 1995 SEOUL_GANGNAM; report "$TOK_R6" "$USER_T" ETC "또 기타"; REP6=$(field reportId)
review "$TOK_ADM" "$REP6" '{"decision":"APPROVE","note":"5회"}'; AT=$(field actionTaken); END=$(field endDate); SUSP_ID=$(field sanctionId); EXP=$(date -u -d "+7 days" +%Y-%m-%d 2>/dev/null || date -v+7d +%Y-%m-%d)
login "$EMAIL_T"; C1=$CODE; R1C=$(field code); RT_TYPE=$(field restrictionType); UNTIL=$(field restrictedUntil)
req POST /api/v1/auth/refresh "" "{\"refreshToken\":\"$RT_T\"}"; C2=$CODE; req GET "$A/reports/$REP6" "$TOK_ADM"; US=$(tg userStatus)
check "SUSPEND·endDate 7일 뒤, 로그인 403 AUTH_009 TEMPORARY restrictedUntil, refresh 401, target SUSPENDED" "" "$([ "$AT" = SUSPEND ] && [ "${END:0:10}" \> "$(date +%Y-%m-%d)" ] && [ "$C1" = 403 ] && [ "$R1C" = AUTH_009 ] && [ "$RT_TYPE" = TEMPORARY ] && [ -n "$UNTIL" ] && [ "$C2" = 401 ] && [ "$US" = SUSPENDED ] && echo true)"

step "10. 감형(1회 한정): 첫 7일 제한 종료(DB 로 재현) → T 로그인 200·ACTIVE(자동 해제) → 누적 5 → 3"
if [ -n "$DB_EXEC_CMD" ]; then
  eval "$DB_EXEC_CMD \"UPDATE SF_USER_SANCTION SET END_DATE = DATE_SUB(NOW(6), INTERVAL 1 HOUR) WHERE SANCTION_ID=$SUSP_ID\"" >/dev/null 2>&1
  login "$EMAIL_T"; C1=$CODE; TOK_T=$(field accessToken); RT_T=$(field refreshToken); req GET /api/v1/users/me "$TOK_T"; US=$(field userStatus); req GET "$A/reports/$REP6" "$TOK_ADM"; RC=$(tg reportCount)
  check "로그인 200, userStatus ACTIVE, reportCount 3" "" "$([ "$C1" = 200 ] && [ "$US" = ACTIVE ] && [ "$RC" = 3 ] && echo true)"
else skip "감형" "DB_EXEC_CMD 없음"; fi

step "11. 직권 제재/해제: BAN → 로그인 403 PERMANENT → 해제 → 로그인 200 / 종류 오류 400 / 없는 사용자 404"
req POST "$A/users/$USER_T/sanctions" "$TOK_ADM" '{"type":"BAN","reason":"직권 영구 차단 테스트"}'; C1=$CODE; BAN_ID=$(field sanctionId); login "$EMAIL_T"; C2=$CODE; P2=$(field restrictionType)
req DELETE "$A/sanctions/$BAN_ID" "$TOK_ADM"; C3=$CODE; ACT=$(field active); login "$EMAIL_T"; C4=$CODE; TOK_T=$(field accessToken)
req POST "$A/users/$USER_T/sanctions" "$TOK_ADM" '{"type":"MUTE","reason":"x"}'; C5=$CODE; req POST "$A/users/999999999/sanctions" "$TOK_ADM" '{"type":"WARNING","reason":"x"}'
check "BAN 200 → 403 PERMANENT → 해제 200 active false → 로그인 200, 400, 404" "" "$([ "$C1" = 200 ] && [ "$C2" = 403 ] && [ "$P2" = PERMANENT ] && [ "$C3" = 200 ] && [ "$ACT" = false ] && [ "$C4" = 200 ] && [ "$C5" = 400 ] && [ "$CODE" = 404 ] && echo true)"

step "12. 두 번째 제한은 감형 없음: R7(4회, 조치 없음)·R8(5회 → SUSPEND) → 제한 종료(DB) → 로그인 200, 누적 5 유지"
mk R7 M 1996 SEOUL_GANGNAM; mk R8 M 1997 SEOUL_GANGNAM
report "$TOK_R7" "$USER_T" ABUSE "7"; REP7=$(field reportId); review "$TOK_ADM" "$REP7" '{"decision":"APPROVE"}'; A7=$(field actionTaken); N7=$(field reportCountAfter)
report "$TOK_R8" "$USER_T" ABUSE "8"; REP8=$(field reportId); review "$TOK_ADM" "$REP8" '{"decision":"APPROVE"}'; A8=$(field actionTaken); SUSP2=$(field sanctionId)
if [ -n "$DB_EXEC_CMD" ]; then
  eval "$DB_EXEC_CMD \"UPDATE SF_USER_SANCTION SET END_DATE = DATE_SUB(NOW(6), INTERVAL 1 HOUR) WHERE SANCTION_ID=$SUSP2\"" >/dev/null 2>&1
  login "$EMAIL_T"; C1=$CODE; req GET "$A/reports/$REP8" "$TOK_ADM"; RC=$(tg reportCount)
  check "4회 NONE, 5회 SUSPEND, 종료 후 로그인 200, reportCount 5(감형 없음)" "" "$([ "$A7" = NONE ] && [ "$N7" = 4 ] && [ "$A8" = SUSPEND ] && [ "$C1" = 200 ] && [ "$RC" = 5 ] && echo true)"
else skip "두 번째 제한 종료" "DB_EXEC_CMD 없음"; fi

step "13. 7회 영구 차단: R9(6회, 조치 없음)·R10(7회 → BAN) → 로그인 403 PERMANENT / 관리자 지정 조치(action=WARNING)로 단계 무시 가능"
mk R9 M 1998 SEOUL_GANGNAM; mk R10 M 1999 SEOUL_GANGNAM
report "$TOK_R9" "$USER_T" ETC "9"; REP9=$(field reportId); review "$TOK_ADM" "$REP9" '{"decision":"APPROVE"}'; A9=$(field actionTaken)
report "$TOK_R10" "$USER_T" ETC "10"; REP10=$(field reportId); review "$TOK_ADM" "$REP10" '{"decision":"APPROVE"}'; A10=$(field actionTaken); N10=$(field reportCountAfter)
login "$EMAIL_T"; C1=$CODE; P1=$(field restrictionType)
mk R11 M 1989 SEOUL_GANGNAM; mk U2 F 1997 SEOUL_JUNG; report "$TOK_R11" "$USER_U2" FRAUD "직권 경고만"; REP11=$(field reportId); review "$TOK_ADM" "$REP11" '{"decision":"APPROVE","action":"WARNING","note":"경고로 종결"}'
check "6회 NONE, 7회 BAN(누적 7), 로그인 403 PERMANENT, 지정 조치 WARNING(누적 1)" "" "$([ "$A9" = NONE ] && [ "$A10" = BAN ] && [ "$N10" = 7 ] && [ "$C1" = 403 ] && [ "$P1" = PERMANENT ] && [ "$CODE" = 200 ] && [ "$(field actionTaken)" = WARNING ] && [ "$(field reportCountAfter)" = 1 ] && echo true)"

step "14. 감사 로그(S12-08): T 대상 로그에 승인·반려·경고·제한·차단·해제·대화 종료가 모두 남음 / 인증 없이 → 401"
req GET "$A/audit-logs?targetUserId=$USER_T&size=50" "$TOK_ADM"; TE=$(field totalElements); ALL_OK=true
for code in REPORT_REJECT REPORT_APPROVE SANCTION_WARNING SANCTION_SUSPEND SANCTION_BAN SANCTION_LIFT CONVERSATION_END; do has "\"actionCode\":\"$code\"" || ALL_OK=false; done
req GET "$A/audit-logs" ""
check "totalElements ≥ 12, 7종 actionCode 모두 존재, 401" "" "$([ "${TE:-0}" -ge 12 ] && [ "$ALL_OK" = true ] && [ "$CODE" = 401 ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
