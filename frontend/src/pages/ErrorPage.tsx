import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { hasActiveSession } from '../lib/auth';
import './ErrorPage.css';

const errorContent = {
  notFound: {
    title: '페이지를 찾을 수 없어요',
    description: '주소가 바뀌었거나 잘못 입력됐을 수 있어요',
  },
  linkExpired: {
    title: '링크가 만료됐어요',
    description: '비밀번호 재설정 링크는 30분 동안만 유효해요. 다시 시도해주세요',
  },
};

type ErrorPageProps = {
  variant?: keyof typeof errorContent;
  title?: string;
  description?: string;
  icon?: ReactNode;
  children?: ReactNode;
};

export default function ErrorPage({
  variant = 'notFound',
  title,
  description,
  icon,
  children,
}: ErrorPageProps) {
  const navigate = useNavigate();
  const content = errorContent[variant];
  return (
    <main className="auth-page-shell error-page">
      <section className="ui-enter error-card" aria-labelledby="error-title">
        <div className={`error-icon error-icon--${variant}`} aria-hidden="true">
          {icon ?? (
            <svg
              width="32"
              height="32"
              viewBox="0 0 32 32"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              {variant === 'linkExpired' ? (
                <>
                  <path d="M9 4h14M9 28h14M10 4v5c0 3 6 7 6 7s6-4 6-7V4M10 28v-5c0-3 6-7 6-7s6 4 6 7v5" />
                  <path d="M13 9h6M12 25h8" />
                </>
              ) : (
                <>
                  <path d="M18 4H8v24h16V10l-6-6Z M18 4v6h6" />
                  <path d="M16 15v5M16 24h.01" />
                </>
              )}
            </svg>
          )}
        </div>
        <h1 id="error-title">{title ?? content.title}</h1>
        <p className="error-description">{description ?? content.description}</p>
        <div className="error-actions">
          <button
            type="button"
            className="ui-button ui-button--primary error-home-button"
            onClick={() => navigate(hasActiveSession() ? '/' : '/login', { replace: true })}
          >
            홈으로 돌아가기
          </button>
          {children}
        </div>
      </section>
    </main>
  );
}
