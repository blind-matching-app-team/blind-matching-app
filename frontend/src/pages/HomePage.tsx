import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import SidebarNav from '../components/SidebarNav';
import { getUserId } from '../lib/auth';

export default function HomePage() {
  const navigate = useNavigate();
  const userId = getUserId();
  const [matchState, setMatchState] = useState<'match' | 'empty'>('match');

  const handleStartMatching = () => {
    navigate('/matching/waiting');
  };

  const handleOpenChat = () => {
    navigate('/chat');
  };

  return (
    <div className="hub-shell">
      <SidebarNav activeItem="matching" userId={userId ?? 1} unreadTotal={3} />

      <main className="hub-main">
        <header className="hub-header">
          <div>
            <p className="eyebrow">Main Hub</p>
            <h1>매칭</h1>
          </div>

          <div className="demo-toggle" aria-label="매칭 상태 보기">
            <button
              type="button"
              className={matchState === 'match' ? 'toggle-button active' : 'toggle-button'}
              onClick={() => setMatchState('match')}
            >
              매칭 있음
            </button>
            <button
              type="button"
              className={matchState === 'empty' ? 'toggle-button active' : 'toggle-button'}
              onClick={() => setMatchState('empty')}
            >
              매칭 없음
            </button>
          </div>
        </header>

        {matchState === 'match' ? (
          <section className="match-panel">
            <div className="match-card">
              <div className="match-card-header">
                <div>
                  <p className="match-label">진행 중인 매칭</p>
                  <h2>김서윤님과의 매칭</h2>
                </div>
                <span className="match-status">매칭 중</span>
              </div>

              <div className="profile-ghost" aria-hidden="true">
                <div className="ghost-avatar">S</div>
              </div>

              <div className="reveal-box" aria-label="Reveal 진행도">
                <div className="reveal-track">
                  <span className="reveal-fill" style={{ width: '72%' }} />
                </div>
                <span className="reveal-percent">72%</span>
              </div>

              <button type="button" className="primary-button large" onClick={handleOpenChat}>
                대화 시작하기
              </button>

              <button type="button" className="text-link subtle" onClick={handleStartMatching}>
                매칭 그만두기
              </button>
            </div>

            <div className="action-row">
              <button type="button" className="secondary-button" onClick={handleStartMatching}>
                재매칭하기
              </button>
            </div>
          </section>
        ) : (
          <section className="empty-panel">
            <div className="empty-icon" aria-hidden="true">
              <span>♡</span>
            </div>
            <p className="empty-title">아직 매칭된 상대가 없어요</p>
            <p className="empty-text">관심사를 바탕으로 새로운 매칭을 시작해 보세요.</p>
            <button type="button" className="primary-button large" onClick={handleStartMatching}>
              매칭 시작하기
            </button>
          </section>
        )}
      </main>
    </div>
  );
}
