import { apiRequest } from '../lib/auth';
import type { ProfileDraft } from '../lib/profileDraft';

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
