import { apiRequest } from '../lib/auth';

// Mirrors backend/docs/PHOTO_VERIFICATION_API.md (BMA-82).
export type PhotoVerificationStatus = {
  verified: boolean;
  verifiedAt: string | null;
  attempts: number;
  lastResult: 'PASS' | 'FAIL' | 'ERROR' | null;
};

// Phase 2 contract; swap /api/mock for /api/v1 when the API subtask is integrated.
const BASE = '/api/mock/verification/photo';

export async function readPhotoVerification(signal: AbortSignal) {
  const result = await apiRequest<{ data: PhotoVerificationStatus }>(
    `${BASE}/status`,
    { signal },
    true,
  );
  return result.data;
}

/** The selfie is only compared server-side; it is never stored or shown (BMA-82 §2). */
export async function verifySelfie(selfie: Blob) {
  const body = new FormData();
  body.append('file', selfie, 'selfie.jpg');
  const result = await apiRequest<{ data: { verified: true; verifiedAt: string } }>(
    BASE,
    { method: 'POST', body },
    true,
  );
  return result.data;
}
