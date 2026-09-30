import { expect, test } from '@playwright/test';

for (const verified of [false, true]) {
  test(`S4 enters S9 ${verified ? 'directly' : 'after S15 verification'}`, async ({ page }) => {
    await page.addInitScript((value) => {
      localStorage.setItem('bma_setup_1_verified', String(value));
    }, verified);
    await page.goto('/profile/preferences');
    await page.getByRole('button', { name: '매칭 시작하기' }).click();
    if (!verified) {
      await expect(page).toHaveURL('/identity-verification');
      await page.getByRole('button', { name: '목 인증 완료 후 계속' }).click();
    }
    await expect(page).toHaveURL('/matching/waiting');
    await expect(page.getByRole('heading', { name: '매칭 상대를 찾고 있어요' })).toBeVisible();
  });
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('bma_access_token', 'ui-test');
    sessionStorage.setItem('bma_refresh_token', 'ui-test');
    sessionStorage.setItem('bma_user_id', '1');
  });
});

test('S5 entry, elapsed time, resume after reload, and confirmed queue removal', async ({
  page,
}) => {
  await page.goto('/');
  await page.getByRole('button', { name: '매칭 없음', exact: true }).click();
  await page.getByRole('button', { name: '매칭 시작하기' }).click();
  await expect(page.getByRole('heading', { name: '매칭 상대를 찾고 있어요' })).toBeVisible();
  await expect(page.getByRole('timer')).toHaveText('00:02', { timeout: 6000 });
  const startedAt = await page.evaluate(
    () => JSON.parse(sessionStorage.getItem('bma-matching-queue-1')!).startedAt,
  );
  await page.reload();
  await expect(page.getByRole('button', { name: '대기 취소', exact: true })).toBeEnabled();
  expect(
    await page.evaluate(
      () => JSON.parse(sessionStorage.getItem('bma-matching-queue-1')!).startedAt,
    ),
  ).toBe(startedAt);
  await page.getByRole('button', { name: '뒤로가기' }).click();
  const dialog = page.getByRole('dialog', {
    name: '매칭 대기를 취소하시겠어요?',
  });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: '계속 기다리기' }).click();
  await expect(page).toHaveURL('/matching/waiting');
  await page.getByRole('button', { name: '대기 취소', exact: true }).click();
  await dialog.getByRole('button', { name: '대기 취소', exact: true }).click();
  await expect(page).toHaveURL('/');
  expect(await page.evaluate(() => sessionStorage.getItem('bma-matching-queue-1'))).toBeNull();
});

test('five minute timeout, retry with a new clock, and later returns to S5', async ({ page }) => {
  await page.goto('/matching/waiting');
  await expect(page.getByRole('button', { name: '대기 취소', exact: true })).toBeEnabled();
  await page.clock.install();
  await page.clock.fastForward(300_000);
  await expect(
    page.getByRole('heading', { name: '지금은 매칭 가능한 상대가 없어요' }),
  ).toBeVisible();
  await page.getByRole('button', { name: '다시 시도' }).click();
  await expect(page.getByRole('timer')).toHaveText('00:00');
  await page.clock.fastForward(300_000);
  await expect(page.getByRole('button', { name: '나중에 하기' })).toBeVisible();
  await page.getByRole('button', { name: '나중에 하기' }).click();
  await expect(page).toHaveURL('/');
});

test('MSW matched response automatically opens S10', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('bma-matching-scenario', 'matched'));
  await page.goto('/matching/waiting');
  await expect(page).toHaveURL('/chat/1', { timeout: 10000 });
});

test('exhausted chances open consumable ticket modal without registering a queue', async ({
  page,
}) => {
  await page.addInitScript(() => sessionStorage.setItem('bma-matching-scenario', 'exhausted'));
  await page.goto('/matching/waiting');
  const dialog = page.getByRole('dialog', { name: '이용권 구매' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('tab', { name: '소모형' })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  expect(await page.evaluate(() => sessionStorage.getItem('bma-matching-queue-1'))).toBeNull();
  await dialog.getByRole('button', { name: '홈으로 돌아가기' }).click();
  await expect(page).toHaveURL('/');
});

test('mobile empty scenario, reduced motion, and no sidebar', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.addInitScript(() => sessionStorage.setItem('bma-matching-scenario', 'empty'));
  await page.goto('/matching/waiting');
  await expect(page.locator('.matching-pulse span')).toHaveCSS('animation-name', 'none');
  await expect(page.locator('.hub-sidebar')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '지금은 매칭 가능한 상대가 없어요' })).toBeVisible(
    { timeout: 10000 },
  );
  const card = await page.locator('.matching-waiting-card').boundingBox();
  expect(card!.x).toBeGreaterThanOrEqual(0);
  expect(card!.x + card!.width).toBeLessThanOrEqual(320);
});
