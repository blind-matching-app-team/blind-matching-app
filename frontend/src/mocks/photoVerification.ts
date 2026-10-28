import { delay, http, HttpResponse } from 'msw';
import { setupKey } from '../lib/preferences';

// Scenarios (bma-photo-scenario): pass (default) | fail | no-photo | error
const scenario = () => sessionStorage.getItem('bma-photo-scenario') || 'pass';
const verifiedKey = () => setupKey('photo-verified');
const attemptsKey = () => setupKey('photo-attempts');
const fail = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message }, { status });

export const photoVerificationHandlers = [
  http.get('/api/mock/verification/photo/status', () => {
    const verifiedAt = localStorage.getItem(verifiedKey());
    return HttpResponse.json({
      data: {
        verified: Boolean(verifiedAt),
        verifiedAt,
        attempts: Number(localStorage.getItem(attemptsKey()) || 0),
        lastResult: verifiedAt ? 'PASS' : null,
      },
    });
  }),
  http.post('/api/mock/verification/photo', async ({ request }) => {
    await delay(900);
    if (localStorage.getItem(verifiedKey()))
      return fail('VERIFY_012', '이미 사진인증을 완료했어요.', 409);
    const file = (await request.formData()).get('file');
    if (!(file instanceof File) || !file.size || file.size > 10 * 1024 * 1024)
      return fail('USER_005', '사진을 다시 찍어주세요.', 400);
    localStorage.setItem(
      attemptsKey(),
      String(Number(localStorage.getItem(attemptsKey()) || 0) + 1),
    );
    const current = scenario();
    if (current === 'no-photo')
      return fail(
        'VERIFY_010',
        '대조할 프로필 사진이 없어요. 프로필 사진을 먼저 등록해주세요.',
        409,
      );
    if (current === 'error')
      return fail(
        'VERIFY_013',
        '사진 대조 서비스에 일시적인 문제가 있어요. 잠시 후 다시 시도해주세요.',
        502,
      );
    if (current === 'fail') return fail('VERIFY_011', '프로필 사진과 일치하지 않아요.', 422);
    // The selfie bytes are dropped here, matching the no-storage rule.
    const verifiedAt = new Date().toISOString().slice(0, 19);
    localStorage.setItem(verifiedKey(), verifiedAt);
    return HttpResponse.json({
      data: { verified: true, similarity: 100, threshold: 80, provider: 'stub', verifiedAt },
    });
  }),
];
