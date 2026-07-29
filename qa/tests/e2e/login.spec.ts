import { test, expect } from '../../fixtures/pages';

test.describe('로그인', () => {
  test('이메일/비밀번호로 로그인할 수 있다', async ({ page, loginPage }) => {
    await loginPage.open();
    await loginPage.login('user@example.com', 'password123');

    await expect(page).toHaveURL(/\/(home|dashboard)/);
  });
});
