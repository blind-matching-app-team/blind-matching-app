import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('bma_access_token', 'ui-test');
    sessionStorage.setItem('bma_refresh_token', 'ui-test');
    sessionStorage.setItem('bma_user_id', '1');
  });
});

test('S10 opens matching conversation, sends and receives, persists read counts', async ({ page }) => {
  await page.goto('/');
  const progress = await page.getByRole('progressbar').getAttribute('aria-valuenow');
  await page.getByRole('button', { name: '대화 시작하기' }).click();
  await expect(page).toHaveURL(/\/chat\/1$/);
  await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', progress!);
  const input = page.getByRole('textbox', { name: '메시지', exact: true });
  await input.fill('반가워요!');
  await page.getByRole('button', { name: '전송', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('반가워요!');
  await expect(page.getByRole('log')).toContainText('좋아요! 조금 더 이야기 나눠요');
  await page.getByRole('link', { name: '채팅목록으로 돌아가기' }).click();
  await expect(page.locator('.room-unread')).toHaveCount(0);
  await expect(page.locator('.desktop-sidebar .nav-badge')).toHaveCount(0);
  await page.reload();
  await expect(page.locator('.room-unread')).toHaveCount(0);
  await page.locator('.chat-room-main').first().click();
  await expect(page.getByRole('log')).toContainText('반가워요!');
});

test('notifications stay read after navigation and reload', async ({ page }) => {
  await page.goto('/notifications');
  await expect(page.locator('.notification-dot')).toHaveCount(2);
  await page.getByRole('button', { name: /미인증 상대 알림/ }).click();
  await page.getByRole('button', { name: '알림', exact: true }).click();
  await expect(page.locator('.notification-dot')).toHaveCount(1);
  await page.reload();
  await expect(page.locator('.notification-dot')).toHaveCount(1);
  await page.getByRole('button', { name: '전체 읽음' }).click();
  await page.reload();
  await expect(page.locator('.notification-dot')).toHaveCount(0);
});

test('report has five types and fraud enters immediate review', async ({ page }) => {
  await page.goto('/chat/1');
  await page.getByRole('button', { name: '채팅 메뉴', exact: true }).click();
  await expect(page.locator('#chat-actions button')).toHaveCount(3);
  await page.getByRole('button', { name: '신고하기', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '신고하기', exact: true });
  await expect(dialog.getByRole('radio')).toHaveCount(5);
  await expect(dialog.getByRole('button', { name: '신고하기' })).toBeDisabled();
  await dialog.getByRole('radio', { name: '사기 및 금전 요구' }).check();
  await dialog.getByLabel('상세사유 (선택)').fill('금전을 요구했습니다.');
  await dialog.getByRole('button', { name: '신고하기' }).click();
  await expect(dialog).toBeHidden();
  const reports = await page.evaluate(() => JSON.parse(sessionStorage.getItem('bma-chat-safety-1')!).reports);
  expect(reports[0]).toMatchObject({ type: 'FRAUD', detail: '금전을 요구했습니다.', queue: 'IMMEDIATE_REVIEW' });
});

test('blocking is silent and suppresses partner delivery', async ({ page }) => {
  await page.goto('/chat/1');
  await page.getByRole('button', { name: '채팅 메뉴', exact: true }).click();
  await page.getByRole('button', { name: '차단하기', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '이 사람을 차단하시겠어요?' });
  await dialog.getByRole('button', { name: '차단하기' }).click();
  await expect(dialog).toBeHidden();
  await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem('bma-chat-safety-1') || '{}').blocked)).toEqual([1]);
  await page.getByRole('textbox', { name: '메시지', exact: true }).fill('확인 메시지');
  await page.getByRole('button', { name: '전송', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('확인 메시지');
  await page.waitForTimeout(1200);
  await expect(page.getByRole('log')).not.toContainText('좋아요! 조금 더 이야기 나눠요');
  await expect(page.locator('.cm-toast')).toHaveCount(0);
});

test('blocked sender sees ordinary success without revealing block', async ({ page }) => {
  await page.goto('/chat/1');
  await page.evaluate(() => sessionStorage.setItem('bma-chat-safety-1', JSON.stringify({ blocked: [], blockedBy: [1], reports: [] })));
  await page.getByRole('textbox', { name: '메시지', exact: true }).fill('안녕하세요');
  await page.getByRole('button', { name: '전송', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('안녕하세요');
  await expect(page.locator('.message-row--mine').last()).toContainText('전송됨');
  await page.waitForTimeout(1200);
  await expect(page.getByRole('log')).not.toContainText('좋아요! 조금 더 이야기 나눠요');
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('leave uses confirmation and remains removed after reload', async ({ page }) => {
  await page.goto('/chat/1');
  await page.getByRole('button', { name: '채팅 메뉴', exact: true }).click();
  await page.getByRole('button', { name: '채팅방 나가기' }).click();
  await page.getByRole('dialog', { name: '채팅방을 나가시겠어요?' }).getByRole('button', { name: '나가기', exact: true }).click();
  await expect(page).toHaveURL(/\/chat$/);
  await expect(page.locator('.chat-room-item')).toHaveCount(2);
  await page.reload();
  await expect(page.locator('.chat-room-item')).toHaveCount(2);
  await page.goto('/chat/1');
  await expect(page.getByRole('heading', { name: '채팅방을 찾을 수 없어요' })).toBeVisible();
});

test('ended conversation is read only and mobile composer stays in viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/chat/3');
  await expect(page.getByRole('textbox', { name: '메시지', exact: true })).toBeDisabled();
  await page.goto('/chat/1');
  const input = page.getByRole('textbox', { name: '메시지', exact: true });
  await input.fill('첫 줄');
  await input.press('Shift+Enter');
  await input.press('A');
  await expect(input).toHaveValue('첫 줄\nA');
  const bounds = await page.getByRole('button', { name: '전송', exact: true }).boundingBox();
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(844);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/chat-mobile.png', fullPage: true });
});

test('failed send retains draft and retry sends once', async ({ page }) => {
  await page.goto('/chat/1');
  await page.evaluate(() => {
    const original = window.fetch;
    let failOnce = true;
    window.fetch = (...args) => {
      if (failOnce && String(args[0]).endsWith('/messages')) {
        failOnce = false;
        return Promise.resolve(new Response(JSON.stringify({ message: '일시적인 전송 오류' }), { status: 503 }));
      }
      return original(...args);
    };
  });
  const input = page.getByRole('textbox', { name: '메시지', exact: true });
  await input.fill('다시 전송할 메시지');
  await page.getByRole('button', { name: '전송', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('일시적인 전송 오류');
  await expect(input).toHaveValue('다시 전송할 메시지');
  await page.getByRole('button', { name: '전송', exact: true }).click();
  await expect(input).toHaveValue('');
  await expect(page.locator('.message-bubble').filter({ hasText: '다시 전송할 메시지' })).toHaveCount(1);
});

test('incoming response outside the room increments unread count', async ({ page }) => {
  await page.goto('/chat/1');
  await page.getByRole('textbox', { name: '메시지', exact: true }).fill('잠시 후 확인할게요');
  await page.getByRole('button', { name: '전송', exact: true }).click();
  await expect(page.getByRole('textbox', { name: '메시지', exact: true })).toHaveValue('');
  await page.getByRole('link', { name: '채팅목록으로 돌아가기' }).click();
  await expect(page.locator('.room-unread')).toHaveText('1');
  await page.locator('.chat-room-main').first().click();
  await page.getByRole('link', { name: '채팅목록으로 돌아가기' }).click();
  await expect(page.locator('.room-unread')).toHaveCount(0);
});
