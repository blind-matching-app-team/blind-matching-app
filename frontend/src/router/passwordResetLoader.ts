import type { LoaderFunctionArgs } from 'react-router-dom';
import { PasswordResetError, validatePasswordResetToken } from '../api/passwordReset';

type TokenStatus = 'valid' | 'invalid' | 'expired' | 'error';
export type TokenResult = { token: string; status: TokenStatus };

export function getTokenError(error: unknown): TokenStatus {
  if (error instanceof PasswordResetError) {
    if (error.code === 'RESET_TOKEN_EXPIRED') return 'expired';
    if (error.code === 'RESET_TOKEN_INVALID') return 'invalid';
  }
  return 'error';
}

export async function passwordResetLoader({ request }: LoaderFunctionArgs): Promise<TokenResult> {
  const token = new URL(request.url).searchParams.get('token')?.trim() ?? '';
  if (!token) return { token, status: 'invalid' };
  try {
    await validatePasswordResetToken(token, request.signal);
    return { token, status: 'valid' };
  } catch (error) {
    return { token, status: getTokenError(error) };
  }
}
