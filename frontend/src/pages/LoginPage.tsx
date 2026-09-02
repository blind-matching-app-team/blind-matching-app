import { type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

export default function LoginPage() {
  const navigate = useNavigate();

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '');

    if (email.includes('suspended')) {
      navigate('/suspended');
      return;
    }

    navigate('/');
  };

  return (
    <div className="login-page">
      <div className="login-panel">
        <div className="brand-row">
          <div className="brand-mark">B</div>
          <span className="brand-name">BLIND MATCHING</span>
        </div>

        <div className="login-header">
          <h1>로그인</h1>
          <p>계속하려면 이메일과 비밀번호를 입력해주세요.</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label className="field-group">
            <span>이메일</span>
            <input name="email" type="email" placeholder="your@email.com" />
          </label>

          <label className="field-group">
            <span>비밀번호</span>
            <input type="password" placeholder="비밀번호를 입력해주세요" />
          </label>

          <div className="form-options">
            <label className="remember-me">
              <input type="checkbox" />
              <span>로그인 상태 유지</span>
            </label>
            <a href="#">비밀번호 찾기</a>
          </div>

          <button type="submit" className="primary-button">
            로그인
          </button>
        </form>

        <div className="divider">
          <span>또는</span>
        </div>

        <button type="button" className="social-button kakao-button">
          카카오로 계속하기
        </button>

        <button type="button" className="social-button apple-button">
          Apple로 계속하기
        </button>

        <p className="sign-up-link">
          계정이 없으신가요?
          <a href="/signup"> 회원가입</a>
        </p>
      </div>
    </div>
  );
}
