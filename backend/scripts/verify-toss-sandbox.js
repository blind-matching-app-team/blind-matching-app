#!/usr/bin/env node
'use strict';

/**
 * 토스페이먼츠 샌드박스 검증 스크립트 (BMA-23).
 *
 * 빌링키 발급 -> 자동결제 승인 -> 취소 를 순서대로 호출하고 각 단계의 응답을 출력한다.
 * 애플리케이션 코드를 거치지 않고 토스 API 만 직접 호출하므로, 결제 로직을 만들기 전에
 * "발급받은 키 조합이 실제로 동작하는지"를 먼저 확인할 수 있다.
 *
 * 실행:
 *   node backend/scripts/verify-toss-sandbox.js
 *
 * 필요한 값은 저장소 루트의 .env 또는 환경변수에서 읽는다.
 * 절차는 backend/docs/TOSS_SANDBOX_VERIFICATION.md 참고.
 *
 * 의존성이 없다. Node 14 이상에서 그대로 동작한다.
 */

const https = require('https');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const TEST_PREFIX = 'test_';
const LIVE_PREFIX = 'live_';
const DEFAULT_BASE_URL = 'https://api.tosspayments.com';

/** 콘솔 색상. 파이프로 넘길 때는 끈다. */
const color = process.stdout.isTTY
    ? { red: '\x1b[31m', green: '\x1b[32m', yellow: '\x1b[33m', dim: '\x1b[2m', bold: '\x1b[1m', off: '\x1b[0m' }
    : { red: '', green: '', yellow: '', dim: '', bold: '', off: '' };

/**
 * 저장소 루트의 .env 를 읽어 process.env 에 채운다. 이미 있는 값은 덮어쓰지 않는다.
 */
function loadDotEnv() {
    const envPath = path.resolve(__dirname, '..', '..', '.env');
    if (!fs.existsSync(envPath)) {
        return;
    }
    const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) {
            continue;
        }
        const eq = trimmed.indexOf('=');
        if (eq === -1) {
            continue;
        }
        const key = trimmed.slice(0, eq).trim();
        let value = trimmed.slice(eq + 1).trim();
        if (value.length >= 2 && (value[0] === '"' || value[0] === "'") && value[value.length - 1] === value[0]) {
            value = value.slice(1, -1);
        }
        if (!(key in process.env)) {
            process.env[key] = value;
        }
    }
    console.log(`${color.dim}.env 를 읽었습니다: ${envPath}${color.off}`);
}

/**
 * UUID v4 를 만든다. Node 14.17 미만에는 crypto.randomUUID 가 없어 직접 생성한다.
 *
 * @returns {string} UUID
 */
function uuidV4() {
    const bytes = crypto.randomBytes(16);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = bytes.toString('hex');
    return [
        hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20),
    ].join('-');
}

/**
 * 비밀값을 가린다. 로그와 오류 메시지에 전문이 남지 않게 한다.
 *
 * @param {string} value 원본
 * @returns {string} 가려진 문자열
 */
function mask(value) {
    if (!value) {
        return '(없음)';
    }
    if (value.length <= 12) {
        return '***';
    }
    return `${value.slice(0, 8)}***${value.slice(-4)}`;
}

/**
 * 토스 API 를 호출한다.
 *
 * @param {object} options 호출 정보
 * @param {string} options.baseUrl API 호스트
 * @param {string} options.pathname 요청 경로
 * @param {string} options.secretKey 시크릿 키
 * @param {object} options.body 요청 본문
 * @param {string} [options.idempotencyKey] 멱등키
 * @returns {Promise<{status: number, body: object|string}>} 응답
 */
function callToss(options) {
    const { baseUrl, pathname, secretKey, body, idempotencyKey } = options;
    const url = new URL(pathname, baseUrl);
    const payload = Buffer.from(JSON.stringify(body), 'utf8');

    // 토스 인증: 시크릿 키 뒤에 콜론을 붙여 Base64 로 인코딩한다.
    // BOM 이 섞이면 값이 '77u/' 로 시작하며 인증에 실패한다. Buffer 로 직접 만들어 피한다.
    const credentials = Buffer.from(`${secretKey}:`, 'utf8').toString('base64');

    const headers = {
        'Authorization': `Basic ${credentials}`,
        'Content-Type': 'application/json',
        'Content-Length': payload.length,
    };
    if (idempotencyKey) {
        headers['Idempotency-Key'] = idempotencyKey;
    }

    return new Promise((resolve, reject) => {
        const req = https.request(
            { hostname: url.hostname, port: url.port || 443, path: url.pathname, method: 'POST', headers },
            (res) => {
                const chunks = [];
                res.on('data', (chunk) => chunks.push(chunk));
                res.on('end', () => {
                    const raw = Buffer.concat(chunks).toString('utf8');
                    let parsed;
                    try {
                        parsed = raw ? JSON.parse(raw) : {};
                    } catch (e) {
                        parsed = raw;
                    }
                    resolve({ status: res.statusCode, body: parsed });
                });
            },
        );
        req.on('error', reject);
        req.setTimeout(30000, () => req.destroy(new Error('요청이 30초 안에 끝나지 않았습니다.')));
        req.write(payload);
        req.end();
    });
}

