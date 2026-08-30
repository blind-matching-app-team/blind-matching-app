#!/usr/bin/env node
'use strict';

/**
 * 인증 API 스모크 테스트 (BMA-35).
 *
 * 실행 중인 서버에 대고 회원가입 -> 로그인 -> 재발급 -> 로그아웃을 순서대로 치고
 * 각 단계의 응답을 검증한다. 실패 케이스(이메일 중복, 자격 증명 오류, 토큰 재사용)도 함께 본다.
 *
 * 실행:
 *   docker compose up -d --build
 *   node backend/scripts/smoke-auth.js
 *
 * 옵션(환경변수):
 *   BASE_URL   기본 http://localhost:8080
 *
 * 의존성이 없다. Node 14 이상에서 그대로 동작한다.
 * 소셜 로그인은 브라우저 이동이 필요해 이 스크립트로 검증하지 않는다.
 */

const http = require('http');
const https = require('https');
const crypto = require('crypto');
const { URL } = require('url');

const BASE_URL = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const API = BASE_URL + '/api/v1/auth';

const color = process.stdout.isTTY
    ? { red: '\x1b[31m', green: '\x1b[32m', yellow: '\x1b[33m', dim: '\x1b[2m', bold: '\x1b[1m', off: '\x1b[0m' }
    : { red: '', green: '', yellow: '', dim: '', bold: '', off: '' };

let passed = 0;
let failed = 0;

/**
 * 검증 결과를 기록하고 출력한다.
 *
 * @param {boolean} condition 통과 여부
 * @param {string} label 설명
 * @param {string} [detail] 실패 시 덧붙일 실제 값
 */
function check(condition, label, detail) {
    if (condition) {
        passed++;
        console.log(`    ${color.green}통과${color.off}  ${label}`);
    } else {
        failed++;
        console.log(`    ${color.red}실패${color.off}  ${label}${detail ? `  ${color.dim}(${detail})${color.off}` : ''}`);
    }
}

/**
 * JSON 요청을 보낸다.
 *
 * @param {string} method HTTP 메서드
 * @param {string} path API 경로
 * @param {object} [body] 요청 본문
 * @param {string} [accessToken] Bearer 토큰
 * @returns {Promise<{status: number, body: object}>} 응답
 */
function request(method, path, body, accessToken) {
    const url = new URL(API + path);
    const payload = body ? Buffer.from(JSON.stringify(body), 'utf8') : null;
    const headers = { Accept: 'application/json' };
    if (payload) {
        headers['Content-Type'] = 'application/json';
        headers['Content-Length'] = payload.length;
    }
    if (accessToken) {
        headers.Authorization = 'Bearer ' + accessToken;
    }

    const client = url.protocol === 'https:' ? https : http;
    return new Promise((resolve, reject) => {
        const req = client.request(
            {
                hostname: url.hostname,
                port: url.port || (url.protocol === 'https:' ? 443 : 80),
                path: url.pathname,
                method,
                headers,
            },
            (res) => {
                const chunks = [];
                res.on('data', (c) => chunks.push(c));
                res.on('end', () => {
                    const raw = Buffer.concat(chunks).toString('utf8');
                    let parsed;
                    try {
                        parsed = raw ? JSON.parse(raw) : {};
                    } catch (e) {
                        parsed = { raw };
                    }
                    resolve({ status: res.statusCode, body: parsed });
                });
            },
        );
        req.on('error', reject);
        req.setTimeout(15000, () => req.destroy(new Error('요청이 15초 안에 끝나지 않았습니다.')));
        if (payload) {
            req.write(payload);
        }
        req.end();
    });
}

/**
 * 서버가 떠 있는지 확인한다.
 */
async function waitForServer() {
    const url = new URL(BASE_URL + '/actuator/health');
    const client = url.protocol === 'https:' ? https : http;
    return new Promise((resolve) => {
        const req = client.get(url.toString(), (res) => {
            res.resume();
            resolve(res.statusCode === 200);
        });
        req.on('error', () => resolve(false));
        req.setTimeout(5000, () => {
            req.destroy();
            resolve(false);
        });
    });
}

