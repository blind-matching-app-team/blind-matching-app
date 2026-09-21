import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { chatAction, connectChat, sendMessage } from '../api/chat';
import {
  appendMessage,
  leaveRoom,
  markRoomRead,
  reportTypes,
  revealBlur,
  useChatStore,
} from '../lib/chatStore';
import SidebarNav from '../components/SidebarNav';
import RevealProgress from '../components/RevealProgress';
import ConfirmModal from '../components/ConfirmModal';
import Modal from '../components/Modal';
import { confirmationContent } from '../components/feedbackContent';
import { useToast } from '../components/useToast';
import './ChatPage.css';

export default function ChatPage() {
  const { id } = useParams();
  // A room change remounts the conversation, discarding the previous draft and connection.
  return <Conversation key={id} roomId={Number(id)} />;
}

function Conversation({ roomId }: { roomId: number }) {
  const { rooms } = useChatStore();
  const room = rooms.find((item) => item.id === roomId);
  const roomExists = Boolean(room);
  const navigate = useNavigate();
  const toast = useToast();
  const [draft, setDraft] = useState('');
  const [connected, setConnected] = useState(false);
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [action, setAction] = useState<'block' | 'leave' | 'report' | null>(null);
  const [reportType, setReportType] = useState('');
  const [detail, setDetail] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);
  const menuButton = useRef<HTMLButtonElement>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);

  useEffect(() => {
    if (!roomExists || room?.isClosed) return;
    let disposed = false;
    let disconnect: (() => void) | undefined;
    connectChat(roomId, (message) => appendMessage(roomId, message))
      .then((close) => {
        if (disposed) close();
        else {
          disconnect = close;
          setConnected(true);
        }
      })
      .catch((reason: Error) => {
        if (!disposed) setError(reason.message);
      });
    return () => {
      disposed = true;
      disconnect?.();
    };
  }, [roomId, room?.isClosed, roomExists, connectionAttempt]);

  useEffect(() => {
    const read = () => {
      if (document.visibilityState === 'visible') markRoomRead(roomId);
    };
    read();
    document.addEventListener('visibilitychange', read);
    return () => document.removeEventListener('visibilitychange', read);
  }, [roomId, room?.unreadCount]);

  useEffect(() => {
    const list = messagesRef.current;
    if (list && stickToBottom.current) list.scrollTop = list.scrollHeight;
  }, [room?.messages.length]);

  useEffect(() => {
    if (!menuOpen) return;
    const outside = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
        menuButton.current?.focus();
      }
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [menuOpen]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.trim() || !connected || busyRef.current || room?.isClosed) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    const content = draft.trim();
    try {
      const message = await sendMessage(roomId, content, crypto.randomUUID());
      stickToBottom.current = true;
      appendMessage(roomId, message);
      setDraft('');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '전송하지 못했어요. 다시 시도해주세요.');
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  async function performAction(kind: 'block' | 'leave' | 'report') {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    try {
      await chatAction(roomId, kind, { type: reportType, detail: detail.trim() });
      setAction(null);
      if (kind === 'report') {
        toast('신고가 접수됐어요.');
        setReportType('');
        setDetail('');
      } else if (kind === 'leave') {
        leaveRoom(roomId);
        navigate('/chat', { replace: true });
      }
      // Blocking is intentionally silent: no success popup or status exposed to the partner.
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '처리하지 못했어요. 다시 시도해주세요.');
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  if (!room)
    return (
      <div className="hub-shell">
        <SidebarNav activeItem="chat" />
        <main className="hub-main ui-empty">
          <h1>채팅방을 찾을 수 없어요</h1>
          <Link className="ui-button ui-button--secondary" to="/chat">
            채팅목록으로
          </Link>
        </main>
      </div>
    );

  return (
    <div className="hub-shell conversation-shell">
      <SidebarNav activeItem="chat" />
      <main className="conversation-main">
        <header className="conversation-header">
          <Link
            to="/chat"
            className="ui-interactive conversation-icon"
            aria-label="채팅목록으로 돌아가기"
          >
            ‹
          </Link>
          <div className="chat-avatar" aria-hidden="true">
            <span style={{ filter: `blur(${revealBlur(room.revealStage)}px)` }}>
              {room.partnerName.charAt(0)}
            </span>
          </div>
          <div className="conversation-partner">
            <h1>{room.partnerName}</h1>
            <span>{room.isClosed ? '종료된 대화' : connected ? '대화 중' : '연결 중'}</span>
          </div>
          <div className="conversation-menu-wrap" ref={menuRef}>
            <button
              ref={menuButton}
              type="button"
              className="ui-interactive conversation-icon"
              aria-label="채팅 메뉴"
              aria-expanded={menuOpen}
              aria-controls="chat-actions"
              onClick={() => setMenuOpen(!menuOpen)}
            >
              ⋯
            </button>
            {menuOpen && (
              <div className="conversation-menu" id="chat-actions">
                {(['report', 'block', 'leave'] as const).map((kind, index) => (
                  <button
                    key={kind}
                    type="button"
                    className="ui-interactive"
                    disabled={busy}
                    onClick={() => {
                      menuButton.current?.focus();
                      setMenuOpen(false);
                      setError('');
                      setAction(kind);
                    }}
                  >
                    {['신고하기', '차단하기', '채팅방 나가기'][index]}
                  </button>
                ))}
              </div>
            )}
          </div>
        </header>
        <RevealProgress room={room} mini />
        <div
          className="conversation-messages"
          ref={messagesRef}
          role="log"
          aria-label="대화 메시지"
          aria-live="polite"
          onScroll={() => {
            const list = messagesRef.current!;
            stickToBottom.current = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
          }}
        >
          <p className="conversation-notice">서로를 존중하며 천천히 알아가 보세요.</p>
          {room.messages.length === 0 && (
            <p className="conversation-notice">아직 메시지가 없어요.</p>
          )}
          {room.messages.map((message, index) => (
            <div key={message.id}>
              {(index === 0 ||
                message.sentAt.slice(0, 10) !== room.messages[index - 1].sentAt.slice(0, 10)) && (
                <p className="conversation-date">
                  {new Date(message.sentAt).toLocaleDateString('ko-KR')}
                </p>
              )}
              <div className={`message-row ${message.sender === 'me' ? 'message-row--mine' : ''}`}>
                <div className="message-bubble">{message.content}</div>
                <time dateTime={message.sentAt}>
                  {new Date(message.sentAt).toLocaleTimeString('ko-KR', {
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                  {message.sender === 'me' && <span>전송됨</span>}
                </time>
              </div>
            </div>
          ))}
        </div>
        {error && action !== 'report' && (
          <p className="conversation-error" role="alert">
            {error}
            {!connected && !room.isClosed && (
              <button
                type="button"
                className="ui-interactive text-link"
                onClick={() => {
                  setError('');
                  setConnectionAttempt((value) => value + 1);
                }}
              >
                다시 연결
              </button>
            )}
          </p>
        )}
        <form className="conversation-composer" onSubmit={submit}>
          <textarea
            aria-label="메시지"
            placeholder={room.isClosed ? '종료된 대화예요' : '메시지를 입력해주세요'}
            value={draft}
            maxLength={4000}
            rows={2}
            disabled={room.isClosed || busy}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (
                event.key === 'Enter' &&
                !event.shiftKey &&
                !event.nativeEvent.isComposing &&
                event.keyCode !== 229
              ) {
                event.preventDefault();
                event.currentTarget.form?.requestSubmit();
              }
            }}
          />
          <button
            className="ui-button ui-button--primary"
            type="submit"
            disabled={!connected || room.isClosed || !draft.trim() || busy}
          >
            {busy ? '처리 중' : '전송'}
          </button>
        </form>
      </main>
      <ConfirmModal
        {...(action === 'block' ? confirmationContent.block : confirmationContent.leaveChat)}
        open={action === 'block' || action === 'leave'}
        onClose={() => setAction(null)}
        onConfirm={() => {
          if (action === 'block' || action === 'leave') void performAction(action);
        }}
      />
      <Modal open={action === 'report'} labelledBy="report-title" describedBy="report-description">
        <form
          className="report-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (reportType) void performAction('report');
          }}
        >
          <h2 id="report-title" className="cm-title">
            신고하기
          </h2>
          <p id="report-description" className="cm-description">
            {room.partnerName}님을 신고하는 이유를 알려주세요.
          </p>
          <fieldset disabled={busy}>
            <legend>신고 유형</legend>
            {reportTypes.map(([value, label]) => (
              <label key={value}>
                <input
                  type="radio"
                  name="report-type"
                  value={value}
                  required
                  checked={reportType === value}
                  onChange={() => setReportType(value)}
                />
                {label}
              </label>
            ))}
          </fieldset>
          <label htmlFor="report-detail">
            상세사유 {reportType === 'ETC' ? '(필수)' : '(선택)'}
          </label>
          <textarea
            id="report-detail"
            value={detail}
            maxLength={1000}
            required={reportType === 'ETC'}
            disabled={busy}
            onChange={(event) => setDetail(event.target.value)}
            placeholder="어떤 일이 있었는지 알려주세요"
          />
          <span className="report-count">{detail.length}/1000</span>
          {error && (
            <p className="field-message" role="alert">
              {error}
            </p>
          )}
          <div className="cm-actions">
            <button
              type="button"
              className="ui-button ui-button--secondary"
              disabled={busy}
              onClick={() => setAction(null)}
            >
              취소
            </button>
            <button
              type="submit"
              className="ui-button cm-confirm--danger"
              disabled={!reportType || busy || (reportType === 'ETC' && !detail.trim())}
            >
              {busy ? '접수 중' : '신고하기'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
