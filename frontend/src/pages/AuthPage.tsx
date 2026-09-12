import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { loginApi, signupApi } from '../api/auth';

type AuthMode = 'login' | 'signup';

type ProviderName = '카카오' | '네이버' | '구글';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function AuthPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const mode: AuthMode = location.pathname === '/signup' ? 'signup' : 'login';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [confirmError, setConfirmError] = useState('');
  const [termsError, setTermsError] = useState('');
  const [bannerError, setBannerError] = useState('');
  const [toastMessage, setToastMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);

  const passwordRules = useMemo(() => {
    const minLength = password.length >= 8;
    const hasLetter = /[A-Za-z]/.test(password);
    const hasNumber = /\d/.test(password);

    return {
      minLength,
      hasLetter,
      hasNumber,
      valid: minLength && hasLetter && hasNumber,
    };
  }, [password]);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }

    const timer = window.setTimeout(() => setToastMessage(''), 2500);
    return () => window.clearTimeout(timer);
  }, [toastMessage]);

  const handleModeChange = (nextMode: AuthMode) => {
    navigate(nextMode === 'login' ? '/login' : '/signup');
  };

  const validateEmail = (value: string) => {
    if (!value.trim()) {
      setEmailError('이메일 형식을 확인해주세요');
      return false;
    }

    if (!EMAIL_REGEX.test(value)) {
      setEmailError('이메일 형식을 확인해주세요');
      return false;
    }

    setEmailError('');
    return true;
  };

  const validatePassword = () => {
    if (!password) {
      return false;
    }

    return passwordRules.valid;
  };

  const validateConfirmPassword = () => {
    if (mode !== 'signup') {
      setConfirmError('');
      return true;
    }

    if (!confirmPassword) {
      setConfirmError('비밀번호가 일치하지 않아요');
      return false;
    }

    if (confirmPassword !== password) {
      setConfirmError('비밀번호가 일치하지 않아요');
      return false;
    }

    setConfirmError('');
    return true;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBannerError('');

    const emailValid = validateEmail(email);
    const passwordValid = validatePassword();
    const confirmValid = validateConfirmPassword();
    const termsValid = mode === 'signup' ? acceptedTerms : true;

    if (mode === 'signup' && !termsValid) {
      setTermsError('약관에 동의해주세요');
    } else {
      setTermsError('');
    }

    if (!emailValid || !passwordValid || !confirmValid || !termsValid) {
      return;
    }

    setLoading(true);

    try {
      if (mode === 'login') {
        const response = await loginApi(email, password);

        if (!response.success) {
          const detail = response.data as {
            restrictionType?: 'TEMPORARY' | 'PERMANENT';
            reason?: string;
            restrictedUntil?: string | null;
          };

          if (response.code === 'ACCOUNT_SUSPENDED') {
            const params = new URLSearchParams({
              type: detail.restrictionType ?? 'PERMANENT',
              reason: detail.reason ?? '관리자 조치에 따라 계정 이용이 제한되었습니다.',
            });

            if (detail.restrictedUntil) {
              params.set('restrictedUntil', detail.restrictedUntil);
            }

            navigate(`/suspended?${params.toString()}`);
            return;
          }

          if (response.code === 'EMAIL_DUPLICATE') {
            const provider =
              typeof response.data === 'object' && response.data && 'provider' in response.data
                ? String(response.data.provider)
                : '';
            const normalized = provider.toUpperCase();
            const providerName =
              normalized === 'KAKAO'
                ? '카카오'
                : normalized === 'NAVER'
                  ? '네이버'
                  : normalized === 'GOOGLE'
                    ? '구글'
                    : '소셜';
            setBannerError(
              `${providerName}로 가입된 이메일이에요. ${providerName}로 로그인해주세요`,
            );
            return;
          }

          setBannerError(response.message || '로그인에 실패했습니다.');
          return;
        }

        const { userId } = response.data;
        if (!userId) {
          setBannerError('로그인 응답이 올바르지 않습니다.');
          return;
        }

        navigate('/');
        return;
      }

      const response = await signupApi(email, password);

      if (!response.success) {
        if (response.code === 'EMAIL_DUPLICATE') {
          const provider =
            typeof response.data === 'object' && response.data && 'provider' in response.data
              ? String(response.data.provider)
              : '';
          const normalized = provider.toUpperCase();
          const providerName =
            normalized === 'KAKAO'
              ? '카카오'
              : normalized === 'NAVER'
                ? '네이버'
                : normalized === 'GOOGLE'
                  ? '구글'
                  : '소셜';
          setBannerError(`${providerName}로 가입된 이메일이에요. ${providerName}로 로그인해주세요`);
          return;
        }

        setBannerError(response.message || '회원가입에 실패했습니다.');
        return;
      }

      navigate('/login');
    } catch (error) {
      const message = error instanceof Error ? error.message : '처리 중 오류가 발생했습니다.';
      setBannerError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleSocialClick = (provider: ProviderName) => {
    setToastMessage('로그인이 취소됐어요');
    console.log(`${provider} OAuth click`);
  };

  const handleDevBypassLogin = () => {
    sessionStorage.setItem('bma_access_token', 'dev-test-access-token');
    sessionStorage.setItem('bma_refresh_token', 'dev-test-refresh-token');
    sessionStorage.setItem('bma_user_id', '1');
    navigate('/', { replace: true });
  };

  const handleTermsClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    setToastMessage('준비 중입니다');
  };

  const passwordLineClass = mode === 'signup' ? 'auth-form signup-form' : 'auth-form login-form';

  return (
    <div className="auth-page-shell">
      <div className="auth-card">
        <div className="logo-badge" aria-label="Blind Matching logo">
          <span>B</span>
        </div>

        <div className="auth-tab-container" role="tablist" aria-label="인증 방식 선택">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            className={mode === 'login' ? 'auth-tab active' : 'auth-tab'}
            onClick={() => handleModeChange('login')}
          >
            로그인
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'signup'}
            className={mode === 'signup' ? 'auth-tab active' : 'auth-tab'}
            onClick={() => handleModeChange('signup')}
          >
            회원가입
          </button>
        </div>

        {bannerError && <div className="auth-banner">{bannerError}</div>}

        <form className={passwordLineClass} onSubmit={handleSubmit} noValidate>
          <label className={`field-group ${emailError ? 'has-error' : ''}`}>
            <input
              type="email"
              value={email}
              placeholder="name@example.com"
              onChange={(event) => {
                setEmail(event.target.value);
                if (emailError) validateEmail(event.target.value);
              }}
              onBlur={() => validateEmail(email)}
              aria-invalid={Boolean(emailError)}
            />
            {emailError && <span className="field-message">{emailError}</span>}
          </label>

          <div className="password-field-wrap">
            <label className="field-group">
              <div className="password-input-wrapper">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  placeholder="비밀번호"
                  onChange={(event) => setPassword(event.target.value)}
                  onBlur={() => {
                    if (mode === 'signup' && password && !passwordRules.valid) {
                      setBannerError('');
                    }
                  }}
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                >
                  {showPassword ? '숨김' : '표시'}
                </button>
              </div>
            </label>
          </div>

          {mode === 'signup' && (
            <>
              <div className="password-field-wrap">
                <label className="field-group">
                  <div className="password-input-wrapper">
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      value={confirmPassword}
                      placeholder="비밀번호 확인"
                      onChange={(event) => setConfirmPassword(event.target.value)}
                      onBlur={validateConfirmPassword}
                      aria-invalid={Boolean(confirmError)}
                    />
                    <button
                      type="button"
                      className="password-toggle"
                      onClick={() => setShowConfirmPassword((prev) => !prev)}
                      aria-label={
                        showConfirmPassword ? '비밀번호 확인 숨기기' : '비밀번호 확인 표시'
                      }
                    >
                      {showConfirmPassword ? '숨김' : '표시'}
                    </button>
                  </div>
                  {confirmError && <span className="field-message error">{confirmError}</span>}
                </label>
              </div>

              <div className="rule-box">
                <div className={`rule-item ${passwordRules.minLength ? 'done' : ''}`}>
                  <span className="rule-bullet">{passwordRules.minLength ? '✓' : '·'}</span>
                  <span>8자 이상</span>
                </div>
                <div className={`rule-item ${passwordRules.hasLetter ? 'done' : ''}`}>
                  <span className="rule-bullet">{passwordRules.hasLetter ? '✓' : '·'}</span>
                  <span>영문 포함</span>
                </div>
                <div className={`rule-item ${passwordRules.hasNumber ? 'done' : ''}`}>
                  <span className="rule-bullet">{passwordRules.hasNumber ? '✓' : '·'}</span>
                  <span>숫자 포함</span>
                </div>
              </div>
            </>
          )}

          {mode === 'signup' && (
            <label className={`terms-row ${termsError ? 'has-error' : ''}`}>
              <input
                type="checkbox"
                checked={acceptedTerms}
                onChange={(event) => {
                  setAcceptedTerms(event.target.checked);
                  if (event.target.checked) setTermsError('');
                }}
              />
              <span>
                이용약관 및{' '}
                <a href="#" onClick={handleTermsClick}>
                  개인정보처리방침
                </a>
                에 동의합니다
              </span>
            </label>
          )}

          {termsError && <span className="field-message error terms-message">{termsError}</span>}

          <button type="submit" className="submit-button" disabled={loading}>
            {loading ? (
              <span className="spinner" aria-label="로딩 중" />
            ) : mode === 'login' ? (
              '로그인'
            ) : (
              '회원가입'
            )}
          </button>
        </form>

        <div className="divider-wrap" aria-hidden="true">
          <span className="divider-line" />
          <span className="divider-text">또는</span>
          <span className="divider-line" />
        </div>

        <div className="social-stack">
          <button
            type="button"
            className="social-button kakao"
            onClick={() => handleSocialClick('카카오')}
          >
            카카오로 계속하기
          </button>
          <button
            type="button"
            className="social-button naver"
            onClick={() => handleSocialClick('네이버')}
          >
            네이버로 계속하기
          </button>
          <button
            type="button"
            className="social-button google"
            onClick={() => handleSocialClick('구글')}
          >
            구글로 계속하기
          </button>
        </div>

        {toastMessage && <div className="toast">{toastMessage}</div>}

        <div className="auth-footer">
          {mode === 'login' ? '계정이 없으신가요?' : '이미 계정이 있으신가요?'}
          <button
            type="button"
            className="text-link"
            onClick={() => handleModeChange(mode === 'login' ? 'signup' : 'login')}
          >
            {mode === 'login' ? '회원가입' : '로그인'}
          </button>
        </div>

        {import.meta.env.DEV && (
          <button type="button" className="dev-login-button" onClick={handleDevBypassLogin}>
            개발용 임시 로그인
          </button>
        )}
      </div>
    </div>
  );
}
