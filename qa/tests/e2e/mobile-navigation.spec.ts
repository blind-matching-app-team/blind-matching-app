import { expect, test } from '@playwright/test';

test.use({ viewport: { width: 390, height: 844 } });

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('bma_access_token', 'ui-test');
    sessionStorage.setItem('bma_refresh_token', 'ui-test');
    sessionStorage.setItem('bma_user_id', '1');
  });
  await page.goto('/');
});

test('mobile menu opens on the left, closes and navigates', async ({ page }) => {
  const trigger = page.getByRole('button', { name: '메뉴 열기' });
  const drawer = page.getByRole('dialog', { name: '메인 메뉴', exact: true });
  await expect(page.locator('.desktop-sidebar')).toBeHidden();
  await trigger.click();
  await expect(drawer).toBeVisible();
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden');
  await expect(drawer).toHaveCSS('margin-left', '0px');
  await page.keyboard.press('Escape');
  await expect(drawer).toBeHidden();
  await expect(trigger).toBeFocused();
  await trigger.click();
  await page.mouse.click(380, 400);
  await expect(drawer).toBeHidden();
  await trigger.click();
  await drawer.getByRole('button', { name: '채팅목록' }).click();
  await expect(page).toHaveURL(/\/chat$/);
  await expect(drawer).toBeHidden();
  await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden');
});

test('drawer closes on desktop resize and respects reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.getByRole('button', { name: '메뉴 열기' }).click();
  const drawer = page.getByRole('dialog', { name: '메인 메뉴', exact: true });
  await expect(drawer).toHaveCSS('animation-name', 'none');
  await page.setViewportSize({ width: 1280, height: 800 });
  await expect(drawer).toBeHidden();
  await expect(page.locator('.desktop-sidebar')).toBeVisible();
  await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden');
});

test('mobile logout confirmation can be cancelled without locking the page', async ({ page }) => {
  const trigger = page.getByRole('button', { name: '메뉴 열기' });
  await trigger.click();
  await page.getByRole('dialog', { name: '메인 메뉴', exact: true })
    .getByRole('button', { name: '로그아웃' }).click();
  const confirmation = page.getByRole('dialog', { name: '로그아웃 하시겠어요?' });
  await expect(confirmation).toBeVisible();
  await confirmation.getByRole('button', { name: '취소' }).click();
  await expect(page.locator('body')).not.toHaveCSS('overflow', 'hidden');
  await expect(trigger).toBeFocused();
});
