import { useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLoaderData, useNavigate } from 'react-router-dom';
import { requestPasswordReset, resetPassword } from '../api/passwordReset';
import PasswordRules from '../components/PasswordRules';
import { useToast } from '../components/useToast';
import { toastMessages } from '../components/feedbackContent';
import { getPasswordRules } from '../lib/passwordRules';
import './PasswordResetPage.css';

import { getTokenError, type TokenResult } from '../router/passwordResetLoader';

function ResetLayout({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <main className="auth-page-shell">
      <section className="ui-enter auth-card reset-card" aria-labelledby="reset-title">
        <div className="logo-badge" aria-label="Blind Matching logo">
          <span>B</span>
        </div>
        <h1 id="reset-title">{title}</h1>
        <p className="reset-description">{description}</p>
        {children}
        <div className="auth-footer">
          <Link className="ui-interactive text-link" to="/login">
            로그인으로 돌아가기
          </Link>
        </div>
      </section>
    </main>
  );
}

export function ForgotPasswordPage() {
  const showToast = useToast();
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const pending = useRef(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('이메일 형식을 확인해주세요');
      return;
    }
    setError('');
    pending.current = true;
    setLoading(true);
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
      showToast('비밀번호 재설정 링크를 발송했어요');
    } catch {
      showToast(toastMessages.networkError);
    } finally {
      pending.current = false;
      setLoading(false);
    }
  }

  return (
    <ResetLayout
      title="비밀번호를 잊으셨나요?"
      description="가입한 이메일을 입력하면 비밀번호 재설정 링크를 보내드려요."
    >
      <form className="auth-form reset-form" onSubmit={handleSubmit} noValidate aria-busy={loading}>
        <label className={`field-group ${error ? 'has-error' : ''}`}>
          <span className="reset-label">이메일</span>
          <input
            className="ui-input"
            type="email"
            autoComplete="email"
            name="email"
            placeholder="name@example.com"
            value={email}
            disabled={loading}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'reset-email-error' : undefined}
            onChange={(event) => {
              setEmail(event.target.value);
              setError('');
              setSent(false);
            }}
          />
        </label>
        {error && (
          <p className="field-message" id="reset-email-error" role="alert">
            {error}
          </p>
        )}
        <button
          type="submit"
          className="ui-button ui-button--primary submit-button"
          disabled={loading}
        >
          {loading ? '발송 중…' : sent ? '링크 다시 보내기' : '재설정 링크 보내기'}
        </button>
        {sent && (
          <p className="reset-notice">재설정 링크를 발송했어요. 메일함과 스팸함을 확인해주세요.</p>
        )}
      </form>
    </ResetLayout>
  );
}

export default function PasswordResetPage() {
  const result = useLoaderData() as TokenResult;
  return <NewPasswordForm key={`${result.token}:${result.status}`} result={result} />;
}

function NewPasswordForm({ result }: { result: TokenResult }) {
  const navigate = useNavigate();
  const showToast = useToast();
  const [status, setStatus] = useState(result.status);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [visible, setVisible] = useState(false);
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);
  const pending = useRef(false);
  const passwordError = submitted && !getPasswordRules(password).valid;
  const confirmationError = submitted && (!confirmation || password !== confirmation);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    setSubmitted(true);
    if (!getPasswordRules(password).valid || password !== confirmation) return;
    pending.current = true;
    setLoading(true);
    try {
      await resetPassword(result.token, password);
      showToast('비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요');
      navigate('/login', { replace: true });
    } catch (error) {
      const nextStatus = getTokenError(error);
      if (nextStatus === 'error') showToast(toastMessages.networkError);
      else setStatus(nextStatus);
    } finally {
      pending.current = false;
      setLoading(false);
    }
  }

  if (status !== 'valid') {
    const isNetworkError = status === 'error';
    return (
      <ResetLayout
        title={isNetworkError ? '연결을 확인해주세요' : '재설정 링크를 사용할 수 없어요'}
        description={
          isNetworkError
            ? toastMessages.networkError
            : status === 'expired'
              ? '재설정 링크가 만료됐어요. 새 링크를 요청해주세요.'
              : '유효하지 않거나 이미 사용한 링크예요. 새 링크를 요청해주세요.'
        }
      >
        {isNetworkError ? (
          <button
            type="button"
            className="ui-button ui-button--primary submit-button"
            onClick={() => navigate(0)}
          >
            다시 시도하기
          </button>
        ) : (
          <Link
            className="ui-button ui-button--primary submit-button reset-link"
            to="/forgot-password"
          >
            새 재설정 링크 받기
          </Link>
        )}
      </ResetLayout>
    );
  }

  return (
    <ResetLayout title="새 비밀번호 설정" description="새롭게 사용할 비밀번호를 입력해주세요.">
      <form className="auth-form reset-form" onSubmit={handleSubmit} noValidate aria-busy={loading}>
        <div className="field-group">
          <label className="reset-label" htmlFor="reset-password">
            새 비밀번호
          </label>
          <div className="password-input-wrapper">
            <input
              className="ui-input"
              id="reset-password"
              type={visible ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="새 비밀번호"
              value={password}
              disabled={loading}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={passwordError}
              aria-describedby={`reset-rules${passwordError ? ' reset-password-error' : ''}`}
            />
            <button
              type="button"
              className="ui-interactive password-toggle"
              aria-label={visible ? '새 비밀번호 숨기기' : '새 비밀번호 표시'}
              onClick={() => setVisible(!visible)}
            >
              {visible ? '숨김' : '표시'}
            </button>
          </div>
          {passwordError && (
            <span className="field-message" id="reset-password-error" role="alert">
              8자 이상, 영문과 숫자를 포함해주세요
            </span>
          )}
        </div>
        <PasswordRules password={password} id="reset-rules" />
        <div className="field-group">
          <label className="reset-label" htmlFor="reset-confirmation">
            새 비밀번호 확인
          </label>
          <div className="password-input-wrapper">
            <input
              className="ui-input"
              id="reset-confirmation"
              type={confirmVisible ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="새 비밀번호 확인"
              value={confirmation}
              disabled={loading}
              onChange={(event) => setConfirmation(event.target.value)}
              aria-invalid={confirmationError}
              aria-describedby={confirmationError ? 'reset-confirmation-error' : undefined}
            />
            <button
              type="button"
              className="ui-interactive password-toggle"
              aria-label={confirmVisible ? '새 비밀번호 확인 숨기기' : '새 비밀번호 확인 표시'}
              onClick={() => setConfirmVisible(!confirmVisible)}
            >
              {confirmVisible ? '숨김' : '표시'}
            </button>
          </div>
          {confirmationError && (
            <span className="field-message" id="reset-confirmation-error" role="alert">
              비밀번호가 일치하지 않아요
            </span>
          )}
        </div>
        <button
          type="submit"
          className="ui-button ui-button--primary submit-button"
          disabled={loading}
        >
          {loading ? '변경 중…' : '비밀번호 변경하기'}
        </button>
      </form>
    </ResetLayout>
  );
}
