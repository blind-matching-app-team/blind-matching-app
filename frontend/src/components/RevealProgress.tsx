import type { Room } from '../lib/chatStore';

export default function RevealProgress({ room, mini = false }: { room: Room; mini?: boolean }) {
  return (
    <div className={mini ? 'reveal-box reveal-mini' : 'reveal-box'}>
      <span className="reveal-percent">
        Reveal {room.revealStage}단계 · {room.revealPercent}%
      </span>
      <div
        className="reveal-track"
        role="progressbar"
        aria-label="Reveal 진행도"
        aria-valuenow={room.revealPercent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span className="reveal-fill" style={{ width: `${room.revealPercent}%` }} />
      </div>
    </div>
  );
}
