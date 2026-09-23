#!/usr/bin/env bash
# BMA-61 Postman 컬렉션(docs/postman/BMA-61-password-reset.postman_collection.json)을 curl 로 재현한다.
# S13 비밀번호 찾기: 링크 요청(존재 여부 비노출) → 토큰 확인 → 새 비밀번호 설정 → 세션 폐기, 재사용·만료·이전 링크 무효.
# 서버는 PASSWORD_RESET_EXPOSE_DEBUG_LINK=true 로 떠 있어야 한다(메일 없이 응답에서 링크를 읽는다).
#   BASE=http://localhost:8080 bash backend/scripts/verify-password-reset.sh
# 만료 시나리오는 DB_EXEC_CMD 가 있을 때만 실행한다. 예)
#   DB_EXEC_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e"
BASE=${BASE:-http://localhost:8080}
TS=$(date +%s)
EMAIL_A="bma61-a-$TS@example.com"; EMAIL_B="bma61-b-$TS@example.com"
PW="Passw0rd!23"; PW2="Reset0Pass!45"; PW3="Third0Pass!67"
PASS=0; FAIL=0; SKIP=0; J=""; CODE=""
req() { local m=$1 p=$2 t=$3 b=$4; local args=(-s -o /tmp/body.$$ -w '%{http_code}' -X "$m" "$BASE$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && { printf %s "$b" > /tmp/req.$$; args+=(--data-binary "@/tmp/req.$$"); }
  CODE=$(curl "${args[@]}"); J=$(cat /tmp/body.$$); }
field() { echo "$J" | grep -oE "\"$1\":(\"[^\"]*\"|[^,}]*)" | head -1 | sed -E "s/^\"$1\"://; s/^\"//; s/\"$//"; }
has() { echo "$J" | grep -q -- "$1"; }
token_of() { echo "$J" | grep -oE '"debugResetLink":"[^"]*"' | sed -E 's/.*token=//; s/"$//'; }
check() { if [ "$3" = true ]; then PASS=$((PASS+1)); echo "  ✔ $1"; else FAIL=$((FAIL+1)); echo "  ✘ $1  [http=$CODE] $(echo "$J" | head -c 400)"; fi; }
skip() { SKIP=$((SKIP+1)); echo "  ⊘ $1 (건너뜀: $2)"; }
step() { echo "[$1]"; }
login() { req POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"; }
PR=/api/v1/auth/password-reset

step "1. A 가입/로그인 (리프레시 토큰 보관)"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PW\"}"; USER_A=$(field userId); login "$EMAIL_A" "$PW"; TOK_A=$(field accessToken); RT_A=$(field refreshToken)
check "가입·로그인 200" "" "$([ "$CODE" = 200 ] && [ -n "$RT_A" ] && echo true)"

step "2. 미가입 이메일 요청 (S13-04) → 200 sent=true, 링크 없음 (존재 여부 비노출)"
req POST $PR/request "" "{\"email\":\"nobody-$TS@example.com\"}"
check "200, sent=true, expiresInMinutes=30, debugResetLink=null" "" "$([ "$CODE" = 200 ] && [ "$(field sent)" = true ] && [ "$(field expiresInMinutes)" = 30 ] && has '"debugResetLink":null' && echo true)"

step "3. 이메일 형식 오류 / 빈 본문 → 400 COMMON_001"
req POST $PR/request "" '{"email":"not-an-email"}'; C1=$CODE; req POST $PR/request "" '{}'
check "400 ×2" "" "$([ "$C1" = 400 ] && [ "$CODE" = 400 ] && [ "$(field code)" = COMMON_001 ] && echo true)"

step "4. A 요청 (S13-05) → 200, 링크(토큰) 발급 [검증용 노출 설정]"
req POST $PR/request "" "{\"email\":\"$EMAIL_A\"}"; TOKEN1=$(token_of)
check "200 sent=true, debugResetLink=…/reset-password?token=…" "" "$([ "$CODE" = 200 ] && [ "$(field sent)" = true ] && has '/reset-password?token=' && [ -n "$TOKEN1" ] && echo true)"

step "5. 토큰 확인 (S13-06 진입) → valid=true, expiresAt"
req GET "$PR/validate?token=$TOKEN1" ""
check "200 valid=true expiresAt 존재" "" "$([ "$CODE" = 200 ] && [ "$(field valid)" = true ] && [ -n "$(field expiresAt)" ] && echo true)"

step "6. 위조 토큰: 확인 → 400 AUTH_017 / 확정 → 400 AUTH_017"
req GET "$PR/validate?token=forged-$TS" ""; C1=$CODE; R1=$(field code); req POST $PR/confirm "" "{\"token\":\"forged-$TS\",\"newPassword\":\"$PW2\"}"
check "400 AUTH_017 ×2" "" "$([ "$C1" = 400 ] && [ "$R1" = AUTH_017 ] && [ "$CODE" = 400 ] && [ "$(field code)" = AUTH_017 ] && echo true)"

step "7. 규칙 위반 새 비밀번호(숫자만) → 400 COMMON_001, 토큰은 그대로 유효"
req POST $PR/confirm "" "{\"token\":\"$TOKEN1\",\"newPassword\":\"12345678\"}"; C1=$CODE; R1=$(field code); req GET "$PR/validate?token=$TOKEN1" ""
check "400 COMMON_001, validate 200" "" "$([ "$C1" = 400 ] && [ "$R1" = COMMON_001 ] && [ "$CODE" = 200 ] && [ "$(field valid)" = true ] && echo true)"

step "8. A 재요청 → 새 토큰 발급, 이전 링크는 무효"
req POST $PR/request "" "{\"email\":\"$EMAIL_A\"}"; TOKEN2=$(token_of); req GET "$PR/validate?token=$TOKEN1" ""; C1=$CODE; req GET "$PR/validate?token=$TOKEN2" ""
check "TOKEN1 400, TOKEN2 200 valid" "" "$([ -n "$TOKEN2" ] && [ "$TOKEN2" != "$TOKEN1" ] && [ "$C1" = 400 ] && [ "$CODE" = 200 ] && [ "$(field valid)" = true ] && echo true)"

step "9. 새 비밀번호 설정 (S13-09) → 200, 리프레시 토큰 폐기 수 1"
req POST $PR/confirm "" "{\"token\":\"$TOKEN2\",\"newPassword\":\"$PW2\"}"
check "200 sessionsEnded=1, changedAt" "" "$([ "$CODE" = 200 ] && [ "$(field sessionsEnded)" = 1 ] && [ -n "$(field changedAt)" ] && echo true)"

step "10. 옛 리프레시 토큰 재발급 → 401 / 옛 비밀번호 로그인 → 401 / 새 비밀번호 로그인 → 200 (S1 이동)"
req POST /api/v1/auth/refresh "" "{\"refreshToken\":\"$RT_A\"}"; C1=$CODE; login "$EMAIL_A" "$PW"; C2=$CODE; login "$EMAIL_A" "$PW2"; TOK_A=$(field accessToken)
check "401, 401, 200" "" "$([ "$C1" = 401 ] && [ "$C2" = 401 ] && [ "$CODE" = 200 ] && [ -n "$TOK_A" ] && echo true)"

step "11. 사용한 토큰 재사용 → 400 AUTH_017 (확인·확정 모두)"
req GET "$PR/validate?token=$TOKEN2" ""; C1=$CODE; req POST $PR/confirm "" "{\"token\":\"$TOKEN2\",\"newPassword\":\"$PW3\"}"
check "400 ×2 AUTH_017" "" "$([ "$C1" = 400 ] && [ "$CODE" = 400 ] && [ "$(field code)" = AUTH_017 ] && echo true)"

step "12. 로그인 유지 확인: 새 비밀번호 세션으로 내 계정 → passwordSet=true"
req GET /api/v1/users/me "$TOK_A"
check "200 passwordSet=true" "" "$([ "$CODE" = 200 ] && [ "$(field passwordSet)" = true ] && echo true)"

step "13. 탈퇴 계정 이메일 요청 → 200, 링크 없음 (조용히 무시)"
req POST /api/v1/auth/signup "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PW\"}"; login "$EMAIL_B" "$PW"; TOK_B=$(field accessToken); req DELETE /api/v1/users/me "$TOK_B"; req POST $PR/request "" "{\"email\":\"$EMAIL_B\"}"
check "200 sent=true debugResetLink=null" "" "$([ "$CODE" = 200 ] && [ "$(field sent)" = true ] && has '"debugResetLink":null' && echo true)"

step "14. 이메일 대소문자 무시: 대문자로 요청해도 발급 (앞뒤 공백은 형식 검증에서 400)"
req POST $PR/request "" "{\"email\":\"$(echo "$EMAIL_A" | tr a-z A-Z)\"}"; TOKEN3=$(token_of)
check "200, 링크 발급" "" "$([ "$CODE" = 200 ] && [ -n "$TOKEN3" ] && echo true)"

if [ -n "$DB_EXEC_CMD" ]; then
  step "15. 만료 시나리오: 만료 시각을 과거로 돌린 뒤 확인·확정 → 400 AUTH_017"
  eval "$DB_EXEC_CMD \"UPDATE US_USER_TOKEN SET EXPIRE_DATE = NOW() - INTERVAL 1 MINUTE WHERE USER_ID=$USER_A AND TOKEN_TYPE='PASSWORD_RESET' AND DELETED='N'\"" >/dev/null 2>&1
  req GET "$PR/validate?token=$TOKEN3" ""; C1=$CODE; req POST $PR/confirm "" "{\"token\":\"$TOKEN3\",\"newPassword\":\"$PW3\"}"
  check "400 ×2 AUTH_017" "" "$([ "$C1" = 400 ] && [ "$CODE" = 400 ] && [ "$(field code)" = AUTH_017 ] && echo true)"
else
  skip "15. 만료 시나리오" "DB_EXEC_CMD 미설정"
fi

step "16. 만료/소진 후 다시 요청하면 새 링크로 재설정 가능 → 로그인 성공"
req POST $PR/request "" "{\"email\":\"$EMAIL_A\"}"; TOKEN4=$(token_of); req POST $PR/confirm "" "{\"token\":\"$TOKEN4\",\"newPassword\":\"$PW3\"}"; C1=$CODE; login "$EMAIL_A" "$PW3"
check "확정 200, 새 비밀번호 로그인 200" "" "$([ "$C1" = 200 ] && [ "$CODE" = 200 ] && [ -n "$(field accessToken)" ] && echo true)"

rm -f /tmp/body.$$ /tmp/req.$$
echo; echo "RESULT: pass=$PASS fail=$FAIL skip=$SKIP"; [ $FAIL = 0 ]