/**
 * 스모크 테스트를 실행한다.
 */
async function main() {
    console.log(`${color.bold}인증 API 스모크 테스트 (BMA-35)${color.off}`);
    console.log(`대상: ${API}\n`);

    if (!await waitForServer()) {
        console.error(`${color.red}서버에 연결할 수 없습니다: ${BASE_URL}/actuator/health${color.off}`);
        console.error(`${color.yellow}docker compose up -d --build 로 먼저 띄우세요.${color.off}`);
        process.exitCode = 1;
        return;
    }
    console.log(`${color.dim}헬스체크 통과${color.off}\n`);

    // 매 실행마다 새 계정을 쓴다. 같은 이메일로 두 번 돌려도 깨지지 않게 한다.
    const suffix = crypto.randomBytes(4).toString('hex');
    const email = `smoke-${Date.now()}-${suffix}@bma.test`;
    const password = 'smoke1234';

    // ── 1. 회원가입 ──────────────────────────────────────────────
    console.log(`${color.bold}[1] 회원가입${color.off}  POST /signup`);
    const signup = await request('POST', '/signup', { email, password });
    check(signup.status === 200, `HTTP 200`, `실제 ${signup.status}`);
    check(signup.body.success === true, 'success=true');
    check(signup.body.data && typeof signup.body.data.userId === 'number', 'userId 반환');
    check(signup.body.data && signup.body.data.status === 'ACTIVE',
        'status=ACTIVE (가입 직후 바로 로그인 가능)', signup.body.data && signup.body.data.status);

    // ── 2. 이메일 중복 ───────────────────────────────────────────
    console.log(`\n${color.bold}[2] 이메일 중복 가입 시도${color.off}  POST /signup`);
    const dup = await request('POST', '/signup', { email, password });
    check(dup.status === 409, 'HTTP 409', `실제 ${dup.status}`);
    check(dup.body.code === 'AUTH_003', 'code=AUTH_003', dup.body.code);
    check(dup.body.data && dup.body.data.errorCode === 'EMAIL_DUPLICATE',
        'data.errorCode=EMAIL_DUPLICATE', JSON.stringify(dup.body.data));
    check(dup.body.data && dup.body.data.provider === 'LOCAL',
        'data.provider=LOCAL (가입 수단 안내)', dup.body.data && dup.body.data.provider);

    // ── 3. 자격 증명 오류 ────────────────────────────────────────
    console.log(`\n${color.bold}[3] 잘못된 비밀번호로 로그인${color.off}  POST /login`);
    const badLogin = await request('POST', '/login', { email, password: 'wrong1234' });
    check(badLogin.status === 401, 'HTTP 401', `실제 ${badLogin.status}`);
    check(badLogin.body.code === 'AUTH_001', 'code=AUTH_001', badLogin.body.code);
    check(badLogin.body.data === undefined,
        'data 키 없음 (전역 non_null)', JSON.stringify(badLogin.body.data));

    // ── 4. 로그인 ────────────────────────────────────────────────
    console.log(`\n${color.bold}[4] 로그인${color.off}  POST /login`);
    const login = await request('POST', '/login', { email, password });
    check(login.status === 200, 'HTTP 200', `실제 ${login.status}`);
    const tokens = login.body.data || {};
    check(typeof tokens.accessToken === 'string' && tokens.accessToken.length > 0, 'accessToken 발급');
    check(typeof tokens.refreshToken === 'string' && tokens.refreshToken.length > 0, 'refreshToken 발급');
    check(typeof tokens.expiresIn === 'number', `expiresIn 반환 (${tokens.expiresIn}초)`);
    check(tokens.profileCompleted === false,
        'profileCompleted=false (신규 유저 -> S2 라우팅)', String(tokens.profileCompleted));

    if (!tokens.refreshToken) {
        console.error(`\n${color.red}로그인에 실패해 이후 단계를 진행할 수 없습니다.${color.off}`);
        return report();
    }

    // ── 5. 토큰 재발급 ───────────────────────────────────────────
    console.log(`\n${color.bold}[5] 토큰 재발급${color.off}  POST /refresh`);
    const refreshed = await request('POST', '/refresh', { refreshToken: tokens.refreshToken });
    check(refreshed.status === 200, 'HTTP 200', `실제 ${refreshed.status}`);
    const rotated = refreshed.body.data || {};
    check(typeof rotated.accessToken === 'string', '새 accessToken 발급');
    check(rotated.refreshToken && rotated.refreshToken !== tokens.refreshToken,
        'refreshToken 이 새 값으로 교체됨 (로테이션)');

    // ── 6. 회수된 리프레시 토큰 재사용 ───────────────────────────
    console.log(`\n${color.bold}[6] 이전 refreshToken 재사용${color.off}  POST /refresh`);
    const reuse = await request('POST', '/refresh', { refreshToken: tokens.refreshToken });
    check(reuse.status === 401, 'HTTP 401', `실제 ${reuse.status}`);
    check(reuse.body.code === 'AUTH_008', 'code=AUTH_008 (재사용 탐지)', reuse.body.code);

    // 재사용 탐지는 해당 사용자의 모든 리프레시 토큰을 폐기한다. 다시 로그인해서 이어간다.
    console.log(`${color.dim}    재사용 탐지로 전 세션이 폐기되었다. 다시 로그인한다.${color.off}`);
    const relogin = await request('POST', '/login', { email, password });
    const fresh = relogin.body.data || {};
    check(relogin.status === 200 && !!fresh.refreshToken, '재로그인 성공');

    if (!fresh.refreshToken) {
        return report();
    }

    // ── 7. 로그아웃 ──────────────────────────────────────────────
    console.log(`\n${color.bold}[7] 로그아웃${color.off}  POST /logout`);
    const logout = await request('POST', '/logout', { refreshToken: fresh.refreshToken }, fresh.accessToken);
    check(logout.status === 200, 'HTTP 200', `실제 ${logout.status}`);
    check(logout.body.success === true, 'success=true');
    check(logout.body.data === undefined,
        'data 키 없음 (본문 없는 성공 응답)', JSON.stringify(logout.body.data));

    // ── 8. 인증 없이 로그아웃 ────────────────────────────────────
    console.log(`\n${color.bold}[8] 인증 없이 로그아웃${color.off}  POST /logout`);
    const noAuth = await request('POST', '/logout', { refreshToken: fresh.refreshToken });
    check(noAuth.status === 401, 'HTTP 401 (로그아웃은 인증 필요)', `실제 ${noAuth.status}`);

    // ── 9. 로그아웃한 토큰으로 재발급 ────────────────────────────
    console.log(`\n${color.bold}[9] 로그아웃한 refreshToken 으로 재발급${color.off}  POST /refresh`);
    const afterLogout = await request('POST', '/refresh', { refreshToken: fresh.refreshToken });
    check(afterLogout.status === 401, 'HTTP 401', `실제 ${afterLogout.status}`);
    check(afterLogout.body.code === 'AUTH_008', 'code=AUTH_008', afterLogout.body.code);

    report();
}

/**
 * 결과를 요약하고 종료 코드를 정한다.
 */
function report() {
    console.log(`\n${'─'.repeat(50)}`);
    if (failed === 0) {
        console.log(`${color.green}${color.bold}전부 통과: ${passed}건${color.off}`);
    } else {
        console.log(`${color.red}${color.bold}실패 ${failed}건${color.off} / 통과 ${passed}건`);
        process.exitCode = 1;
    }
}

main().catch((error) => {
    console.error(`\n${color.red}${color.bold}스모크 테스트 중단${color.off}`);
    console.error(`${color.red}${error.message}${color.off}`);
    process.exitCode = 1;
});
