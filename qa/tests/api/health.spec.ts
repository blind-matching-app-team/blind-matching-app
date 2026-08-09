import { test, expect } from '@playwright/test';

test('백엔드 서버 정상 기동 확인', async ({ request }) => {
  const res = await request.get('/actuator/health');
  expect(res.status()).toBe(200);

  const body = await res.json();
  expect(body.status).toBe('UP');
});