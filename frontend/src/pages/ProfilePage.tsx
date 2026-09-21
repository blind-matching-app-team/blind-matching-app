import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../components/SidebarNav';
import { clearAuthSession, getUserId } from '../lib/auth';

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

  const handleMenuClick = (id: string) => {
    if (id === 'profile') {
      navigate('/profile/edit');
      return;
    }

    if (id === 'preferences') {
      navigate('/profile/preferences');
      return;
    }

    if (id === 'password') {
      showToast('비밀번호 변경 모달을 열어주세요');
      return;
    }

    if (id === 'verification') {
      showToast('사진인증 페이지로 이동합니다');
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

        <section className="profile-summary-card">
          <div className="profile-avatar">J</div>
          <div className="profile-summary-text">
            <strong>정연</strong>
            <span>jyeon@email.com</span>
          </div>
        </section>

        <div className="profile-menu-list">
          {menuItems.map((item) => (
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
          ))}
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
