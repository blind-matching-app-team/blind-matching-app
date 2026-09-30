import { http, HttpResponse } from 'msw';
import { getUserId } from '../lib/auth';
import { MATCHING_TIMEOUT_MS, type MatchingState } from '../api/matching';
import { getRooms } from '../lib/chatStore';

type Queue = { startedAt: number; scenario: string };
const key = () => `bma-matching-queue-${getUserId()}`;
const readQueue = (): Queue | null => JSON.parse(sessionStorage.getItem(key()) || 'null');
const respond = (data: MatchingState | null) => HttpResponse.json({ data });

function state(queue: Queue): MatchingState {
  const elapsed = Date.now() - queue.startedAt;
  if (queue.scenario === 'matched' && elapsed >= 3000) {
    const room = getRooms().find((item) => !item.isClosed);
    if (room) return { status: 'matched', roomId: room.id };
  }
  if (elapsed >= MATCHING_TIMEOUT_MS || (queue.scenario === 'empty' && elapsed >= 3000)) {
    return { status: 'empty' };
  }
  return { status: 'waiting', startedAt: queue.startedAt };
}

export const matchingHandlers = [
  http.post('/api/mock/matching/queue', () => {
    const scenario = sessionStorage.getItem('bma-matching-scenario') || 'waiting';
    const current = readQueue();
    // Repeated mounts and reloads must not register another queue or restart its clock.
    if (current && state(current).status !== 'empty') return respond(state(current));
    if (scenario === 'exhausted') return respond({ status: 'exhausted' });
    const queue = { startedAt: Date.now(), scenario };
    sessionStorage.setItem(key(), JSON.stringify(queue));
    return respond(state(queue));
  }),
  http.get('/api/mock/matching/queue', () => {
    const queue = readQueue();
    return respond(queue ? state(queue) : { status: 'empty' });
  }),
  http.delete('/api/mock/matching/queue', () => {
    const queue = readQueue();
    const current = queue ? state(queue) : null;
    // A completed match wins a simultaneous cancellation.
    if (current?.status === 'matched') return respond(current);
    sessionStorage.removeItem(key());
    return respond(null);
  }),
];
