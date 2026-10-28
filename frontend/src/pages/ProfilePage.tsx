import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../components/SidebarNav';
import { clearAuthSession, getUserId } from '../lib/auth';
import { getMyAccount, getMyPhoto, type MyAccount } from '../api/profile';
import { readPhotoVerification } from '../api/photoVerification';

import ConfirmModal from '../components/ConfirmModal';
import { confirmationContent, toastMessages } from '../components/feedbackContent';
import { useToast } from '../components/useToast';

type MenuItem = {
  id: string;
  label: string;
  kind: 'default' | 'highlight';
};

const menuItems: MenuItem[] = [
  { id: 'profile', label: '프로필 수정', kind: 'default' },
  { id: 'preferences', label: '매칭 선호조건 수정', kind: 'default' },
  { id: 'password', label: '비밀번호 변경', kind: 'default' },
  { id: 'verification', label: '사진인증 받기', kind: 'default' },
  { id: 'payment', label: '이용권 구매', kind: 'highlight' },
  { id: 'history', label: '결제 내역', kind: 'default' },
  { id: 'blocked', label: '차단 목록', kind: 'default' },
  { id: 'alerts', label: '알림 설정', kind: 'default' },
];

export default function ProfilePage() {
  const navigate = useNavigate();
  const userId = getUserId();
  const showToast = useToast();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [summary, setSummary] = useState<{
    userId: number | null;
    account: MyAccount | null;
    photo: string | null;
    error: string;
  } | null>(null);
  const [photoFailed, setPhotoFailed] = useState(false);
  const [photoVerified, setPhotoVerified] = useState(false);
  const currentSummary = summary?.userId === userId ? summary : null;

  useEffect(() => {
    const controller = new AbortController();
    readPhotoVerification(controller.signal)
      .then((status) => setPhotoVerified(status.verified))
      .catch(() => {
        /* Unknown status keeps the S8-17 entry available; S16 rechecks it. */
      });
    return () => controller.abort();
  }, [userId]);

  useEffect(() => {
    const controller = new AbortController();
    let photoUrl: string | null = null;
    async function loadSummary() {
      const [account, photo] = await Promise.allSettled([
        getMyAccount(),
        getMyPhoto(controller.signal),
      ]);
      if (controller.signal.aborted) return;
      if (photo.status === 'fulfilled' && photo.value) {
        photoUrl = URL.createObjectURL(photo.value);
      }
      setPhotoFailed(false);
      setSummary({
        userId,
        account: account.status === 'fulfilled' ? account.value : null,
        photo: photoUrl,
        error:
          account.status === 'rejected'
            ? '내 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.'
            : photo.status === 'rejected'
              ? '프로필 사진을 불러오지 못했어요.'
              : '',
      });
    }
    void loadSummary();
    return () => {
      controller.abort();
      if (photoUrl) URL.revokeObjectURL(photoUrl);
    };
  }, [userId]);

  const handleMenuClick = (id: string) => {
    if (id === 'profile') {
      navigate('/profile/edit', { state: { from: '/profile' } });
      return;
    }

    if (id === 'preferences') {
      navigate('/profile/preferences', { state: { from: '/profile' } });
      return;
    }

    if (id === 'password') {
      showToast('비밀번호 변경 모달을 열어주세요');
      return;
    }

    if (id === 'verification') {
      if (!photoVerified) navigate('/profile/photo-verification');
      return;
    }

    if (id === 'payment') {
      showToast('이용권 구매 모달을 열어주세요');
      return;
    }

    if (id === 'history' || id === 'blocked' || id === 'alerts') {
      showToast(toastMessages.comingSoon);
    }
  };

  const handleLogout = () => {
    clearAuthSession();
    navigate('/login', { replace: true });
  };

  const handleDeleteAccount = () => {
    clearAuthSession();
    navigate('/login', { replace: true });
  };

  return (
    <div className="hub-shell chat-shell">
      <SidebarNav activeItem="mypage" userId={userId ?? 1} />

      <main className="ui-enter hub-main profile-main">
        <header className="notification-header">
          <h1>마이페이지</h1>
        </header>

        <section
          className="profile-summary-card"
          aria-label="내 프로필"
          aria-busy={!currentSummary}
        >
          <div className="profile-avatar">
            {currentSummary?.photo && !photoFailed ? (
              <img
                src={currentSummary.photo}
                alt="내 프로필 사진"
                onError={() => setPhotoFailed(true)}
              />
            ) : (
              <span role="img" aria-label="기본 프로필 이미지">
                {Array.from(currentSummary?.account?.nickname ?? '')[0] || '•'}
              </span>
            )}
          </div>
          <div className="profile-summary-text">
            {!currentSummary ? (
              <span role="status">내 정보를 불러오는 중…</span>
            ) : (
              <>
                {currentSummary.account && (
                  <>
                    <strong>{currentSummary.account.nickname || '닉네임 미등록'}</strong>
                    <span>{currentSummary.account.email || '이메일 미등록'}</span>
                  </>
                )}
                {photoVerified && <span className="profile-verified-badge">✓ 사진인증됨</span>}
                {(currentSummary.error || photoFailed) && (
                  <span role="alert">
                    {currentSummary.error || '프로필 사진을 표시하지 못했어요.'}
                  </span>
                )}
              </>
            )}
          </div>
        </section>

        <div className="profile-menu-list">
          {menuItems.map((item) =>
            item.id === 'verification' && photoVerified ? (
              // S8-17: a verified user sees the status instead of re-entering S16.
              <div key={item.id} className="profile-menu-item profile-menu-item--done">
                <span>사진인증</span>
                <span className="menu-status">인증완료</span>
              </div>
            ) : (
              <button
                key={item.id}
                type="button"
                className={
                  item.kind === 'highlight'
                    ? 'ui-interactive profile-menu-item highlight'
                    : 'ui-interactive profile-menu-item'
                }
                onClick={() => handleMenuClick(item.id)}
              >
                <span>{item.label}</span>
                <span className="menu-arrow">›</span>
              </button>
            ),
          )}
        </div>

        <div className="profile-actions">
          <button
            type="button"
            className="ui-interactive profile-logout-button"
            onClick={() => setShowLogoutConfirm(true)}
          >
            로그아웃
          </button>
          <button
            type="button"
            className="ui-interactive profile-delete-link"
            onClick={() => setShowDeleteConfirm(true)}
          >
            회원 탈퇴
          </button>
        </div>
      </main>

      <ConfirmModal
        {...confirmationContent.logout}
        open={showLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
        onConfirm={handleLogout}
      />
      <ConfirmModal
        {...confirmationContent.deleteAccount}
        open={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        onConfirm={handleDeleteAccount}
      />
    </div>
  );
}
