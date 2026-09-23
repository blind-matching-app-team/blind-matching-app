#!/usr/bin/env bash
# BMA-82 Postman 컬렉션(docs/postman/BMA-82-photo.postman_collection.json)을 curl 로 재현한다.
# S16 사진인증: 상태 → 프로필 사진 없이 대조(409) → 프로필 사진 등록 → 다른 사진(대조 실패 422, 재시도 무제한) → 같은 사진(통과) →
# 배지 노출(내 정보·S8 프로필·S5/S10 카드) → 미인증 상대 알림(S7-11) → 사진 교체 시 인증 해제 → 셀피 미저장 확인(DB_EXEC_CMD).
#   BASE=http://localhost:8080 bash backend/scripts/verify-photo.sh
# 얼굴 대조는 스텁(app.verification.photo.provider=stub): 프로필 사진과 같은 파일이면 100, 아니면 0.
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
PW="Passw0rd!23"; N=$((TS % 10000))
PNG=${PNG:-"$(cd "$(dirname "$0")/.." && pwd)/docs/postman/sample-profile.png"}
PASS=0; FAIL=0; SKIP=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
upload() { local t=$1 p=$2 f=$3 ct=${4:-image/png}; CODE=$(curl -s -o /tmp/body.$$ -w '%{http_code}' -X POST "$BASE$p" -H "Authorization: Bearer $t" -F "file=@$f;type=$ct"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
pt() { echo "$J" | grep -oE '"partner":\{[^}]*\}' | head -1 | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
mk() { local n=$1 g=$2 y=$3 r=$4; local e="bma82-$n-$TS@example.com"; eval "EMAIL_$n=$e"
  req POST /api/v1/auth/signup "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; eval "USER_$n=$(field userId)"
  req POST /api/v1/auth/login "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; local t; t=$(field accessToken); eval "TOK_$n=$t"
  req PUT /api/v1/users/me/profile "$t" "{\"nickname\":\"사진$n$N\",\"birthDate\":\"$y-05-05\",\"genderCode\":\"$g\",\"regionCode\":\"$r\"}"; }
V=/api/v1/verification/photo; IMG=/api/v1/users/me/image
# 다른 사진: 1×1 PNG 를 즉석에서 만든다(프로필 사진과 바이트가 다름).
OTHER=/tmp/other-$$.png; printf '\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\x0aIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\x0d\x0a\x2d\xb4\x00\x00\x00\x00IEND\xaeB`\x82' > "$OTHER"
NOTIMG=/tmp/notimg-$$.png; printf 'this is not an image, just text padding......' > "$NOTIMG"

step "1. A(남) 가입·프로필 → 사진인증 상태: 미인증·시도 0 / 내 정보 photoVerified false / 프로필(S8-08) photoVerified false"
mk A M 1995 SEOUL_GANGNAM; req GET $V/status "$TOK_A"; S_OK=$([ "$CODE" = 200 ] && [ "$(field verified)" = false ] && [ "$(field attempts)" = 0 ] && has '"lastResult":null' && echo true)
req GET /api/v1/users/me "$TOK_A"; M_OK=$([ "$(field photoVerified)" = false ] && echo true); req GET /api/v1/users/me/profile "$TOK_A"
check "status verified false·attempts 0·lastResult null, me.photoVerified false, profile.photoVerified false" "" "$([ "$S_OK" = true ] && [ "$M_OK" = true ] && [ "$CODE" = 200 ] && [ "$(field photoVerified)" = false ] && echo true)"

step "2. 프로필 사진 없이 대조 → 409 VERIFY_010 (S3 사진이 먼저)"
upload "$TOK_A" $V "$PNG"
check "409 VERIFY_010" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = VERIFY_010 ] && echo true)"

step "3. 프로필 사진 등록 → 다른 사진으로 대조(S16-04) → 422 VERIFY_011 (S16-04a~d 재촬영), 시도 1·lastResult FAIL"
upload "$TOK_A" $IMG "$PNG"; IMG_ID=$(field imageId); U_OK=$([ "$CODE" = 200 ] && [ -n "$IMG_ID" ] && echo true)
upload "$TOK_A" $V "$OTHER"; C1=$CODE; R1=$(field code); req GET $V/status "$TOK_A"
check "업로드 200, 대조 422 VERIFY_011, attempts 1·lastResult FAIL·verified false" "" "$([ "$U_OK" = true ] && [ "$C1" = 422 ] && [ "$R1" = VERIFY_011 ] && [ "$(field attempts)" = 1 ] && [ "$(field lastResult)" = FAIL ] && [ "$(field verified)" = false ] && echo true)"

step "4. 재시도 무제한: 다시 실패 → 422, attempts 2 / 이미지가 아닌 파일 → 400 USER_005 / 빈 파일 → 400"
upload "$TOK_A" $V "$OTHER"; C1=$CODE; upload "$TOK_A" $V "$NOTIMG"; C2=$CODE; R2=$(field code); : > /tmp/empty-$$.png; upload "$TOK_A" $V /tmp/empty-$$.png; C3=$CODE; req GET $V/status "$TOK_A"
check "422, 400 USER_005, 400, attempts 2(이미지 아닌 파일은 시도로 세지 않음)" "" "$([ "$C1" = 422 ] && [ "$C2" = 400 ] && [ "$R2" = USER_005 ] && [ "$C3" = 400 ] && [ "$(field attempts)" = 2 ] && echo true)"

step "5. 같은 사진(셀피=프로필)으로 대조 → 200 verified·similarity 100·threshold 80·provider stub·profileImageId (S16-05~08)"
upload "$TOK_A" $V "$PNG"
check "200 verified true, similarity 100, threshold 80, stub, profileImageId 일치, verifiedAt" "" "$([ "$CODE" = 200 ] && [ "$(field verified)" = true ] && [ "$(field similarity)" = 100.0 ] && [ "$(field threshold)" = 80.0 ] && [ "$(field provider)" = stub ] && [ "$(field profileImageId)" = "$IMG_ID" ] && [ -n "$(field verifiedAt)" ] && echo true)"

step "6. 완료 후: 상태 verified·attempts 3·PASS, 내 정보·프로필 photoVerified true(S8-08 배지), 재대조 → 409 VERIFY_012(S8-17 '인증완료')"
req GET $V/status "$TOK_A"; S_OK=$([ "$(field verified)" = true ] && [ "$(field attempts)" = 3 ] && [ "$(field lastResult)" = PASS ] && echo true)
req GET /api/v1/users/me "$TOK_A"; M_OK=$([ "$(field photoVerified)" = true ] && echo true); req GET /api/v1/users/me/profile "$TOK_A"; P_OK=$([ "$(field photoVerified)" = true ] && echo true); upload "$TOK_A" $V "$PNG"
check "status OK, me true, profile true, 재대조 409 VERIFY_012" "" "$([ "$S_OK" = true ] && [ "$M_OK" = true ] && [ "$P_OK" = true ] && [ "$CODE" = 409 ] && [ "$(field code)" = VERIFY_012 ] && echo true)"

step "7. 배지 노출(요구 3): B(여, 미인증)와 매칭 → B 의 현재 매칭 카드 partner.photoVerified true(S5) / A 의 카드 partner false / 매칭 상세(S10)도 동일"
mk B F 1997 SEOUL_JUNG; req POST /api/v1/matching/actions "$TOK_B" "{\"targetUserId\":$USER_A,\"actionType\":\"LIKE\"}"; req POST /api/v1/matching/actions "$TOK_A" "{\"targetUserId\":$USER_B,\"actionType\":\"LIKE\"}"; MATCH=$(field matchId)
req GET /api/v1/matches/current "$TOK_B"; B_SEES=$(pt photoVerified); req GET /api/v1/matches/current "$TOK_A"; A_SEES=$(pt photoVerified); req GET "/api/v1/matches/$MATCH" "$TOK_B"
check "B 카드 partner.photoVerified true, A 카드 false, 상세 partner true" "" "$([ -n "$MATCH" ] && [ "$B_SEES" = true ] && [ "$A_SEES" = false ] && [ "$CODE" = 200 ] && [ "$(pt photoVerified)" = true ] && echo true)"

step "8. S7-11: 매칭 성사 시 상대가 미인증인 A 에게만 MATCH_UNVERIFIED_PARTNER(→ S10 matchId), B 에게는 없음, 양쪽 최신 알림은 MATCH_CREATED"
req GET "/api/v1/notifications?page=0&size=5" "$TOK_A"; A_LATEST=$(field eventCode); A_UNV=$(echo "$J" | grep -c '"eventCode":"MATCH_UNVERIFIED_PARTNER"'); A_TGT=$(echo "$J" | grep -oE '"eventCode":"MATCH_UNVERIFIED_PARTNER"[^}]*"target":\{[^}]*\}' | grep -c "\"matchId\":$MATCH")
req GET "/api/v1/notifications?page=0&size=5" "$TOK_B"; B_LATEST=$(field eventCode); B_UNV=$(echo "$J" | grep -c '"eventCode":"MATCH_UNVERIFIED_PARTNER"')
check "A: UNVERIFIED 1건(target S10 matchId)·최신 MATCH_CREATED, B: UNVERIFIED 0건·최신 MATCH_CREATED" "" "$([ "$A_UNV" = 1 ] && [ "$A_TGT" = 1 ] && [ "$A_LATEST" = MATCH_CREATED ] && [ "$B_UNV" = 0 ] && [ "$B_LATEST" = MATCH_CREATED ] && echo true)"

step "9. 채팅방 목록(S6)·추천 카드에도 배지: B 의 방 목록 partner.photoVerified true"
req GET /api/v1/chat/rooms "$TOK_B"
check "200, partner.photoVerified true" "" "$([ "$CODE" = 200 ] && [ "$(pt photoVerified)" = true ] && echo true)"

step "10. 프로필 사진 교체 → 인증 해제(다른 사진으로 배지 유지 불가): status verified false·profileImageId null, 프로필 photoVerified false, B 카드 partner false"
upload "$TOK_A" $IMG "$OTHER"; C1=$CODE; req GET $V/status "$TOK_A"; S_OK=$([ "$(field verified)" = false ] && has '"profileImageId":null' && echo true); req GET /api/v1/users/me/profile "$TOK_A"; P_OK=$([ "$(field photoVerified)" = false ] && echo true); req GET /api/v1/matches/current "$TOK_B"
check "교체 200, verified false·imageId null, profile false, B 카드 partner false" "" "$([ "$C1" = 200 ] && [ "$S_OK" = true ] && [ "$P_OK" = true ] && [ "$(pt photoVerified)" = false ] && echo true)"

step "11. 새 사진으로 다시 인증 가능 → 200 / 사진 삭제 → 인증 해제 → 대조 409 VERIFY_010"
upload "$TOK_A" $V "$OTHER"; C1=$CODE; req DELETE $IMG "$TOK_A"; C2=$CODE; req GET $V/status "$TOK_A"; S_OK=$([ "$(field verified)" = false ] && echo true); upload "$TOK_A" $V "$OTHER"
check "재인증 200, 삭제 200, verified false, 409 VERIFY_010" "" "$([ "$C1" = 200 ] && [ "$C2" = 200 ] && [ "$S_OK" = true ] && [ "$CODE" = 409 ] && [ "$(field code)" = VERIFY_010 ] && echo true)"

step "12. 촬영 이미지 미저장(AC): 시도 이력 테이블에 이미지 컬럼 없음, 업로드 저장소에는 프로필 사진 파일만 / 인증 없이 → 401"
if [ -n "$DB_EXEC_CMD" ]; then
  COLS=$(eval "$DB_EXEC_CMD \"SELECT GROUP_CONCAT(COLUMN_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='bma' AND TABLE_NAME='US_PHOTO_VERIFICATION'\"" 2>/dev/null | tail -1)
  ROWS=$(eval "$DB_EXEC_CMD \"SELECT COUNT(*) FROM US_PHOTO_VERIFICATION WHERE USER_ID=$USER_A\"" 2>/dev/null | tail -1)
  NO_BLOB=$(echo "$COLS" | grep -qiE "IMAGE_DATA|SELFIE|BLOB|OBJECT_KEY|URL" && echo false || echo true)
  req GET $V/status ""
  check "컬럼에 이미지/키/URL 없음($NO_BLOB), A 시도 이력 5건(FAIL 2·PASS 1·… 결과·유사도만), 401" "" "$([ "$NO_BLOB" = true ] && [ "${ROWS:-0}" -ge 4 ] && [ "$CODE" = 401 ] && echo true)"
else req GET $V/status ""; check "인증 없이 401 (DB 확인은 DB_EXEC_CMD 없음)" "" "$([ "$CODE" = 401 ] && echo true)"; fi

rm -f /tmp/body.$$ /tmp/req.$$ "$OTHER" "$NOTIMG" /tmp/empty-$$.png
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
