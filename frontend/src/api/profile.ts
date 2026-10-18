import { apiRequest, getAuthHeaders } from '../lib/auth';
import type { ProfileDraft } from '../lib/profileDraft';

export type MyAccount = {
  userId: number;
  nickname: string | null;
  email: string | null;
};

export async function getMyAccount() {
  const result = await apiRequest<{ data: MyAccount }>('/api/v1/users/me', {}, true);
  return result.data;
}

export async function getMyPhoto(signal: AbortSignal) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  const response = await fetch(`${baseUrl}/api/v1/users/me/image/content`, {
    headers: getAuthHeaders(false),
    credentials: 'include',
    signal,
  });
  if (response.status === 204) return null;
  if (!response.ok) throw new Error('프로필 사진을 불러오지 못했어요.');
  return response.blob();
}

export async function uploadProfilePhoto(file: File) {
  const body = new FormData();
  body.append('photo', file);
  const result = await apiRequest<{ data: { url: string } }>(
    '/api/mock/profile/photo',
    { method: 'POST', body },
    true,
  );
  return result.data.url;
}
export async function saveProfile(profile: ProfileDraft) {
  return apiRequest('/api/mock/profile', { method: 'PUT', body: JSON.stringify(profile) }, true);
}
