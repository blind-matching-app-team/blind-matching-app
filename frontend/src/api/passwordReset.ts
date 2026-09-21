// Provisional S13 contract shared with MSW; real API integration is a separate task.
export class PasswordResetError extends Error {
  code: string;
  constructor(code: string) {
    super(code);
    this.code = code;
  }
}

async function post(action: string, body: Record<string, string>, signal?: AbortSignal) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  const response = await fetch(`${baseUrl}/api/v1/auth/password-reset/${action}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  });
  const payload = await response.json();
  if (!response.ok || !payload.success) {
    throw new PasswordResetError(payload.code ?? 'REQUEST_FAILED');
  }
}

export const requestPasswordReset = (email: string) => post('request', { email });
export const validatePasswordResetToken = (token: string, signal?: AbortSignal) =>
  post('validate', { token }, signal);
export const resetPassword = (token: string, password: string) =>
  post('confirm', { token, password });