/**
 * 응답을 보기 좋게 출력하고 실패면 예외를 던진다.
 *
 * @param {string} label 단계 이름
 * @param {{status: number, body: object|string}} response 응답
 * @returns {object} 성공 응답 본문
 */
function expectOk(label, response) {
    const { status, body } = response;
    if (status >= 200 && status < 300) {
        console.log(`${color.green}  성공 (HTTP ${status})${color.off}`);
        return body;
    }
    const code = body && body.code ? body.code : '(코드 없음)';
    const message = body && body.message ? body.message : JSON.stringify(body);
    console.log(`${color.red}  실패 (HTTP ${status})${color.off}`);
    console.log(`${color.red}  code    : ${code}${color.off}`);
    console.log(`${color.red}  message : ${message}${color.off}`);

    const error = new Error(`${label} 실패: ${code} ${message}`);
    error.tossCode = code;
    throw error;
}

/**
 * 필수 환경변수를 읽는다. 없으면 안내와 함께 종료한다.
 *
 * @param {string} name 변수 이름
 * @param {string} hint 설명
 * @returns {string} 값
 */
function requireEnv(name, hint) {
    const value = (process.env[name] || '').trim();
    if (!value) {
        console.error(`${color.red}환경변수 ${name} 이(가) 없습니다. ${hint}${color.off}`);
        const error = new Error(`missing:${name}`);
        error.missingEnv = true;
        throw error;
    }
    return value;
}

/**
 * 검증 3단계를 실행한다.
 */
