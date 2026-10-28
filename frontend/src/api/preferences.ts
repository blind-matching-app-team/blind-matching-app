import { apiRequest } from '../lib/auth';
import type { Preferences } from '../lib/preferences';

export function savePreferences(preferences: Preferences) {
  return apiRequest<{ data: { identityVerified: boolean } }>(
    '/api/mock/preferences',
    { method: 'PUT', body: JSON.stringify(preferences) },
    true,
  );
}
