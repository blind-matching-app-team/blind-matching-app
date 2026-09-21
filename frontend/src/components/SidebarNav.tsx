import { useNavigate } from 'react-router-dom';
import { useEffect, useId, useRef, useState } from 'react';
import ConfirmModal from './ConfirmModal';
import { confirmationContent } from './feedbackContent';
import { clearAuthSession } from '../lib/auth';
import './SidebarNav.css';
import { useChatStore } from '../lib/chatStore';

export type SidebarItemId = 'matching' | 'chat' | 'alerts' | 'mypage';

export type SidebarNavProps = {
  activeItem: SidebarItemId;
  userName?: string;
  userId?: number | null;
};

const navItems: Array<{ id: SidebarItemId; label: string; badge: number }> = [
  { id: 'matching', label: '매칭', badge: 0 },
  { id: 'chat', label: '채팅목록', badge: 0 },
  { id: 'alerts', label: '알림', badge: 0 },
  { id: 'mypage', label: '마이페이지', badge: 0 },
];

export default function SidebarNav({ activeItem, userName = '정연', userId = 1 }: SidebarNavProps) {
  const navigate = useNavigate();
  const { rooms } = useChatStore();
  const unreadTotal = rooms.reduce((sum, room) => sum + room.unreadCount, 0);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const drawerRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const drawerId = useId();

  useEffect(() => {
    const media = window.matchMedia('(max-width: 860px)');
    const closeOnDesktop = () => {
      if (!media.matches) setMenuOpen(false);
    };
    media.addEventListener('change', closeOnDesktop);
    return () => media.removeEventListener('change', closeOnDesktop);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const drawer = drawerRef.current!;
    const trigger = triggerRef.current;
    const previousOverflow = document.body.style.overflow;
    drawer.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      drawer.close();
      document.body.style.overflow = previousOverflow;
      if (trigger?.getClientRects().length) trigger.focus();
    };
  }, [menuOpen]);

  const handleNavClick = (itemId: SidebarItemId) => {
    setMenuOpen(false);
    if (itemId === 'matching') {
      navigate('/');
      return;
    }

    if (itemId === 'chat') {
      navigate('/chat');
      return;
    }

    if (itemId === 'alerts') {
      navigate('/notifications');
      return;
    }

    if (itemId === 'mypage') {
      navigate('/profile');
      return;
    }
  };

  const handleLogout = () => {
    clearAuthSession();
    navigate('/login', { replace: true });
  };

  const mappedItems = navItems.map((item) => ({
    ...item,
    badge: item.id === 'chat' ? unreadTotal : item.badge,
  }));

  const menuContent = (
    <>
      <div className="sidebar-brand">
        <div className="brand-mark">B</div>
        <span>Blind Matching</span>
      </div>

      <nav className="sidebar-nav" aria-label="메인 네비게이션">
        {mappedItems.map((item) => (
          <button
            key={item.id}
            type="button"
            className={
              item.id === activeItem ? 'ui-interactive nav-item active' : 'ui-interactive nav-item'
            }
            onClick={() => handleNavClick(item.id)}
            aria-current={item.id === activeItem ? 'page' : undefined}
          >
            <span>{item.label}</span>
            {item.badge > 0 && <span className="nav-badge">{item.badge}</span>}
          </button>
        ))}
      </nav>

      <div className="sidebar-user">
        <div className="user-avatar">{userName.charAt(0)}</div>
        <div className="user-meta">
          <strong>{userName}</strong>
          <span>사용자 #{userId}</span>
        </div>
      </div>

      <button
        type="button"
        className="ui-interactive sidebar-logout"
        onClick={() => {
          setMenuOpen(false);
          setShowLogoutConfirm(true);
        }}
      >
        로그아웃
      </button>
    </>
  );

  return (
    <>
      <header className="mobile-nav-header">
        <button
          ref={triggerRef}
          type="button"
          className="ui-interactive mobile-nav-button"
          aria-label="메뉴 열기"
          aria-expanded={menuOpen}
          aria-controls={drawerId}
          aria-haspopup="dialog"
          onClick={() => setMenuOpen(true)}
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" />
          </svg>
        </button>
        <span>Blind Matching</span>
      </header>
      <aside className="hub-sidebar desktop-sidebar">{menuContent}</aside>
      <dialog
        ref={drawerRef}
        id={drawerId}
        className="mobile-nav-drawer"
        aria-label="메인 메뉴"
        onCancel={(event) => {
          event.preventDefault();
          setMenuOpen(false);
        }}
        onClick={(event) => {
          if (event.target !== event.currentTarget) return;
          const rect = event.currentTarget.getBoundingClientRect();
          if (
            event.clientX < rect.left ||
            event.clientX > rect.right ||
            event.clientY < rect.top ||
            event.clientY > rect.bottom
          ) {
            setMenuOpen(false);
          }
        }}
      >
        <div className="hub-sidebar mobile-sidebar">
          <button
            type="button"
            className="ui-interactive mobile-nav-button mobile-nav-close"
            aria-label="메뉴 닫기"
            onClick={() => setMenuOpen(false)}
          >
            <span aria-hidden="true">×</span>
          </button>
          {menuOpen && menuContent}
        </div>
      </dialog>
      <ConfirmModal
        {...confirmationContent.logout}
        open={showLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
        onConfirm={handleLogout}
      />
    </>
  );
}
