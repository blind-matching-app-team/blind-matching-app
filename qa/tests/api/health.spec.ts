import { test, expect } from '@playwright/test';

test.describe('Health check API', () => {
  test('GET /health 는 200을 반환한다', async ({ request }) => {
    const response = await request.get('/health');
    expect(response.ok()).toBeTruthy();
  });
});
