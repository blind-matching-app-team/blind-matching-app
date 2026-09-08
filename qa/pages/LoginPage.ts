import { Locator, Page } from '@playwright/test';
import { BasePage } from './BasePage';

export class LoginPage extends BasePage {
  readonly loginTab:Locator;
  readonly signupTab:Locator;

  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly markButton: Locator;
  readonly loginButton: Locator;

  readonly dividerLine: Locator;
  readonly kakaoLoginButton: Locator;
  readonly naverLoginButton: Locator;
  readonly googleLoginButton: Locator;

  readonly footerText: Locator;
  readonly footerButton: Locator;


  constructor(page: Page) {
    super(page);
    this.loginTab = page.getByRole('tab', { name: '로그인' });
    this.signupTab = page.getByRole('tab', { name: '회원가입' });
    this.emailInput = page.locator('input[type="email"]');
    this.passwordInput = page.locator('input[type="password"]');
    this.markButton = page.locator('.password-toggle')
    this.loginButton = page.getByRole('button', { name: '로그인' });
    this.kakaoLoginButton = page.getByRole('button', { name: '카카오로 계속하기' });
    this.naverLoginButton = page.getByRole('button', { name: '네이버로 계속하기' });
    this.googleLoginButton = page.getByRole('button', { name: '구글로 계속하기' });
    this.footerText = page.locator('div[class=auth-footer]');
    this.footerButton = page.getByRole('button', { name: '회원가입' });
  }

  async open() {
    await this.goto('/login');
  }

  async login(email: string, password: string) {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(password);
    await this.loginButton.click();
  }
}
