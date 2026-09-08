import { Locator, Page } from '@playwright/test';
import { BasePage } from './BasePage';

export class LoginPage extends BasePage {
    readonly loginTab:Locator;
    readonly signupTab:Locator;

    readonly emailInput: Locator;
    readonly passwordInput: Locator;
    readonly confirmPasswordInput: Locator;

    readonly markButton: Locator;
    readonly markConfirmButton: Locator;
    readonly signupButton: Locator;
    readonly checkbox: Locator;

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
        this.passwordInput = page.locator('input[placeholder="비밀번호"]');
        this.confirmPasswordInput = page.locator('input[placeholder="비밀번호 확인"]');

        this.markButton = page.locator("//button[@aria-label='비밀번호 표시']");
        this.markConfirmButton = page.locator("//button[@aria-label='비밀번호 확인 표시']");

        this.checkbox = page.locator('input[type="checkbox"]');

        this.signupButton = page.getByRole('button', { name: '회원가입' });
        this.kakaoLoginButton = page.getByRole('button', { name: '카카오로 계속하기' });
        this.naverLoginButton = page.getByRole('button', { name: '네이버로 계속하기' });
        this.googleLoginButton = page.getByRole('button', { name: '구글로 계속하기' });
        this.footerText = page.locator('div[class=auth-footer]');
        this.footerButton = page.getByRole('button', { name: '회원가입' });
    }

    async open() {
        await this.goto('/login');
    }

    async signup(email: string, password: string) {
        await this.emailInput.fill(email);
        await this.passwordInput.fill(password);
        await this.confirmPasswordInput.fill(password);
        await this.signupButton.click();
    }
}