import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../components/SidebarNav';

type NotificationType = 'verification' | 'next-step' | 'warning' | 'match-ended';

type NotificationItem = {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  time: string;
  unread: boolean;
};

const initialNotifications: NotificationItem[] = [
  {
    id: 1,
    type: 'verification',
    title: '미인증 상대 알림',
    message: '상대방은 아직 사진 인증을 완료하지 않았어요',
    time: '2시간 전',
    unread: true,
  },
  {
    id: 2,
    type: 'next-step',
    title: '다음 단계 요청 도착',
    message: '???님이 다음 단계를 요청했어요',
    time: '5시간 전',
    unread: true,
  },
  {
    id: 3,
    type: 'warning',
    title: '경고 조치 알림',
    message: '안전한 이용을 위해 경고 조치가 적용됐어요. 반복 시 이용이 제한될 수 있어요',
    time: '1일 전',
    unread: false,
  },
  {
    id: 4,
    type: 'match-ended',
    title: '매칭 종료 알림',
    message: '매칭이 종료됐어요',
    time: '2일 전',
    unread: false,
  },
];

const typeMeta: Record<NotificationType, { iconBg: string; icon: string; route: string }> = {
  verification: { iconBg: '#FCEBEB', icon: '⚠', route: '/' },
  'next-step': { iconBg: '#FBEAF0', icon: '✦', route: '/' },
  warning: { iconBg: '#FCEBEB', icon: '!', route: '/suspended' },
  'match-ended': { iconBg: '#F1EFE8', icon: '✓', route: '/' },
};

export default function NotificationPage() {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState(initialNotifications);

  const unreadTotal = useMemo(
    () => notifications.filter((item) => item.unread).length,
    [notifications],
  );

  const markAllRead = () => {
    setNotifications((prev) => prev.map((item) => ({ ...item, unread: false })));
  };

  const handleNotificationClick = (item: NotificationItem) => {
    if (item.type === 'verification') {
      navigate('/');
      return;
    }

    if (item.type === 'next-step') {
      navigate('/');
      return;
    }

    if (item.type === 'warning') {
      navigate('/suspended');
      return;
    }

    navigate('/');
  };

  const handleEmptyAction = () => {
    navigate('/');
  };

  return (
    <div className="hub-shell chat-shell">
      <SidebarNav activeItem="alerts" unreadTotal={unreadTotal} />

      <main className="hub-main notification-main">
        <header className="notification-header">
          <h1>알림</h1>
          <button type="button" className="link-button" onClick={markAllRead}>
            전체 읽음
          </button>
        </header>

        {notifications.length === 0 ? (
          <section className="notification-empty">
            <div className="empty-icon" aria-hidden="true">
              <span>🔔</span>
            </div>
            <p className="empty-title">아직 알림이 없어요</p>
            <button type="button" className="primary-button large" onClick={handleEmptyAction}>
              매칭 시작하기
            </button>
          </section>
        ) : (
          <section className="notification-list" aria-label="알림 목록">
            {notifications.map((item) => {
              const meta = typeMeta[item.type];

              return (
                <button
                  key={item.id}
                  type="button"
                  className="notification-item"
                  onClick={() => handleNotificationClick(item)}
                >
                  <span className="notification-icon" style={{ background: meta.iconBg }}>
                    {meta.icon}
                  </span>

                  <span className="notification-content">
                    <span className="notification-title">{item.title}</span>
                    <span className="notification-message">{item.message}</span>
                    <span className="notification-time">{item.time}</span>
                  </span>

                  {item.unread && <span className="notification-dot" aria-label="읽지 않은 알림" />}
                </button>
              );
            })}
          </section>
        )}
      </main>
    </div>
  );
}
