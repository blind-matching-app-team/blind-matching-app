import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../components/SidebarNav';
import { leaveRoom, useChatStore } from '../lib/chatStore';
import ConfirmModal from '../components/ConfirmModal';
import { confirmationContent } from '../components/feedbackContent';
import { chatAction } from '../api/chat';
import { useToast } from '../components/useToast';

export default function ChatListPage() {
  const navigate = useNavigate();
  const { rooms } = useChatStore();
  const [leaving, setLeaving] = useState<number | null>(null);
  const toast = useToast();
  const handleOpenRoom = (roomId: number) => {
    navigate(`/chat/${roomId}`);
  };

  const handleLeaveRoom = (roomId: number) => {
    setLeaving(roomId);
  };

  return (
    <div className="hub-shell chat-shell">
      <SidebarNav activeItem="chat" />

      <main className="ui-enter hub-main chat-main">
        <header className="chat-header">
          <h1>채팅목록</h1>
        </header>

        {rooms.length === 0 ? (
          <section className="ui-empty chat-empty">
            <div className="empty-icon" aria-hidden="true">
              <span>💬</span>
            </div>
            <p className="empty-title">아직 대화 중인 상대가 없어요</p>
            <button
              type="button"
              className="ui-button ui-button--primary primary-button large"
              onClick={() => navigate('/')}
            >
              매칭 시작하기
            </button>
          </section>
        ) : (
          <section className="chat-list" aria-label="채팅방 목록">
            {rooms.map((room) => (
              <article key={room.id} className="chat-room-item">
                <button
                  type="button"
                  className="ui-interactive chat-room-main"
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
                  className="ui-interactive leave-room-button"
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
      <ConfirmModal
        {...confirmationContent.leaveChat}
        open={leaving !== null}
        onClose={() => setLeaving(null)}
        onConfirm={() => {
          if (leaving === null) return;
          const roomId = leaving;
          void chatAction(roomId, 'leave')
            .then(() => leaveRoom(roomId))
            .catch((error: Error) => toast(error.message));
        }}
      />
    </div>
  );
}
