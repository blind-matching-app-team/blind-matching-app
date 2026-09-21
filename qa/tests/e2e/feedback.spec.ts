import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('bma_access_token', 'ui-test');
    sessionStorage.setItem('bma_refresh_token', 'ui-test');
    sessionStorage.setItem('bma_user_id', '1');
  });
});

for (const path of ['/', '/chat', '/notifications', '/profile']) {
  test(`shared logout confirmation on ${path}`, async ({ page }) => {
    await page.goto(path);
    const trigger = page.locator('.sidebar-logout');
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: '로그아웃 하시겠어요?' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: '로그아웃', exact: true }))
      .toHaveCSS('background-color', 'rgb(237, 147, 177)');
    await page.mouse.click(5, 5);
    await page.keyboard.press('Escape');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: '취소' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(trigger).toBeFocused();
    await trigger.click();
    await dialog.getByRole('button', { name: '로그아웃', exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
}

test('danger confirmation uses withdrawal copy and can be cancelled', async ({ page }) => {
  await page.goto('/profile');
  await page.getByRole('button', { name: '회원 탈퇴' }).click();
  const dialog = page.getByRole('dialog', { name: '정말 탈퇴하시겠어요?' });
  await expect(dialog).toContainText('탈퇴하면 모든 정보가 삭제되고 복구할 수 없어요.');
  await expect(dialog.getByRole('button', { name: '탈퇴하기' }))
    .toHaveCSS('background-color', 'rgb(226, 75, 74)');
  await dialog.getByRole('button', { name: '취소' }).click();
  await expect(page).toHaveURL(/\/profile$/);
});

test('repeated toast restarts its three-second lifetime and survives navigation', async ({ page }) => {
  await page.goto('/profile');
  await page.clock.install();
  await page.clock.pauseAt(new Date(Date.now() + 1000));
  const trigger = page.getByRole('button', { name: '결제 내역' });
  await trigger.click();
  const toast = page.getByRole('status');
  await expect(toast).toHaveText('준비 중인 기능이에요');
  await page.clock.runFor(2000);
  await trigger.click();
  await page.getByRole('button', { name: '알림', exact: true }).click();
  await page.clock.runFor(2999);
  await expect(toast).toBeVisible();
  await page.clock.runFor(1);
  await expect(toast).toHaveCount(0);
});

test('S1 social cancellation uses the shared toast', async ({ page }) => {
  await page.goto('/login');
  await page.clock.install();
  await page.clock.pauseAt(new Date(Date.now() + 1000));
  await page.getByRole('button', { name: /카카오/ }).click();
  await expect(page.getByRole('status')).toHaveText('로그인이 취소됐어요');
  await page.clock.runFor(3000);
  await expect(page.getByRole('status')).toHaveCount(0);
});

test('rematching opens the common normal confirmation before navigating', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '재매칭하기', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '재매칭 하시겠어요?' });
  await expect(dialog).toContainText('재매칭권 1개가 소모되고, 지금 매칭은 종료돼요.');
  await dialog.getByRole('button', { name: '재매칭하기' }).click();
  await expect(page).toHaveURL(/\/matching\/waiting$/);
});
