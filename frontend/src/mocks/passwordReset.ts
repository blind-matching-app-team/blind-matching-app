import { delay, http, HttpResponse } from 'msw';
import { getPasswordRules } from '../lib/passwordRules';

const usedTokens = new Set<string>();
const success = () => HttpResponse.json({ success: true, code: 'SUCCESS', data: null });
const failure = (code: string) =>
  HttpResponse.json({ success: false, code, data: null }, { status: 400 });

function tokenError(token: unknown) {
  if (token === 'expired-reset-token') return 'RESET_TOKEN_EXPIRED';
  if (token !== 'demo-reset-token' || usedTokens.has(token)) return 'RESET_TOKEN_INVALID';
  return null;
}

export const passwordResetHandlers = [
  http.post('*/api/v1/auth/password-reset/request', async ({ request }) => {
    const body = (await request.json()) as { email?: string };
    await delay(300);
    if (typeof body.email !== 'string' || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email))
      return failure('INVALID_EMAIL');
    // Same response and delay for all valid addresses; no account lookup or real email.
    return success();
  }),
  http.post('*/api/v1/auth/password-reset/validate', async ({ request }) => {
    const body = (await request.json()) as { token?: string };
    await delay(150);
    const error = tokenError(body.token);
    return error ? failure(error) : success();
  }),
  http.post('*/api/v1/auth/password-reset/confirm', async ({ request }) => {
    const body = (await request.json()) as { token?: string; password?: string };
    await delay(300);
    const error = tokenError(body.token);
    if (error) return failure(error);
    if (typeof body.password !== 'string' || !getPasswordRules(body.password).valid)
      return failure('INVALID_PASSWORD');
    usedTokens.add(body.token!);
    return success();
  }),
];
