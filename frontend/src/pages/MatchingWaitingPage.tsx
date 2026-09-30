import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  cancelMatching,
  joinMatching,
  MATCHING_TIMEOUT_MS,
  readMatching,
  type MatchingState,
} from '../api/matching';
import ConfirmModal from '../components/ConfirmModal';
import MatchingTicketModal from '../components/MatchingTicketModal';
import { useToast } from '../components/useToast';
import { toastMessages } from '../components/feedbackContent';
import './MatchingWaitingPage.css';

export default function MatchingWaitingPage() {
  const navigate = useNavigate();
  const showToast = useToast();
  const [state, setState] = useState<MatchingState | null>(null);
  const [now, setNow] = useState(Date.now);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const cancelPending = useRef(false);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    function apply(next: MatchingState) {
      if (controller.signal.aborted || cancelPending.current) return;
      setState(next);
      setNow(Date.now());
      if (next.status === 'matched') navigate(`/chat/${next.roomId}`, { replace: true });
      if (next.status === 'waiting') timer = setTimeout(() => void poll(), 1000);
      else setConfirmOpen(false);
    }
    async function poll() {
      try {
        apply(await readMatching(controller.signal));
      } catch {
        if (!controller.signal.aborted && !cancelPending.current) setError(true);
      }
    }
    void joinMatching(controller.signal)
      .then(apply)
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      });
    const clock = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      mounted.current = false;
      controller.abort();
      clearTimeout(timer);
      clearInterval(clock);
    };
  }, [attempt, navigate]);

  function retry() {
    setError(false);
    setState(null);
    setAttempt((value) => value + 1);
  }

  async function cancel() {
    if (cancelPending.current) return;
    cancelPending.current = true;
    setCancelling(true);
    try {
      const result = await cancelMatching();
      if (!mounted.current) return;
      navigate(result?.status === 'matched' ? `/chat/${result.roomId}` : '/', { replace: true });
    } catch {
      if (mounted.current) {
        showToast(toastMessages.networkError);
        setAttempt((value) => value + 1);
      }
    } finally {
      cancelPending.current = false;
      if (mounted.current) setCancelling(false);
    }
  }

  const elapsed =
    state?.status === 'waiting'
      ? Math.min(
          MATCHING_TIMEOUT_MS / 1000,
          Math.max(0, Math.floor((now - state.startedAt) / 1000)),
        )
      : 0;
  const time = `${String(Math.floor(elapsed / 60)).padStart(2, '0')}:${String(elapsed % 60).padStart(2, '0')}`;
  const empty = state?.status === 'empty';

  return (
    <main className="matching-waiting-shell">
      <section className="ui-enter matching-waiting-card" aria-labelledby="matching-waiting-title">
        <button
          className="ui-interactive matching-back"
          type="button"
          aria-label="뒤로가기"
          disabled={cancelling || (!state && !error)}
          onClick={() =>
            empty || state?.status === 'exhausted'
              ? navigate('/', { replace: true })
              : setConfirmOpen(true)
          }
        >
          ←
        </button>
        <div className="matching-waiting-content">
          <div
            className={empty || error ? 'matching-empty-icon' : 'matching-pulse'}
            aria-hidden="true"
          >
            {empty || error ? <span>♡</span> : <span />}
          </div>
          <h1
            id="matching-waiting-title"
            className={empty ? 'matching-empty-title' : ''}
            aria-live="polite"
          >
            {error
              ? '연결을 확인해주세요'
              : empty
                ? '지금은 매칭 가능한 상대가 없어요'
                : state?.status === 'exhausted'
                  ? '매칭 기회를 모두 사용했어요'
                  : '매칭 상대를 찾고 있어요'}
          </h1>
          {!empty && !error && state?.status !== 'exhausted' && (
            <p className="matching-elapsed" role="timer" aria-label="경과 시간">
              {time}
            </p>
          )}
          {error && <p className="matching-elapsed">{toastMessages.networkError}</p>}
        </div>
        <div className="matching-waiting-actions">
          {empty || error ? (
            <>
              <button
                className="ui-button ui-button--primary matching-retry"
                type="button"
                onClick={retry}
              >
                다시 시도
              </button>
              <button
                className="ui-button ui-button--secondary matching-cancel"
                type="button"
                disabled={cancelling}
                onClick={() => (empty ? navigate('/', { replace: true }) : setConfirmOpen(true))}
              >
                나중에 하기
              </button>
            </>
          ) : (
            <button
              className="ui-button ui-button--secondary matching-cancel"
              type="button"
              disabled={cancelling || !state || state.status === 'exhausted'}
              onClick={() => setConfirmOpen(true)}
            >
              {cancelling ? '취소 중…' : '대기 취소'}
            </button>
          )}
        </div>
      </section>
      <ConfirmModal
        open={confirmOpen}
        title="매칭 대기를 취소하시겠어요?"
        description="대기를 취소하면 매칭 찾기가 중단돼요."
        confirmLabel="대기 취소"
        cancelLabel="계속 기다리기"
        onClose={() => setConfirmOpen(false)}
        onConfirm={() => void cancel()}
      />
      <MatchingTicketModal
        open={state?.status === 'exhausted'}
        onClose={() => navigate('/', { replace: true })}
      />
    </main>
  );
}
