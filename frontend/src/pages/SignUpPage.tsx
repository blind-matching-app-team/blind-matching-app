export default function SignUpPage() {
  return (
    <div className="login-page">
      <div className="login-panel">
        <div className="brand-row">
          <div className="brand-mark">B</div>
          <span className="brand-name">BLIND MATCHING</span>
        </div>

        <div className="login-header">
          <h1>회원가입</h1>
          <p>새 계정을 만들고 매칭을 시작해보세요.</p>
        </div>

        <form className="login-form">
          <label className="field-group">
            <span>이름</span>
            <input type="text" placeholder="이름을 입력해주세요" />
          </label>

          <label className="field-group">
            <span>이메일</span>
            <input type="email" placeholder="your@email.com" />
          </label>

          <label className="field-group">
            <span>비밀번호</span>
            <input type="password" placeholder="8자 이상 입력해주세요" />
          </label>

          <label className="field-group">
            <span>비밀번호 확인</span>
            <input type="password" placeholder="비밀번호를 다시 입력해주세요" />
          </label>

          <button type="submit" className="primary-button">
            회원가입
          </button>
        </form>

        <div className="divider">
          <span>또는</span>
        </div>

        <button type="button" className="social-button kakao-button">
          카카오로 가입하기
        </button>

        <button type="button" className="social-button apple-button">
          Apple로 가입하기
        </button>

        <p className="sign-up-link">
          이미 계정이 있으신가요?
          <a href="/login"> 로그인</a>
        </p>
      </div>
    </div>
  );
}
