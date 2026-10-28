import { delay, http, HttpResponse } from 'msw';
import type { IdentityRequest } from '../api/identity';
import { setupKey } from '../lib/preferences';

// Adult/minor follows the birth date typed into the mock window. Server-side outcomes that
// cannot be typed are forced with bma-identity-scenario: duplicate | fail
const scenario = () => sessionStorage.getItem('bma-identity-scenario');
const pendingKey = () => setupKey('identity-transaction');
const fail = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message }, { status });

/** Stands in for the provider window; the payload mirrors the backend stub gateway. */
export async function openMockIdentitySdk(request: IdentityRequest) {
  const { showMockIdentitySdk } = await import('./identitySdk');
  const result = await showMockIdentitySdk();
  return {
    transactionId: request.transactionId,
    result: { ...result, ...(scenario() === 'fail' ? { invalid: true } : {}) },
  };
}

function isAdult(birthDate: string) {
  const [y, m, d] = birthDate.split('-').map(Number);
  const today = new Date();
  const age =
    today.getFullYear() -
    y -
    (today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d) ? 1 : 0);
  return age >= 19;
}

export const identityHandlers = [
  http.get('/api/mock/verification/identity/status', () => {
    const verified = localStorage.getItem(setupKey('verified')) === 'true';
    return HttpResponse.json({
      data: {
        verified,
        verifiedAt: verified ? localStorage.getItem(setupKey('verified-at')) : null,
        provider: verified ? 'stub' : null,
        matchingAllowed: verified,
        lastAttemptStatus: verified ? 'VERIFIED' : null,
      },
    });
  }),
  http.post('/api/mock/verification/identity/request', async () => {
    await delay(200);
    if (localStorage.getItem(setupKey('verified')) === 'true')
      return fail('VERIFY_002', '이미 본인인증을 완료했어요.', 409);
    const transactionId = crypto.randomUUID();
    // A new request expires the previous transaction.
    sessionStorage.setItem(pendingKey(), transactionId);
    return HttpResponse.json({
      data: {
        transactionId,
        provider: 'stub',
        sdkParams: {},
        expiresAt: new Date(Date.now() + 10 * 60_000).toISOString().slice(0, 19),
      } satisfies IdentityRequest,
    });
  }),
  http.post('/api/mock/verification/identity/confirm', async ({ request }) => {
    await delay(300);
    const body = (await request.json()) as {
      transactionId?: string;
      providerPayload?: { result?: { birthDate?: string; invalid?: boolean } };
    };
    if (!body.transactionId || body.transactionId !== sessionStorage.getItem(pendingKey()))
      return fail('VERIFY_005', '인증 요청을 찾을 수 없어요. 다시 시도해주세요.', 404);
    sessionStorage.removeItem(pendingKey());
    const result = body.providerPayload?.result;
    if (!result?.birthDate || result.invalid)
      return fail('VERIFY_007', '본인인증에 실패했어요. 다시 시도해주세요.', 400);
    if (scenario() === 'duplicate')
      return fail('VERIFY_004', '이미 다른 계정에서 본인인증을 완료한 정보예요.', 409);
    if (!isAdult(result.birthDate)) {
      // The server rolls the account back here (withdrawal + token revoke), before S15-09.
      for (const name of ['profile-completed', 'preferences', 'preferences-draft', 'verified'])
        localStorage.removeItem(setupKey(name));
      return fail('VERIFY_003', '만 19세 이상만 가입할 수 있어요.', 403);
    }
    const verifiedAt = new Date().toISOString().slice(0, 19);
    localStorage.setItem(setupKey('verified'), 'true');
    localStorage.setItem(setupKey('verified-at'), verifiedAt);
    return HttpResponse.json({
      data: {
        status: 'VERIFIED',
        verifiedAt,
        provider: 'stub',
        adult: true,
        matchingAllowed: true,
      },
    });
  }),
];
