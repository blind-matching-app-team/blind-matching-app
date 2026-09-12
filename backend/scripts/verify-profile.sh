#!/usr/bin/env bash
# BMA-41 Postman 컬렉션(docs/postman/BMA-41-profile.postman_collection.json) 22단계를
# curl 로 재현한다. newman/node 가 없는 환경용. 앱이 떠 있는 상태에서 실행한다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-profile.sh
# Windows Git Bash 에서는 LANG=C.UTF-8 을 앞에 붙여야 한글 본문이 깨지지 않는다.
BASE=${BASE:-http://localhost:8080}
PNG=${PNG:-"$(cd "$(dirname "$0")/.." && pwd)/docs/postman/sample-profile.png"}
TS=$(date +%s)
EMAIL_A="bma41-a-$TS@example.com"; EMAIL_B="bma41-b-$TS@example.com"
PW="Passw0rd!23"; NICK="닉$TS"; NICK=${NICK:0:10}
PASS=0; FAIL=0
J=""; CODE=""
req() { # method path [token] [body]
  local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$)
}
upload() { local t=$1; CODE=$(curl -s -o /tmp/body.$$ -w '%{http_code}' -X POST "$BASE/api/v1/users/me/image" -H "Authorization: Bearer $t" -F "file=@$PNG;type=image/png"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
check() { # name condition-description bool
  if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $J"; fi; }
step() { echo "[$1]"; }

step "1. 사용자 A 회원가입"; req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"
check "200 + userId" "" "$([ "$CODE" = 200 ] && [ -n "$(field userId)" ] && echo true)"
step "2. 로그인 A (profileCompleted=false)"; req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"
TOK_A=$(field accessToken); check "200, profileCompleted=false, onboardingCompleted 존재" "" "$([ "$CODE" = 200 ] && [ "$(field profileCompleted)" = false ] && [ -n "$(field onboardingCompleted)" ] && echo true)"
step "3. 지역 목록"; req GET /api/v1/regions "$TOK_A"
check "200, 시/도 17개, 강남구 포함" "" "$([ "$CODE" = 200 ] && [ "$(echo "$J" | grep -o "\"children\"" | wc -l)" = 17 ] && echo "$J" | grep -q SEOUL_GANGNAM && echo true)"
step "4. 닉네임 확인 - 사용 가능"; req GET "/api/v1/users/me/profile/nickname-check?nickname=$(printf %s "$NICK" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g')" "$TOK_A"
check "available=true" "" "$([ "$CODE" = 200 ] && [ "$(field available)" = true ] && echo true)"
step "5. 프로필 저장"; req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK\",\"birthDate\":\"1998-03-14\",\"regionCode\":\"SEOUL_GANGNAM\",\"mbtiCode\":\"INFP\",\"heightCm\":175}"
check "200, COMPLETE, score=70, 지역명 강남구/서울특별시, 성별 null 키 존재" "" "$([ "$CODE" = 200 ] && [ "$(field profileStatus)" = COMPLETE ] && [ "$(field profileScore)" = 70 ] && [ "$(field regionName)" = 강남구 ] && [ "$(field regionSidoName)" = 서울특별시 ] && echo "$J" | grep -q '"genderCode":null' && echo true)"
step "6. 프로필 조회"; req GET /api/v1/users/me/profile "$TOK_A"
check "200, age 계산, mbti INFP" "" "$([ "$CODE" = 200 ] && [ -n "$(field age)" ] && [ "$(field mbtiCode)" = INFP ] && echo true)"
step "7. 닉네임 확인 - 본인 닉네임"; req GET "/api/v1/users/me/profile/nickname-check?nickname=$(printf %s "$NICK" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g')" "$TOK_A"
check "available=true" "" "$([ "$CODE" = 200 ] && [ "$(field available)" = true ] && echo true)"
step "8. 시/도 코드 거부"; req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK\",\"birthDate\":\"1998-03-14\",\"regionCode\":\"SEOUL\"}"
check "400 (COMMON_002 아님)" "" "$([ "$CODE" = 400 ] && [ "$(field code)" != COMMON_002 ] && echo true)"
step "9. 없는 지역 코드"; req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK\",\"birthDate\":\"1998-03-14\",\"regionCode\":\"NOWHERE\"}"
check "400 (COMMON_002 아님)" "" "$([ "$CODE" = 400 ] && [ "$(field code)" != COMMON_002 ] && echo true)"
step "10. 닉네임 10자 초과"; req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"열한글자를넘기는닉네임\",\"birthDate\":\"1998-03-14\",\"regionCode\":\"SEOUL_GANGNAM\"}"
check "400 (COMMON_002 아님)" "" "$([ "$CODE" = 400 ] && [ "$(field code)" != COMMON_002 ] && echo true)"
step "11. 사진 조회 - 미등록"; req GET /api/v1/users/me/image "$TOK_A"
check "200, data 없음" "" "$([ "$CODE" = 200 ] && ! echo "$J" | grep -q '"data"' && echo true)"
step "12. 사진 업로드"; upload "$TOK_A"; IMG1=$(field imageId)
check "200, imageId, objectKey profile/, contentType image/png" "" "$([ "$CODE" = 200 ] && [ -n "$IMG1" ] && echo "$J" | grep -q '"objectKey":"profile/' && [ "$(field contentType)" = image/png ] && echo true)"
step "13. 사진 조회 - 등록됨"; req GET /api/v1/users/me/image "$TOK_A"
check "imageId 동일" "" "$([ "$CODE" = 200 ] && [ "$(field imageId)" = "$IMG1" ] && echo true)"
step "14. 재업로드는 교체"; upload "$TOK_A"; IMG2=$(field imageId); req GET /api/v1/users/me/image "$TOK_A"
check "새 imageId, 조회 시 새 것만" "" "$([ -n "$IMG2" ] && [ "$IMG2" != "$IMG1" ] && [ "$(field imageId)" = "$IMG2" ] && echo true)"
step "15. 사진 삭제"; req DELETE /api/v1/users/me/image "$TOK_A"
check "200" "" "$([ "$CODE" = 200 ] && echo true)"
step "16. 삭제 - 이미 없음"; req DELETE /api/v1/users/me/image "$TOK_A"
check "404" "" "$([ "$CODE" = 404 ] && echo true)"
step "17. 재로그인 - profileCompleted=true"; req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"
check "profileCompleted=true" "" "$([ "$CODE" = 200 ] && [ "$(field profileCompleted)" = true ] && echo true)"
step "18. 사용자 B 회원가입"; req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"
check "200" "" "$([ "$CODE" = 200 ] && echo true)"
step "19. 로그인 B"; req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; TOK_B=$(field accessToken)
check "200" "" "$([ "$CODE" = 200 ] && [ -n "$TOK_B" ] && echo true)"
step "20. 닉네임 확인 - 남이 쓰는 닉네임"; req GET "/api/v1/users/me/profile/nickname-check?nickname=$(printf %s "$NICK" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g')" "$TOK_B"
check "available=false" "" "$([ "$CODE" = 200 ] && [ "$(field available)" = false ] && echo true)"
step "21. 닉네임 중복 저장"; req PUT /api/v1/users/me/profile "$TOK_B" "{\"nickname\":\"$NICK\",\"birthDate\":\"1997-01-02\",\"regionCode\":\"BUSAN_HAEUNDAE\"}"
check "409 USER_003" "" "$([ "$CODE" = 409 ] && [ "$(field code)" = USER_003 ] && echo true)"
step "22. 인증 없이 저장"; req PUT /api/v1/users/me/profile "" "{\"nickname\":\"무단\",\"birthDate\":\"1998-03-14\",\"regionCode\":\"SEOUL_GANGNAM\"}"
check "401" "" "$([ "$CODE" = 401 ] && echo true)"
rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
