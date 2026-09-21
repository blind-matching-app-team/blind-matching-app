import { expect, test } from '@playwright/test';

// Development server required: these smoke checks exercise the actual MSW contract.
test('login entry, email validation, and identical send confirmation', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('link', { name: '비밀번호를 잊으셨나요?' }).click();
  await expect(page).toHaveURL(/\/forgot-password$/);
  await page.getByRole('button', { name: '재설정 링크 보내기' }).click();
  await expect(page.getByRole('alert')).toHaveText('이메일 형식을 확인해주세요');
  for (const email of ['member@example.com', 'unknown@example.com']) {
    await page.getByRole('textbox', { name: '이메일' }).fill(email);
    await page.getByRole('button', { name: '재설정 링크 보내기' }).click();
    await expect(page.getByRole('status')).toHaveText('비밀번호 재설정 링크를 발송했어요');
    await expect(page.getByRole('button', { name: '링크 다시 보내기' })).toBeEnabled();
  }
  await page.getByRole('link', { name: '로그인으로 돌아가기' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('direct email link validates the token and changes the password', async ({ page }) => {
  await page.goto('/reset-password?token=demo-reset-token');
  await expect(page.getByRole('heading', { name: '새 비밀번호 설정' })).toBeVisible();
  await page.getByLabel('새 비밀번호', { exact: true }).fill('short');
  await page.getByLabel('새 비밀번호 확인', { exact: true }).fill('different');
  await page.getByRole('button', { name: '비밀번호 변경하기' }).click();
  await expect(page.getByText('8자 이상, 영문과 숫자를 포함해주세요')).toBeVisible();
  await expect(page.getByText('비밀번호가 일치하지 않아요')).toBeVisible();
  await page.getByLabel('새 비밀번호', { exact: true }).fill('NewPassword123');
  await page.getByLabel('새 비밀번호 확인', { exact: true }).fill('NewPassword123');
  await page.getByRole('button', { name: '새 비밀번호 표시', exact: true }).click();
  await expect(page.getByLabel('새 비밀번호', { exact: true })).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: '비밀번호 변경하기' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('status')).toHaveText('비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요');
});

for (const token of ['', 'invalid', 'expired-reset-token']) {
  test(`unusable reset link: ${token || 'missing'}`, async ({ page }) => {
    await page.goto(`/reset-password${token ? `?token=${token}` : ''}`);
    await expect(page.getByRole('heading', { name: '재설정 링크를 사용할 수 없어요' })).toBeVisible();
    await expect(page.getByLabel('새 비밀번호', { exact: true })).toHaveCount(0);
    if (token === 'expired-reset-token') {
      await expect(page.getByText('재설정 링크가 만료됐어요. 새 링크를 요청해주세요.')).toBeVisible();
    }
    await page.getByRole('link', { name: '새 재설정 링크 받기' }).click();
    await expect(page).toHaveURL(/\/forgot-password$/);
  });
}

test('mobile reset form fits the viewport and returns to login', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/reset-password?token=demo-reset-token');
  await expect(page.getByRole('heading', { name: '새 비밀번호 설정' })).toBeVisible();
  const card = await page.locator('.reset-card').boundingBox();
  expect(card).not.toBeNull();
  expect(card!.x).toBeGreaterThanOrEqual(0);
  expect(card!.x + card!.width).toBeLessThanOrEqual(375);
  await page.screenshot({ path: test.info().outputPath('password-reset-mobile.png'), fullPage: true });
  await page.getByRole('link', { name: '로그인으로 돌아가기' }).click();
  await expect(page).toHaveURL(/\/login$/);
});
