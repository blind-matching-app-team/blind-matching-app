import { Link, useSearchParams } from 'react-router-dom';

export default function SuspendedPage() {
  const [searchParams] = useSearchParams();
  const restrictionType = searchParams.get('type') === 'TEMPORARY' ? 'TEMPORARY' : 'PERMANENT';
  const reason = searchParams.get('reason') ?? '관리자 조치에 따라 계정 이용이 제한되었습니다.';
  const restrictedUntil = searchParams.get('restrictedUntil');

  const isTemporary = restrictionType === 'TEMPORARY';
  const formattedDate = restrictedUntil
    ? new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: 'numeric',
        day: 'numeric',
      }).format(new Date(restrictedUntil))
    : '';

  const title = isTemporary ? '이용이 일시 제한된 계정이에요' : '이용이 영구 정지된 계정이에요';
  const icon = isTemporary ? '⏳' : '🔒';

  return (
    <div className="auth-page-shell">
      <div className="auth-card suspended-card">
        <div className="suspended-icon" aria-label="이용 제한 상태 아이콘">
          {icon}
        </div>

        <h1 className="suspended-title">{title}</h1>
        <p className="suspended-subtitle">사유: {reason}</p>

        {isTemporary && formattedDate && (
          <p className="suspended-note">{formattedDate} 이후 다시 이용할 수 있어요</p>
        )}

        <p className="suspended-contact">문의가 필요하면 고객센터로 연락해주세요</p>

        <div className="suspended-actions">
          <Link to="/login" className="submit-button is-link">
            확인
          </Link>
        </div>
      </div>
    </div>
  );
}
