import { delay, http, HttpResponse } from 'msw';
import { setupKey, validatePreferences, type Preferences } from '../lib/preferences';

export const preferenceHandlers = [
  http.put('/api/mock/preferences', async ({ request }) => {
    await delay(300);
    const value = (await request.json()) as Preferences;
    const message = validatePreferences(value);
    if (message) return HttpResponse.json({ message }, { status: 400 });
    try {
      localStorage.setItem(setupKey('preferences'), JSON.stringify(value));
      localStorage.removeItem(setupKey('preferences-draft'));
    } catch {
      return HttpResponse.json(
        { message: '저장 공간이 부족해요. 다시 시도해주세요.' },
        { status: 507 },
      );
    }
    return HttpResponse.json({
      data: { identityVerified: localStorage.getItem(setupKey('verified')) === 'true' },
    });
  }),
  http.post('/api/mock/identity-verification', async () => {
    await delay(300);
    try {
      localStorage.setItem(setupKey('verified'), 'true');
    } catch {
      return HttpResponse.json({ message: '인증 상태를 저장하지 못했어요.' }, { status: 507 });
    }
    return HttpResponse.json({ success: true });
  }),
];
