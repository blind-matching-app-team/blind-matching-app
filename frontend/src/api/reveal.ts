import { apiRequest } from '../lib/auth';

export type RevealStage = 1 | 2 | 3;
export type RevealState = {
  roomId: number;
  stage: RevealStage;
  partner: {
    // Masking is decided server-side; the partial-reveal rule is still pending (BMA-19).
    displayName: string;
    mbti: string;
    region: string;
    commonInterestCount: number;
    // Null below stage 2, or when the partner has not entered a height.
    height: number | null;
    photoUrl: string | null;
  };
  // Null once the final stage is open.
  nextStage: {
    remainingMinutes: number;
    remainingMessages: number;
    requestedByMe: boolean;
    requestedByPartner: boolean;
  } | null;
  rematchTickets: number;
};

// Phase 2 contract; replace these mock endpoints when the Reveal API subtask is integrated.
export async function readReveal(roomId: number, signal: AbortSignal) {
  const result = await apiRequest<{ data: RevealState }>(
    `/api/mock/reveal/${roomId}`,
    { signal },
    true,
  );
  return result.data;
}

export async function requestNextStage(roomId: number) {
  const result = await apiRequest<{ data: RevealState }>(
    `/api/mock/reveal/${roomId}/request`,
    { method: 'POST' },
    true,
  );
  return result.data;
}

export async function consentNextStage(roomId: number) {
  const result = await apiRequest<{ data: RevealState }>(
    `/api/mock/reveal/${roomId}/consent`,
    { method: 'POST' },
    true,
  );
  return result.data;
}

export async function rematch(roomId: number) {
  await apiRequest(`/api/mock/reveal/${roomId}/rematch`, { method: 'POST' }, true);
}
