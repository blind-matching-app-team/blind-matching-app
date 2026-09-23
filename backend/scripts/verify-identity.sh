#!/usr/bin/env bash
# BMA-79 Postman 컬렉션(docs/postman/BMA-79-identity.postman_collection.json)을 curl 로 재현한다.
# S15 본인인증: 상태 → 요청(거래 ID) → 확인(스텁 인증사, 성인) → 매칭 진입 가능(재인증 없음) → 미성년자 거부·계정 롤백 →
# 중복 CI → 요청 오류/만료 → 미인증 매칭 진입 차단.
#   BASE=http://localhost:8080 bash backend/scripts/verify-identity.sh
# 요청 만료는 DB 로 재현한다(DB_EXEC_CMD 없으면 건너뜀). 인증사는 스텁(app.verification.identity.provider=stub).
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
PW="Passw0rd!23"; N=$((TS % 10000))
PASS=0; FAIL=0; SKIP=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
mk() { local n=$1 g=$2 y=$3 r=$4; local e="bma79-$n-$TS@example.com"; eval "EMAIL_$n=$e"
  req POST /api/v1/auth/signup "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; eval "USER_$n=$(field userId)"
  req POST /api/v1/auth/login "" "{\"email\":\"$e\",\"password\":\"$PW\"}"; local t; t=$(field accessToken); eval "TOK_$n=$t"; eval "RT_$n=$(field refreshToken)"
  req PUT /api/v1/users/me/profile "$t" "{\"nickname\":\"인증$n$N\",\"birthDate\":\"$y-05-05\",\"genderCode\":\"$g\",\"regionCode\":\"$r\"}"; }
V=/api/v1/verification/identity; Q=/api/v1/matching/queue
# CI 는 실행마다 달라야 이전 실행의 계정과 중복(VERIFY_004)되지 않는다. 같은 실행 안에서 같은 이름+생년월일이면 같은 CI(같은 사람).
result() { printf '{"transactionId":"%s","providerPayload":{"result":{"name":"%s","birthDate":"%s","genderCode":"%s","phoneNumber":"%s","ci":"ci-%s-%s-%s"}}}' "$1" "$2" "$3" "$4" "$5" "$2" "$3" "$TS"; }
YEAR_MINOR=$(( $(date +%Y) - 18 ))   # 만 18세 → 미성년자
YEAR_TODAY19=$(( $(date +%Y) - 19 )); TODAY_MD=$(date +%m-%d)

step "1. A(남) 가입·프로필 → 상태: 미인증(verified false, lastAttemptStatus null), GET /users/me identityVerified false"
mk A M 1995 SEOUL_GANGNAM; req GET $V/status "$TOK_A"; S_OK=$([ "$CODE" = 200 ] && [ "$(field verified)" = false ] && [ "$(field matchingAllowed)" = false ] && has '"lastAttemptStatus":null' && echo true); req GET /api/v1/users/me "$TOK_A"
check "verified false·matchingAllowed false·lastAttemptStatus null, me.identityVerified false" "" "$([ "$S_OK" = true ] && [ "$CODE" = 200 ] && [ "$(field identityVerified)" = false ] && echo true)"

step "2. 미인증 상태로 매칭 진입(S4 → S9) → 403 VERIFY_001 (S15 관문)"
req POST $Q "$TOK_A"
check "403 VERIFY_001" "" "$([ "$CODE" = 403 ] && [ "$(field code)" = VERIFY_001 ] && echo true)"

step "3. 인증 요청(S15-04) → 거래 ID·provider stub·SDK 파라미터·만료(≈10분) / 상태 lastAttemptStatus REQUESTED"
req POST $V/request "$TOK_A"; TX_A=$(field transactionId); R_OK=$([ "$CODE" = 200 ] && [ -n "$TX_A" ] && [ "$(field provider)" = stub ] && has '"sdkToken"' && [ -n "$(field expiresAt)" ] && echo true); req GET $V/status "$TOK_A"
check "200 transactionId·stub·sdkToken·expiresAt, lastAttemptStatus REQUESTED" "" "$([ "$R_OK" = true ] && [ "$(field lastAttemptStatus)" = REQUESTED ] && echo true)"

