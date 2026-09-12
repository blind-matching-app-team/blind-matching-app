#!/usr/bin/env bash
# BMA-44 Postman 컬렉션(docs/postman/BMA-44-preference.postman_collection.json) 22단계를
# curl 로 재현한다. newman/node 가 없는 환경용. 앱이 떠 있는 상태에서 실행한다.
#   BASE=http://localhost:8080 bash backend/scripts/verify-preference.sh
# Windows Git Bash 에서는 LANG=C.UTF-8 을 앞에 붙여야 한글 본문이 깨지지 않는다.
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma44-a-$TS@example.com"; EMAIL_B="bma44-b-$TS@example.com"
PW="Passw0rd!23"; NICK_A="선호A$((TS % 100000))"; NICK_B="선호B$((TS % 100000))"
PASS=0; FAIL=0; J=""; CODE=""
req() { # method path [token] [body]
  local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$)
}
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
count_items() { echo "$J" | grep -o '"profile"' | wc -l | tr -d ' '; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 300)"; fi; }
step() { echo "[$1]"; }
pref() { req PUT /api/v1/users/me/preference "$1" "$2"; }
recs() { req GET "/api/v1/matching/recommendations?size=50" "$1"; }

step "1. 사용자 A 회원가입"; req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"
check "200" "" "$([ "$CODE" = 200 ] && echo true)"
step "2. 로그인 A"; req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; TOK_A=$(field accessToken)
check "200 + accessToken" "" "$([ "$CODE" = 200 ] && [ -n "$TOK_A" ] && echo true)"
step "3. 지역 목록 (시/도 17, 제주 하위에 서귀포시)"; req GET /api/v1/regions "$TOK_A"
check "200" "" "$([ "$CODE" = 200 ] && [ "$(echo "$J" | grep -o '"children"' | wc -l | tr -d ' ')" = 17 ] && echo "$J" | grep -q JEJU_SEOGWIPO && echo true)"
step "4. 선호조건 조회 - 미설정 기본값"; req GET /api/v1/users/me/preference "$TOK_A"
check "200, 지역/나이 null 키 존재, matchingEnabled=true, 키 필드 없음" "" "$([ "$CODE" = 200 ] && echo "$J" | grep -q '"preferredRegionCode":null' && echo "$J" | grep -q '"minAge":null' && [ "$(field matchingEnabled)" = true ] && ! echo "$J" | grep -qi height && echo true)"
step "5. 저장 - 시/군/구 + 나이 25~35"; pref "$TOK_A" '{"preferredRegionCode":"SEOUL_GANGNAM","minAge":25,"maxAge":35}'
check "200, SIGUNGU, 강남구, SEOUL/서울특별시, 25~35" "" "$([ "$CODE" = 200 ] && [ "$(field preferredRegionScope)" = SIGUNGU ] && [ "$(field preferredRegionName)" = 강남구 ] && [ "$(field regionSidoCode)" = SEOUL ] && [ "$(field regionSidoName)" = 서울특별시 ] && [ "$(field minAge)" = 25 ] && [ "$(field maxAge)" = 35 ] && echo true)"
step "6. 조회 - 저장값 반영"; req GET /api/v1/users/me/preference "$TOK_A"
check "SEOUL_GANGNAM, 25~35" "" "$([ "$CODE" = 200 ] && [ "$(field preferredRegionCode)" = SEOUL_GANGNAM ] && [ "$(field maxAge)" = 35 ] && echo true)"
step "7. 저장 - 시/도 전체(서울), 나이 제한 없음"; pref "$TOK_A" '{"preferredRegionCode":"SEOUL"}'
check "200, SIDO, 서울특별시, sido=SEOUL, 나이 null" "" "$([ "$CODE" = 200 ] && [ "$(field preferredRegionScope)" = SIDO ] && [ "$(field preferredRegionName)" = 서울특별시 ] && [ "$(field regionSidoCode)" = SEOUL ] && echo "$J" | grep -q '"minAge":null' && echo true)"
step "8. 저장 - 없는 지역 코드"; pref "$TOK_A" '{"preferredRegionCode":"NOWHERE","minAge":20,"maxAge":30}'
check "400 COMMON_001" "" "$([ "$CODE" = 400 ] && [ "$(field code)" = COMMON_001 ] && echo true)"
step "9. 저장 - 지역 누락"; pref "$TOK_A" '{"minAge":20,"maxAge":30}'
check "400 COMMON_001" "" "$([ "$CODE" = 400 ] && [ "$(field code)" = COMMON_001 ] && echo true)"
step "10. 저장 - 최소 나이 18 (미성년자 범위 차단)"; pref "$TOK_A" '{"preferredRegionCode":"SEOUL","minAge":18,"maxAge":30}'
check "400 (최소 나이 메시지)" "" "$([ "$CODE" = 400 ] && echo "$J" | grep -q "최소 나이" && echo true)"
step "11. 저장 - 최대 나이 100"; pref "$TOK_A" '{"preferredRegionCode":"SEOUL","minAge":20,"maxAge":100}'
check "400 (최대 나이 메시지)" "" "$([ "$CODE" = 400 ] && echo "$J" | grep -q "최대 나이" && echo true)"
step "12. 저장 - 최소 > 최대"; pref "$TOK_A" '{"preferredRegionCode":"SEOUL","minAge":40,"maxAge":30}'
check "400 (순서 메시지)" "" "$([ "$CODE" = 400 ] && echo "$J" | grep -q "클 수 없습니다" && echo true)"
step "13. 저장 - 키 필드는 무시된다"; pref "$TOK_A" '{"preferredRegionCode":"SEOUL","minHeightCm":170,"maxHeightCm":190}'
check "200, 응답에 height 없음" "" "$([ "$CODE" = 200 ] && ! echo "$J" | grep -qi height && echo true)"
step "14. 인증 없이 저장"; pref "" '{"preferredRegionCode":"SEOUL"}'
check "401" "" "$([ "$CODE" = 401 ] && echo true)"
step "15. 프로필 A 저장 (서울 강남, 성별 M) - 추천 조회 자격"; req PUT /api/v1/users/me/profile "$TOK_A" "{\"nickname\":\"$NICK_A\",\"birthDate\":\"1995-05-05\",\"genderCode\":\"M\",\"regionCode\":\"SEOUL_GANGNAM\"}"
check "200 COMPLETE" "" "$([ "$CODE" = 200 ] && [ "$(field profileStatus)" = COMPLETE ] && echo true)"
step "16. 사용자 B 가입/로그인/프로필 (제주 서귀포시, 1997년생 F)"; req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; TOK_B=$(field accessToken); req PUT /api/v1/users/me/profile "$TOK_B" "{\"nickname\":\"$NICK_B\",\"birthDate\":\"1997-07-07\",\"genderCode\":\"F\",\"regionCode\":\"JEJU_SEOGWIPO\"}"
check "B 프로필 COMPLETE" "" "$([ "$CODE" = 200 ] && [ "$(field profileStatus)" = COMPLETE ] && echo true)"
step "17. A 희망지역=제주 전체 → 추천에 B 포함"; pref "$TOK_A" '{"preferredRegionCode":"JEJU"}'; recs "$TOK_A"
check "200, 1건 이상" "" "$([ "$CODE" = 200 ] && [ "$(count_items)" -ge 1 ] && echo true)"
step "18. A 희망지역=제주시 → B(서귀포) 제외"; pref "$TOK_A" '{"preferredRegionCode":"JEJU_JEJU"}'; recs "$TOK_A"
check "200, 0건" "" "$([ "$CODE" = 200 ] && [ "$(count_items)" = 0 ] && echo true)"
step "19. A 희망지역=서귀포시 → B 포함"; pref "$TOK_A" '{"preferredRegionCode":"JEJU_SEOGWIPO"}'; recs "$TOK_A"
check "200, 1건 이상" "" "$([ "$CODE" = 200 ] && [ "$(count_items)" -ge 1 ] && echo true)"
step "20. A 나이 19~25 → B(만 29) 제외"; pref "$TOK_A" '{"preferredRegionCode":"JEJU","minAge":19,"maxAge":25}'; recs "$TOK_A"
check "200, 0건" "" "$([ "$CODE" = 200 ] && [ "$(count_items)" = 0 ] && echo true)"
step "21. A 나이 26~35 → B 포함"; pref "$TOK_A" '{"preferredRegionCode":"JEJU","minAge":26,"maxAge":35}'; recs "$TOK_A"
check "200, 1건 이상" "" "$([ "$CODE" = 200 ] && [ "$(count_items)" -ge 1 ] && echo true)"
step "22. 매칭 참여 끄기 → 조회에 반영, 지역 유지"; pref "$TOK_A" '{"preferredRegionCode":"JEJU","matchingEnabled":false}'; req GET /api/v1/users/me/preference "$TOK_A"
check "matchingEnabled=false, 지역 JEJU" "" "$([ "$CODE" = 200 ] && [ "$(field matchingEnabled)" = false ] && [ "$(field preferredRegionCode)" = JEJU ] && echo true)"
rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL"; [ $FAIL = 0 ]
