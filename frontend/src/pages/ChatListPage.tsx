import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../components/SidebarNav';

type ChatRoom = {
  id: number;
  partnerName: string;
  lastMessage: string;
  lastSeenAt: string;
  unreadCount: number;
  isClosed: boolean;
  revealStage: number;
  isOnline?: boolean;
};

const chatRooms: ChatRoom[] = [
  {
    id: 1,
    partnerName: '김서윤',
    lastMessage: '오늘 저녁에 시간 괜찮으세요?',
    lastSeenAt: '오전 9:48',
    unreadCount: 3,
    isClosed: false,
    revealStage: 2,
    isOnline: true,
  },
  {
    id: 2,
    partnerName: '박지훈',
    lastMessage: '추천 장소를 보냈어요.',
    lastSeenAt: '어제',
    unreadCount: 0,
    isClosed: false,
    revealStage: 4,
    isOnline: false,
  },
  {
    id: 3,
    partnerName: '이도윤',
    lastMessage: '매칭이 종료된 대화입니다.',
    lastSeenAt: '2일 전',
    unreadCount: 0,
    isClosed: true,
    revealStage: 5,
  },
];

export default function ChatListPage() {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState(chatRooms);

  const unreadTotal = useMemo(
    () => rooms.reduce((sum, room) => sum + room.unreadCount, 0),
    [rooms],
  );

  const handleOpenRoom = (roomId: number) => {
    navigate(`/chat/${roomId}`);
  };

  const handleLeaveRoom = (roomId: number) => {
    setRooms((prev) => prev.filter((room) => room.id !== roomId));
  };

  return (
    <div className="hub-shell chat-shell">
      <SidebarNav activeItem="chat" unreadTotal={unreadTotal} />

      <main className="hub-main chat-main">
        <header className="chat-header">
          <h1>채팅목록</h1>
        </header>

        {rooms.length === 0 ? (
          <section className="chat-empty">
            <div className="empty-icon" aria-hidden="true">
              <span>💬</span>
            </div>
            <p className="empty-title">아직 대화 중인 상대가 없어요</p>
            <button type="button" className="primary-button large" onClick={() => navigate('/')}>
              매칭 시작하기
            </button>
          </section>
        ) : (
          <section className="chat-list" aria-label="채팅방 목록">
            {rooms.map((room) => (
              <article key={room.id} className="chat-room-item">
                <button
                  type="button"
                  className="chat-room-main"
                  onClick={() => handleOpenRoom(room.id)}
                >
                  <div className="chat-avatar" aria-hidden="true">
                    <span>{room.partnerName.charAt(0)}</span>
                    {room.isOnline && <em className="online-dot" />}
                  </div>

                  <div className="chat-body">
                    <div className="chat-row">
                      <strong>{room.partnerName}</strong>
                      {room.isClosed && <span className="closed-badge">종료됨</span>}
                    </div>

                    <div className="chat-preview-row">
                      <span className="message-mask">{room.isClosed ? '???' : '???'}</span>
                      <span className="message-preview">{room.lastMessage}</span>
                    </div>
                    <div className="chat-meta-row">
                      <span>{room.lastSeenAt}</span>
                      {room.unreadCount > 0 && (
                        <span className="room-unread">{room.unreadCount}</span>
                      )}
                    </div>
                  </div>
                </button>

                <button
                  type="button"
                  className="leave-room-button"
                  onClick={() => handleLeaveRoom(room.id)}
                  aria-label={`${room.partnerName} 나가기`}
                >
                  나가기
                </button>
              </article>
            ))}
          </section>
        )}
      </main>
    </div>
  );
}