step "4. 재요청 → 새 거래 ID, 이전 거래 ID 는 만료(409 VERIFY_006)"
req POST $V/request "$TOK_A"; TX_A2=$(field transactionId); req POST $V/confirm "$TOK_A" "$(result "$TX_A" 홍길동 1995-05-05 M 01011112222 '')"
check "새 ID ≠ 옛 ID, 옛 ID 확인 409 VERIFY_006" "" "$([ -n "$TX_A2" ] && [ "$TX_A2" != "$TX_A" ] && [ "$CODE" = 409 ] && [ "$(field code)" = VERIFY_006 ] && echo true)"

step "5. 확인(성인 1995-05-05) → VERIFIED·adult true·matchingAllowed true → 상태·me 반영"
req POST $V/confirm "$TOK_A" "$(result "$TX_A2" 홍길동 1995-05-05 M 01011112222 '')"; C_OK=$([ "$CODE" = 200 ] && [ "$(field status)" = VERIFIED ] && [ "$(field adult)" = true ] && [ "$(field matchingAllowed)" = true ] && [ "$(field provider)" = stub ] && echo true)
req GET $V/status "$TOK_A"; S_OK=$([ "$(field verified)" = true ] && [ -n "$(field verifiedAt)" ] && [ "$(field lastAttemptStatus)" = VERIFIED ] && echo true); req GET /api/v1/users/me "$TOK_A"
check "VERIFIED/adult/matchingAllowed, status verified·verifiedAt·VERIFIED, me.identityVerified true·identityVerifiedAt" "" "$([ "$C_OK" = true ] && [ "$S_OK" = true ] && [ "$(field identityVerified)" = true ] && [ -n "$(field identityVerifiedAt)" ] && echo true)"

step "6. AC: 인증 후 재인증 요구 없이 매칭 진입 → 200 WAITING (정리: 취소) / 재요청·재확인 → 409 VERIFY_002"
req POST $Q "$TOK_A"; C1=$CODE; S1=$(field status); req DELETE $Q "$TOK_A"; req POST $V/request "$TOK_A"; C2=$CODE; R2=$(field code); req POST $V/confirm "$TOK_A" "$(result "$TX_A2" 홍길동 1995-05-05 M 01011112222 '')"
check "진입 200 WAITING, 재요청 409 VERIFY_002, 재확인 409 VERIFY_002" "" "$([ "$C1" = 200 ] && [ "$S1" = WAITING ] && [ "$C2" = 409 ] && [ "$R2" = VERIFY_002 ] && [ "$CODE" = 409 ] && [ "$(field code)" = VERIFY_002 ] && echo true)"

