import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  consentNextStage,
  readReveal,
  rematch,
  requestNextStage,
  type RevealStage,
  type RevealState,
} from '../api/reveal';
import ConfirmModal from '../components/ConfirmModal';
import MatchingTicketModal from '../components/MatchingTicketModal';
import { useToast } from '../components/useToast';
import { confirmationContent, toastMessages } from '../components/feedbackContent';
import ErrorPage from './ErrorPage';
import './RevealPage.css';

const STAGES = 3;
// S10-02 silhouette 10px, S10-09 partial 2px; stage 3 shows the S3 photo as-is.
const BLUR: Record<RevealStage, number> = { 1: 10, 2: 2, 3: 0 };

const errorCode = (error: unknown) =>
  error instanceof Error ? (error.cause as { code?: string } | undefined)?.code : undefined;

function remainingLabel({
  remainingMinutes,
  remainingMessages,
}: NonNullable<RevealState['nextStage']>) {
  const hours = Math.floor(remainingMinutes / 60);
  const minutes = remainingMinutes % 60;
  const parts = [];
  if (remainingMinutes > 0)
    parts.push(`${hours ? `${hours}시간 ` : ''}${hours && !minutes ? '' : `${minutes}분 `}후`);
  if (remainingMessages > 0) parts.push(`대화 ${remainingMessages}개 더 필요`);
  return parts.join(' · ');
}

