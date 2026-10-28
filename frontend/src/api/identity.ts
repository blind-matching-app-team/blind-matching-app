import { apiRequest } from '../lib/auth';

// Mirrors backend/docs/IDENTITY_VERIFICATION_API.md (BMA-79).
export type IdentityStatus = {
  verified: boolean;
  verifiedAt: string | null;
  provider: string | null;
  matchingAllowed: boolean;
  lastAttemptStatus: string | null;
};
export type IdentityRequest = {
  transactionId: string;
  provider: string;
  sdkParams: Record<string, unknown>;
  expiresAt: string;
};

// Phase 2 contract; swap /api/mock for /api/v1 when the API subtask is integrated.
const BASE = '/api/mock/verification/identity';

export async function readIdentityStatus(signal: AbortSignal) {
  const result = await apiRequest<{ data: IdentityStatus }>(`${BASE}/status`, { signal }, true);
  return result.data;
}

export async function requestIdentity() {
  const result = await apiRequest<{ data: IdentityRequest }>(
    `${BASE}/request`,
    { method: 'POST' },
    true,
  );
  return result.data;
}

export async function confirmIdentity(transactionId: string, providerPayload: unknown) {
  const result = await apiRequest<{ data: { status: 'VERIFIED'; adult: boolean } }>(
    `${BASE}/confirm`,
    { method: 'POST', body: JSON.stringify({ transactionId, providerPayload }) },
    true,
  );
  return result.data;
}

/** Opens the provider's verification window and resolves with its raw result. */
export async function runIdentitySdk(request: IdentityRequest) {
  // The provider (PASS/NICE …) is not contracted yet; only the dev mock SDK exists.
  if (!import.meta.env.DEV) throw new Error('본인인증 서비스를 준비 중이에요.');
  const { openMockIdentitySdk } = await import('../mocks/identity');
  return openMockIdentitySdk(request);
}