step "7. 미성년자(BMA-19 안건3): B 확인에 인증사 생년월일 $YEAR_MINOR-05-05(만 18세) → 403 VERIFY_003, 계정 롤백(로그인 401, 남은 토큰 404, 리프레시 401)"
mk B F 1997 SEOUL_JUNG; req POST $V/request "$TOK_B"; TX_B=$(field transactionId)
req POST $V/confirm "$TOK_B" "$(result "$TX_B" 김영희 "$YEAR_MINOR-05-05" F 01033334444 '')"; C1=$CODE; R1=$(field code)
req POST /api/v1/auth/login "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; C2=$CODE; req GET /api/v1/users/me "$TOK_B"; C3=$CODE; R3=$(field code); req POST /api/v1/auth/refresh "" "{\"refreshToken\":\"$RT_B\"}"
check "403 VERIFY_003, 로그인 401, 남은 액세스 토큰으로 me 404 USER_001(계정 없음), refresh 401 (프로필 자기 입력 1997 은 무시, 인증사 생년월일이 기준)" "" "$([ "$C1" = 403 ] && [ "$R1" = VERIFY_003 ] && [ "$C2" = 401 ] && [ "$C3" = 404 ] && [ "$R3" = USER_001 ] && [ "$CODE" = 401 ] && echo true)"

step "8. 경계: 오늘이 19번째 생일($YEAR_TODAY19-$TODAY_MD) → 성인 / 같은 이메일 재가입 가능(탈퇴 처리)"
mk C M 1990 SEOUL_GANGNAM; req POST $V/request "$TOK_C"; TX_C=$(field transactionId); req POST $V/confirm "$TOK_C" "$(result "$TX_C" 이철수 "$YEAR_TODAY19-$TODAY_MD" M 01055556666 '')"; C1=$CODE
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"
check "생일 당일 VERIFIED 200, B 이메일 재가입 200" "" "$([ "$C1" = 200 ] && [ "$CODE" = 200 ] && echo true)"

step "9. 중복 CI: D 가 A 와 같은 인증 결과(같은 사람) → 409 VERIFY_004, D 는 여전히 미인증"
mk D M 1993 SEOUL_GANGNAM; req POST $V/request "$TOK_D"; TX_D=$(field transactionId); req POST $V/confirm "$TOK_D" "$(result "$TX_D" 홍길동 1995-05-05 M 01011112222 '')"; C1=$CODE; R1=$(field code); req GET $V/status "$TOK_D"
check "409 VERIFY_004, verified false·lastAttemptStatus REJECTED_DUPLICATE" "" "$([ "$C1" = 409 ] && [ "$R1" = VERIFY_004 ] && [ "$(field verified)" = false ] && [ "$(field lastAttemptStatus)" = REJECTED_DUPLICATE ] && echo true)"

step "10. 거래 ID 오류: 없는 ID → 404 VERIFY_005 / 남의 ID(D 가 C 의 것) → 404 / transactionId 누락 → 400 / 스텁 결과 누락 → 400 VERIFY_007"
req POST $V/confirm "$TOK_D" "$(result nope 홍길동 1995-05-05 M 010 '')"; C1=$CODE; R1=$(field code)
req POST $V/request "$TOK_D"; TX_D2=$(field transactionId); req POST $V/confirm "$TOK_D" "$(result "$TX_C" 홍길동 1995-05-05 M 010 '')"; C2=$CODE
req POST $V/confirm "$TOK_D" '{"providerPayload":{}}'; C3=$CODE; req POST $V/confirm "$TOK_D" "{\"transactionId\":\"$TX_D2\",\"providerPayload\":{}}"; C4=$CODE; R4=$(field code); req GET $V/status "$TOK_D"
check "404 VERIFY_005, 404, 400, 400 VERIFY_007, lastAttemptStatus FAILED" "" "$([ "$C1" = 404 ] && [ "$R1" = VERIFY_005 ] && [ "$C2" = 404 ] && [ "$C3" = 400 ] && [ "$C4" = 400 ] && [ "$R4" = VERIFY_007 ] && [ "$(field lastAttemptStatus)" = FAILED ] && echo true)"

step "11. 요청 만료(10분, DB 로 재현) → 409 VERIFY_006 → 새로 요청하면 정상 인증"
req POST $V/request "$TOK_D"; TX_D3=$(field transactionId)
if [ -n "$DB_EXEC_CMD" ]; then
  eval "$DB_EXEC_CMD \"UPDATE US_IDENTITY_VERIFICATION SET EXPIRE_DATE = DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE TRANSACTION_ID='$TX_D3'\"" >/dev/null 2>&1
  req POST $V/confirm "$TOK_D" "$(result "$TX_D3" 박민수 1992-03-03 M 01077778888 '')"; C1=$CODE; R1=$(field code)
  req POST $V/request "$TOK_D"; TX_D4=$(field transactionId); req POST $V/confirm "$TOK_D" "$(result "$TX_D4" 박민수 1992-03-03 M 01077778888 '')"
  check "409 VERIFY_006, 재요청 후 VERIFIED" "" "$([ "$C1" = 409 ] && [ "$R1" = VERIFY_006 ] && [ "$CODE" = 200 ] && [ "$(field status)" = VERIFIED ] && echo true)"
else skip "요청 만료" "DB_EXEC_CMD 없음"; fi

step "12. 인증 없이 → 401 / 인증된 A 는 언제든 상태 조회로 verified true (S15 재노출 없음)"
req GET $V/status ""; C1=$CODE; req POST $V/request ""; C2=$CODE; req GET $V/status "$TOK_A"
check "401 ×2, A verified true" "" "$([ "$C1" = 401 ] && [ "$C2" = 401 ] && [ "$CODE" = 200 ] && [ "$(field verified)" = true ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
