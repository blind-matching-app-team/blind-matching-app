import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  confirmIdentity,
  readIdentityStatus,
  requestIdentity,
  runIdentitySdk,
} from '../api/identity';
import { clearAuthSession } from '../lib/auth';
import './IdentityVerificationPage.css';

const errorCode = (error: unknown) =>
  error instanceof Error ? (error.cause as { code?: string } | undefined)?.code : undefined;

const ERROR_MESSAGE: Record<string, string> = {
  VERIFY_004: '이미 다른 계정에서 본인인증을 완료한 정보예요. 기존 계정으로 로그인해주세요.',
  VERIFY_005: '인증 요청이 만료됐어요. 다시 시도해주세요.',
  VERIFY_006: '인증 요청이 만료됐어요. 다시 시도해주세요.',
  VERIFY_007: '본인인증에 실패했어요. 다시 시도해주세요.',
};

export default function IdentityVerificationPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const fromProfile = location.state?.from === '/profile';
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [rejected, setRejected] = useState(false);

  // AC: an already verified account never sees S15 again.
  useEffect(() => {
    const controller = new AbortController();
    readIdentityStatus(controller.signal)
      .then((status) => {
        if (status.verified) navigate('/matching/waiting', { replace: true });
      })
      .catch(() => {
        /* The start button still works; the server answers VERIFY_002 if already verified. */
      });
    return () => controller.abort();
  }, [navigate]);

  async function verify() {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const request = await requestIdentity();
      const payload = await runIdentitySdk(request);
      await confirmIdentity(request.transactionId, payload);
      navigate('/matching/waiting', { replace: true });
    } catch (reason) {
      const code = errorCode(reason);
      if (code === 'VERIFY_002') navigate('/matching/waiting', { replace: true });
      else if (code === 'VERIFY_003') {
        // The account is already rolled back server-side, so the tokens are dead.
        clearAuthSession();
        setRejected(true);
      } else if ((reason as { cancelled?: boolean }).cancelled)
        setError('본인인증이 취소됐어요. 매칭을 시작하려면 인증을 완료해주세요.');
      else
        setError(
          (code && ERROR_MESSAGE[code]) ||
            (reason instanceof Error ? reason.message : '다시 시도해주세요.'),
        );
    } finally {
      setBusy(false);
    }
  }

  if (rejected)
    return (
      <main className="identity-shell">
        <section
          className="ui-enter identity-card identity-card--rejected"
          aria-labelledby="identity-rejected-title"
        >
          <div className="identity-content">
            {/* S15-06 */}
            <div className="identity-reject-icon" aria-hidden="true">
              !
            </div>
            {/* S15-07 */}
            <h1 id="identity-rejected-title" className="identity-reject-title">
              만 19세 이상만 가입할 수 있어요
            </h1>
            {/* S15-08 */}
            <p className="identity-description">
              본인인증 결과, 성인 인증이 확인되지 않았어요. 블라인드는 안전을 위해 성인만 이용할 수
              있는 서비스예요
            </p>
          </div>
          {/* S15-09: 계정 롤백은 confirm 시점에 서버가 이미 끝냈으므로 이동만 한다. */}
          <button
            type="button"
            className="ui-button ui-button--secondary identity-button"
            onClick={() => navigate('/login', { replace: true })}
          >
            확인
          </button>
        </section>
      </main>
    );

  return (
    <main className="identity-shell">
      <section className="ui-enter identity-card" aria-labelledby="identity-title">
        <div className="identity-content">
          {/* S15-01 */}
          <div className="logo-badge identity-logo" aria-label="Blind Matching logo">
            <span>B</span>
          </div>
          {/* S15-02 */}
          <h1 id="identity-title" className="identity-title">
            본인인증이 필요해요
          </h1>
          {/* S15-03 */}
          <p className="identity-description">
            안전한 매칭을 위해 실제 본인인지 확인하는 절차예요
            <br />
            (약 30초 소요)
          </p>
          {error && (
            <p role="alert" className="identity-error">
              {error}
            </p>
          )}
        </div>
        {/* S15-04 */}
        <button
          type="button"
          className="ui-button ui-button--primary identity-button"
          disabled={busy}
          onClick={() => void verify()}
        >
          {busy ? '인증 진행 중…' : '통신사 본인인증 시작'}
        </button>
        {/* S15-05 */}
        <p className="identity-footnote">인증 후에만 매칭을 시작할 수 있어요</p>
        <Link className="identity-back" to={fromProfile ? '/profile' : '/profile/preferences'}>
          {fromProfile ? '마이페이지로 돌아가기' : '선호조건으로 돌아가기'}
        </Link>
      </section>
    </main>
  );
}
