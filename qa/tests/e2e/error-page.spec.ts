import { expect, test } from '@playwright/test';

for (const signedIn of [false, true]) {
  test(`unknown paths show 404 and return to ${signedIn ? 'S5' : 'S1'}`, async ({ page }) => {
    if (signedIn) {
      await page.addInitScript(() => {
        sessionStorage.setItem('bma_access_token', 'ui-test');
        sessionStorage.setItem('bma_refresh_token', 'ui-test');
        sessionStorage.setItem('bma_user_id', '1');
      });
    }
    for (const path of ['/missing-page', '/profile/unknown/deep?source=test', '/login/unknown']) {
      await page.goto(path);
      await expect(page.getByRole('heading', { name: '페이지를 찾을 수 없어요' })).toBeVisible();
      await expect(page.getByText('주소가 바뀌었거나 잘못 입력됐을 수 있어요')).toBeVisible();
      await expect(page).toHaveURL(path);
      await page.getByRole('button', { name: '홈으로 돌아가기' }).click();
      await expect(page).toHaveURL(signedIn ? '/' : '/login');
    }
  });
}

test('mobile 404 fits the viewport and supports keyboard navigation', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto('/missing-page');
  const button = page.getByRole('button', { name: '홈으로 돌아가기' });
  await expect(button).toBeVisible();
  const card = await page.locator('.error-card').boundingBox();
  expect(card!.x).toBeGreaterThanOrEqual(0);
  expect(card!.x + card!.width).toBeLessThanOrEqual(320);
  await page.keyboard.press('Tab');
  await expect(button).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL('/login');
});
