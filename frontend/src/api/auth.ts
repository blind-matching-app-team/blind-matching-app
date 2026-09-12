import { apiRequest, clearAuthSession, setAuthSession } from '../lib/auth';

export type AuthApiResponse<T> = {
  success: boolean;
  code: string;
  message: string;
  data: T;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userId: number;
  profileCompleted: boolean;
};

export type SignupResponse = {
  userId: number;
  status: string;
};

export type SuspendedAccountDetail = {
  errorCode: string;
  restrictionType: 'TEMPORARY' | 'PERMANENT';
  reason: string;
  restrictedUntil?: string | null;
};

export type DuplicateEmailDetail = {
  errorCode: string;
  provider: string;
};

export async function loginApi(email: string, password: string) {
  const response = await apiRequest<AuthApiResponse<LoginResponse>>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

  if (response.success) {
    setAuthSession({
      accessToken: response.data.accessToken,
      refreshToken: response.data.refreshToken,
      userId: response.data.userId,
    });
  }

  return response;
}

export async function signupApi(email: string, password: string, phoneNumber = '') {
  return apiRequest<AuthApiResponse<SignupResponse>>('/api/v1/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ email, password, phoneNumber }),
  });
}

export function logout() {
  clearAuthSession();
}
