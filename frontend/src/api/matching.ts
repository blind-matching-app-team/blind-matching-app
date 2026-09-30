import { apiRequest } from '../lib/auth';

export const MATCHING_TIMEOUT_MS = 5 * 60 * 1000;
export type MatchingState =
  | { status: 'waiting'; startedAt: number }
  | { status: 'matched'; roomId: number }
  | { status: 'empty' | 'exhausted' };

// Phase 2 contract; replace these mock endpoints when the API subtask is integrated.
export async function joinMatching(signal: AbortSignal) {
  const result = await apiRequest<{ data: MatchingState }>(
    '/api/mock/matching/queue',
    { method: 'POST', signal },
    true,
  );
  return result.data;
}

export async function readMatching(signal: AbortSignal) {
  const result = await apiRequest<{ data: MatchingState }>(
    '/api/mock/matching/queue',
    { signal },
    true,
  );
  return result.data;
}

export async function cancelMatching() {
  const result = await apiRequest<{ data: MatchingState | null }>(
    '/api/mock/matching/queue',
    { method: 'DELETE' },
    true,
  );
  return result.data;
}
