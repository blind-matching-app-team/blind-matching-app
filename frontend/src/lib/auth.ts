const STORAGE_KEYS = {
  accessToken: 'bma_access_token',
  refreshToken: 'bma_refresh_token',
  userId: 'bma_user_id',
} as const;

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
  userId: number;
};

export function setAuthSession(tokens: AuthTokens) {
  sessionStorage.setItem(STORAGE_KEYS.accessToken, tokens.accessToken);
  sessionStorage.setItem(STORAGE_KEYS.refreshToken, tokens.refreshToken);
  sessionStorage.setItem(STORAGE_KEYS.userId, String(tokens.userId));
}

export function getAccessToken() {
  return sessionStorage.getItem(STORAGE_KEYS.accessToken) ?? '';
}

export function getRefreshToken() {
  return sessionStorage.getItem(STORAGE_KEYS.refreshToken) ?? '';
}

export function getUserId() {
  const id = sessionStorage.getItem(STORAGE_KEYS.userId);
  return id ? Number(id) : null;
}

export function hasActiveSession() {
  return Boolean(getAccessToken() && getRefreshToken());
}

export function clearAuthSession() {
  sessionStorage.removeItem(STORAGE_KEYS.accessToken);
  sessionStorage.removeItem(STORAGE_KEYS.refreshToken);
  sessionStorage.removeItem(STORAGE_KEYS.userId);
}

export function getAuthHeaders(includeJson = true) {
  const headers: Record<string, string> = {};
  const token = getAccessToken();

  if (includeJson) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}

export async function apiRequest<T>(
  input: string,
  init: RequestInit = {},
  requireAuth = false,
): Promise<T> {
  const headers = new Headers(init.headers ?? {});

  if (!headers.has('Content-Type') && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (requireAuth) {
    const token = getAccessToken();
    if (!token) {
      throw new Error('인증이 만료되었습니다. 다시 로그인해주세요.');
    }

    headers.set('Authorization', `Bearer ${token}`);
  }

  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  const requestUrl = input.startsWith('http') ? input : `${baseUrl}${input}`;

  try {
    const response = await fetch(requestUrl, {
      ...init,
      headers,
      credentials: 'include',
    });

    const text = await response.text();
    const payload = text ? JSON.parse(text) : null;

    if (!response.ok) {
      const message = payload?.message ?? '요청 처리 중 오류가 발생했습니다.';
      const error = new Error(message) as Error & { code?: string; detail?: unknown };
      error.code = payload?.code;
      error.detail = payload?.data;
      throw error;
    }

    return payload as T;
  } catch (error) {
    if (error instanceof Error && error.name === 'TypeError') {
      throw new Error('백엔드 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.', {
        cause: error,
      });
    }

    if (error instanceof Error) {
      throw new Error(error.message, { cause: error });
    }

    throw new Error('요청 처리 중 오류가 발생했습니다.', { cause: error });
  }
}