export default function RevealPage() {
  const navigate = useNavigate();
  const showToast = useToast();
  const roomId = Number(useParams().roomId);
  const [reveal, setReveal] = useState<RevealState | null>(null);
  const [error, setError] = useState<'notFound' | 'network' | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [pending, setPending] = useState(false);
  const [consentDismissed, setConsentDismissed] = useState(false);
  const [rematchOpen, setRematchOpen] = useState(false);
  const [ticketOpen, setTicketOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    readReveal(roomId, controller.signal)
      .then(setReveal)
      .catch((reason) => {
        if (controller.signal.aborted) return;
        setError(errorCode(reason) === 'MATCH_NOT_FOUND' ? 'notFound' : 'network');
      });
    return () => controller.abort();
  }, [roomId, attempt]);

  async function run(action: () => Promise<void>) {
    if (pending) return;
    setPending(true);
    try {
      await action();
    } catch (reason) {
      if (errorCode(reason) === 'NO_REMATCH_TICKET') setTicketOpen(true);
      else showToast(reason instanceof Error ? reason.message : toastMessages.networkError);
    } finally {
      setPending(false);
    }
  }

  if (error === 'notFound' || !Number.isInteger(roomId))
    return (
      <ErrorPage
        title="매칭 정보를 찾을 수 없어요"
        description="이미 종료되었거나 존재하지 않는 매칭이에요"
      />
    );

  if (!reveal)
    return (
      <main className="reveal-shell">
        <section className="ui-enter reveal-card reveal-card--status">
          {error ? (
            <>
              <p className="reveal-guide">{toastMessages.networkError}</p>
              <button
                type="button"
                className="ui-button ui-button--primary reveal-start"
                onClick={() => {
                  setError(null);
                  setAttempt((value) => value + 1);
                }}
              >
                다시 시도
              </button>
            </>
          ) : (
            <p className="reveal-guide" role="status">
              매칭 정보를 불러오고 있어요…
            </p>
          )}
        </section>
      </main>
    );

  const { stage, partner, nextStage } = reveal;
  const conditionMet =
    nextStage !== null && !nextStage.remainingMinutes && !nextStage.remainingMessages;
  const requested = nextStage?.requestedByMe ?? false;

  return (
    <main className="reveal-shell">
      <section className="ui-enter reveal-card" aria-labelledby="reveal-name">
        {/* S10-01 */}
        <button
          className="ui-interactive reveal-back"
          type="button"
          aria-label="뒤로가기"
          onClick={() => navigate('/')}
        >
          ←
        </button>

        <div className="reveal-profile">
          {/* S10-02 (1단계) / S10-09 (2단계) */}
          <div
            className="reveal-avatar"
            role="img"
            aria-label={stage === 3 ? '상대 프로필 사진' : `Reveal ${stage}단계로 가려진 프로필`}
          >
            {stage === 3 && partner.photoUrl ? (
              <img src={partner.photoUrl} alt="" />
            ) : (
              <svg
                viewBox="0 0 120 120"
                aria-hidden="true"
                style={{ filter: `blur(${BLUR[stage]}px)` }}
              >
                <circle cx="60" cy="46" r="22" />
                <path d="M18 120c0-26 19-44 42-44s42 18 42 44Z" />
              </svg>
            )}
          </div>

          {/* S10-03 (1단계) / S10-10 (2단계) */}
          <h1 id="reveal-name" className="reveal-name">
            {partner.displayName}
          </h1>

          {/* S10-04 */}
          <ul className="reveal-tags" aria-label="상대 정보">
            <li>{partner.mbti}</li>
            <li>{partner.region}</li>
            <li>공통관심사 {partner.commonInterestCount}개</li>
          </ul>
          {/* S10-18: 2단계부터, 키를 입력한 유저만 */}
          {stage >= 2 && partner.height !== null && (
            <ul className="reveal-tags reveal-tags--key" aria-label="공개된 정보">
              <li>{partner.height}cm</li>
            </ul>
          )}

          {/* S10-05 */}
          <div
            className="reveal-progress"
            role="progressbar"
            aria-label="Reveal 진행도"
            aria-valuemin={1}
            aria-valuemax={STAGES}
            aria-valuenow={stage}
            aria-valuetext={`${STAGES}단계 중 ${stage}단계`}
          >
            {Array.from({ length: STAGES }, (_, index) => (
              <span key={index} className={index < stage ? 'is-filled' : ''} />
            ))}
          </div>

          {/* S10-06 (1단계) / S10-11 (2단계) */}
          {stage === 1 && (
            <p className="reveal-guide">
              매칭 후 24시간이 지나고, 대화가 20개 이상 쌓이면 서로 동의하에 다음 단계를 열 수
              있어요
            </p>
          )}
          {stage === 2 && <p className="reveal-guide">조금만 더 대화하면 전체 공개돼요</p>}
        </div>

        <div className="reveal-actions">
          {stage === 1 && nextStage && (
            <>
              {/* S10-13 */}
              {!requested && (
                <p className={conditionMet ? 'reveal-condition is-met' : 'reveal-condition'}>
                  {conditionMet
                    ? '✓ 조건 충족 — 지금 요청할 수 있어요'
                    : '아직 조건을 채우지 못했어요'}
                </p>
              )}
              {/* S10-12: 미충족도 숨기지 않고 비활성 정보로 노출 */}
              <button
                type="button"
                className={`ui-interactive reveal-chip${conditionMet ? ' is-met' : ''}`}
                disabled={!conditionMet || requested || pending}
                onClick={() =>
                  void run(async () => {
                    setReveal(await requestNextStage(roomId));
                  })
                }
              >
                {requested
                  ? '요청 완료 · 상대의 동의를 기다리고 있어요'
                  : conditionMet
                    ? '✓ 조건 충족 · 다음 단계 요청하기'
                    : remainingLabel(nextStage)}
              </button>
            </>
          )}

          {/* S10-07 */}
          <button
            type="button"
            className="ui-button ui-button--primary reveal-start"
            onClick={() => navigate(`/chat/${roomId}`)}
          >
            대화 시작하기
          </button>

          {/* S10-19 */}
          <button
            type="button"
            className="reveal-rematch"
            disabled={pending}
            onClick={() => (reveal.rematchTickets > 0 ? setRematchOpen(true) : setTicketOpen(true))}
          >
            재매칭 요청 (즉시) · 보유 {reveal.rematchTickets}개
          </button>
        </div>
      </section>

      {/* S10-14~17: '나중에'는 무응답과 동일하게 API 호출 없이 닫기만 한다. */}
      <ConfirmModal
        open={Boolean(nextStage?.requestedByPartner) && !consentDismissed}
        title={`${partner.displayName}님이 다음 단계를 요청했어요`}
        description="동의하면 서로 조금 더 선명하게 보여요"
        confirmLabel="동의하고 열기"
        cancelLabel="나중에"
        onClose={() => setConsentDismissed(true)}
        onConfirm={() =>
          void run(async () => {
            setReveal(await consentNextStage(roomId));
          })
        }
      />
      <ConfirmModal
        open={rematchOpen}
        {...confirmationContent.rematch}
        onClose={() => setRematchOpen(false)}
        onConfirm={() =>
          void run(async () => {
            await rematch(roomId);
            navigate('/matching/waiting', { replace: true });
          })
        }
      />
      {/* v1.4: 재매칭권 소진 시 CM-13(소모형) */}
      <MatchingTicketModal
        open={ticketOpen}
        message="재매칭권을 모두 사용했어요. 즉시 재매칭하려면 이용권이 필요해요."
        onClose={() => setTicketOpen(false)}
      />
    </main>
  );
}