async function main() {
    console.log(`${color.bold}토스페이먼츠 샌드박스 검증 (BMA-23)${color.off}`);
    console.log('빌링키 발급 → 자동결제 승인 → 취소\n');

    loadDotEnv();

    const secretKey = requireEnv(
        'TOSS_SECRET_KEY',
        '개발자센터(https://developers.tosspayments.com/my/api-keys)에서 테스트 시크릿 키를 발급해 .env 에 넣으세요.',
    );

    // 실결제 차단. 이 스크립트는 어떤 경우에도 라이브 키로 동작하지 않는다.
    if (secretKey.startsWith(LIVE_PREFIX)) {
        console.error(`${color.red}라이브 키(live_)가 감지되어 중단합니다. 실제 결제가 발생할 수 있습니다.${color.off}`);
        console.error(`${color.red}테스트 키(test_)를 사용하세요. key=${mask(secretKey)}${color.off}`);
        process.exitCode = 1;
        return;
    }
    if (!secretKey.startsWith(TEST_PREFIX)) {
        console.error(`${color.red}TOSS_SECRET_KEY 가 test_ 로 시작하지 않습니다. key=${mask(secretKey)}${color.off}`);
        process.exitCode = 1;
        return;
    }

    // 클라이언트 키는 이 스크립트가 쓰지 않지만, 세트가 맞는지 미리 확인해 준다.
    // 섞여 있으면 나중에 프런트 연동에서 INVALID_API_KEY 로 터진다.
    const clientKey = (process.env.TOSS_CLIENT_KEY || '').trim();
    if (clientKey && !clientKey.startsWith(TEST_PREFIX)) {
        console.error(`${color.red}TOSS_CLIENT_KEY 가 테스트 키가 아닙니다. 시크릿 키와 세트가 맞지 않습니다. key=${mask(clientKey)}${color.off}`);
        process.exitCode = 1;
        return;
    }

    const baseUrl = (process.env.TOSS_API_BASE_URL || DEFAULT_BASE_URL).trim();
    const amount = Number(process.env.TOSS_TEST_AMOUNT || 1000);

    const card = {
        number: requireEnv('TOSS_TEST_CARD_NUMBER', '개발자센터 > 테스트 > 테스트 카드 정보에서 확인하세요. 테스트 환경은 앞 6자리(BIN)만 유효하면 됩니다.'),
        expirationYear: requireEnv('TOSS_TEST_CARD_EXPIRY_YEAR', '두 자리 연도입니다. 예: 30'),
        expirationMonth: requireEnv('TOSS_TEST_CARD_EXPIRY_MONTH', '두 자리 월입니다. 예: 12'),
        identityNumber: requireEnv('TOSS_TEST_CARD_IDENTITY', '생년월일 6자리(YYMMDD) 또는 사업자번호 10자리입니다.'),
        password: requireEnv('TOSS_TEST_CARD_PASSWORD', '카드 비밀번호 앞 두 자리입니다.'),
    };

    const customerKey = uuidV4();
    const orderId = `bma-verify-${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;

    console.log(`\n호스트      : ${baseUrl}`);
    console.log(`시크릿 키   : ${mask(secretKey)} ${color.green}(테스트)${color.off}`);
    console.log(`customerKey : ${customerKey}`);
    console.log(`orderId     : ${orderId}`);
    console.log(`금액        : ${amount.toLocaleString('ko-KR')}원\n`);

    // ---------------------------------------------------------------
    // 1단계: 빌링키 발급
    // ---------------------------------------------------------------
    console.log(`${color.bold}[1/3] 빌링키 발급${color.off}  POST /v1/billing/authorizations/card`);
    const issued = expectOk('빌링키 발급', await callToss({
        baseUrl,
        pathname: '/v1/billing/authorizations/card',
        secretKey,
        body: {
            customerKey,
            cardNumber: card.number,
            cardExpirationYear: card.expirationYear,
            cardExpirationMonth: card.expirationMonth,
            customerIdentityNumber: card.identityNumber,
            cardPassword: card.password,
        },
    }));

    const billingKey = issued.billingKey;
    if (!billingKey) {
        throw new Error('응답에 billingKey 가 없습니다. 응답: ' + JSON.stringify(issued));
    }
    console.log(`  billingKey : ${mask(billingKey)}`);
    if (issued.card) {
        console.log(`  카드       : ${issued.card.company || '?'} ${issued.card.number || ''} (${issued.card.cardType || '?'})`);
    }

    // ---------------------------------------------------------------
    // 2단계: 자동결제 승인
    // ---------------------------------------------------------------
    console.log(`\n${color.bold}[2/3] 자동결제 승인${color.off}  POST /v1/billing/{billingKey}`);
    const approved = expectOk('자동결제 승인', await callToss({
        baseUrl,
        pathname: `/v1/billing/${encodeURIComponent(billingKey)}`,
        secretKey,
        body: {
            customerKey,
            amount,
            orderId,
            orderName: 'BMA 샌드박스 검증 결제',
        },
    }));

    const paymentKey = approved.paymentKey;
    if (!paymentKey) {
        throw new Error('응답에 paymentKey 가 없습니다. 응답: ' + JSON.stringify(approved));
    }
    console.log(`  paymentKey : ${mask(paymentKey)}`);
    console.log(`  상태       : ${approved.status}`);
    console.log(`  승인 금액  : ${Number(approved.totalAmount || 0).toLocaleString('ko-KR')}원`);

    if (approved.status !== 'DONE') {
        throw new Error(`승인 상태가 DONE 이 아닙니다: ${approved.status}`);
    }
    if (Number(approved.totalAmount) !== amount) {
        throw new Error(`승인 금액이 요청 금액과 다릅니다. 요청=${amount}, 승인=${approved.totalAmount}`);
    }

    // ---------------------------------------------------------------
    // 3단계: 취소
    // ---------------------------------------------------------------
    console.log(`\n${color.bold}[3/3] 결제 취소${color.off}  POST /v1/payments/{paymentKey}/cancel`);
    const canceled = expectOk('결제 취소', await callToss({
        baseUrl,
        pathname: `/v1/payments/${encodeURIComponent(paymentKey)}/cancel`,
        secretKey,
        // 취소는 중복 호출 위험이 있어 토스가 멱등키를 권장한다.
        idempotencyKey: uuidV4(),
        body: { cancelReason: 'BMA 샌드박스 검증' },
    }));

    console.log(`  상태       : ${canceled.status}`);
    const cancels = canceled.cancels || [];
    if (cancels.length > 0) {
        console.log(`  취소 금액  : ${Number(cancels[0].cancelAmount || 0).toLocaleString('ko-KR')}원`);
    }
    if (canceled.status !== 'CANCELED') {
        throw new Error(`취소 상태가 CANCELED 가 아닙니다: ${canceled.status}`);
    }

    console.log(`\n${color.green}${color.bold}3단계 모두 성공했습니다. 테스트 키 조합이 정상 동작합니다.${color.off}`);
    console.log(`${color.dim}개발자센터 > 테스트 결제내역에서 위 orderId 로 확인할 수 있습니다.${color.off}`);
}

main().catch((error) => {
    if (error.missingEnv) {
        // requireEnv 가 이미 안내를 출력했다.
        process.exitCode = 1;
        return;
    }
    console.error(`\n${color.red}${color.bold}검증 실패${color.off}`);
    console.error(`${color.red}${error.message}${color.off}`);

    if (error.tossCode === 'NOT_SUPPORTED_METHOD' || error.tossCode === 'UNAUTHORIZED_KEY'
        || error.tossCode === 'FORBIDDEN_REQUEST') {
        console.error(`\n${color.yellow}자동결제(빌링)는 리스크 검토 및 추가 계약 후 사용할 수 있습니다.`);
        console.error(`계약 없이 테스트 키만으로는 막혀 있을 수 있습니다. 토스페이먼츠 1544-7772 로 문의하세요.${color.off}`);
    }
    if (error.tossCode === 'INVALID_API_KEY') {
        console.error(`\n${color.yellow}클라이언트 키와 시크릿 키가 세트가 아니거나 테스트/라이브를 섞어 쓴 경우입니다.`);
        console.error(`개발자센터에서 한 세트로 발급된 키를 다시 확인하세요.${color.off}`);
    }
    process.exitCode = 1;
});
