import { delay, http, HttpResponse } from 'msw';
import { getPasswordRules } from '../lib/passwordRules';

// The mock has no stored password: any current password passes except "wrong1234",
// which reproduces AUTH_015.
const WRONG_PASSWORD = 'wrong1234';
const fail = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message }, { status });

export const accountHandlers = [
  http.put('/api/mock/users/me/password', async ({ request }) => {
    await delay(400);
    const { currentPassword = '', newPassword = '' } = (await request.json()) as {
      currentPassword?: string;
      newPassword?: string;
    };
    if (!currentPassword || !newPassword)
      return fail('COMMON_001', '비밀번호를 입력해주세요.', 400);
    if (currentPassword === WRONG_PASSWORD)
      return fail('AUTH_015', '현재 비밀번호가 일치하지 않아요.', 400);
    if (newPassword === currentPassword)
      return fail('COMMON_001', '현재 비밀번호와 다른 비밀번호를 입력해주세요.', 400);
    if (!getPasswordRules(newPassword).valid || newPassword.length > 64)
      return fail('COMMON_001', '영문과 숫자를 포함해 8~64자로 입력해주세요.', 400);
    return HttpResponse.json({
      data: { changedAt: new Date().toISOString().slice(0, 19), sessionsEnded: 1 },
    });
  }),
];
