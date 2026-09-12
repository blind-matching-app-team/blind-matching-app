import { useNavigate } from 'react-router-dom';
import { clearAuthSession } from '../lib/auth';

export type SidebarItemId = 'matching' | 'chat' | 'alerts' | 'mypage';

export type SidebarNavProps = {
  activeItem: SidebarItemId;
  userName?: string;
  userId?: number | null;
  unreadTotal?: number;
};

const navItems: Array<{ id: SidebarItemId; label: string; badge: number }> = [
  { id: 'matching', label: '매칭', badge: 0 },
  { id: 'chat', label: '채팅목록', badge: 0 },
  { id: 'alerts', label: '알림', badge: 0 },
  { id: 'mypage', label: '마이페이지', badge: 0 },
];

export default function SidebarNav({
  activeItem,
  userName = '정연',
  userId = 1,
  unreadTotal = 0,
}: SidebarNavProps) {
  const navigate = useNavigate();

  const handleNavClick = (itemId: SidebarItemId) => {
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

  return (
    <aside className="hub-sidebar">
      <div className="sidebar-brand">
        <div className="brand-mark">B</div>
        <span>Blind Matching</span>
      </div>

      <nav className="sidebar-nav" aria-label="메인 네비게이션">
        {mappedItems.map((item) => (
          <button
            key={item.id}
            type="button"
            className={item.id === activeItem ? 'nav-item active' : 'nav-item'}
            onClick={() => handleNavClick(item.id)}
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

      <button type="button" className="sidebar-logout" onClick={handleLogout}>
        로그아웃
      </button>
    </aside>
  );
}
