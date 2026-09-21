import { getUserId } from './auth';
import { regions } from '../data/regions';

export type Preferences = { province: string; district: string; minAge: string; maxAge: string };
export const defaultPreferences: Preferences = {
  province: '',
  district: '',
  minAge: '',
  maxAge: '',
};
export const setupKey = (name: string) => `bma_setup_${getUserId()}_${name}`;
export function readPreferences(): Preferences {
  try {
    return {
      ...defaultPreferences,
      ...JSON.parse(
        localStorage.getItem(setupKey('preferences-draft')) ??
          localStorage.getItem(setupKey('preferences')) ??
          '{}',
      ),
    };
  } catch {
    return { ...defaultPreferences };
  }
}
export function validatePreferences(value: Preferences): string {
  if (value.province && !regions.some((r) => r.code === value.province && !r.parent))
    return '희망 지역을 다시 선택해주세요.';
  if (
    value.district &&
    !regions.some((r) => r.code === value.district && r.parent === value.province)
  )
    return '시/군/구를 다시 선택해주세요.';
  for (const age of [value.minAge, value.maxAge]) {
    if (age !== '' && (!/^\d+$/.test(age) || Number(age) < 19 || Number(age) > 99))
      return '나이는 19~99세 범위로 선택해주세요.';
  }
  if (value.minAge && value.maxAge && Number(value.minAge) > Number(value.maxAge))
    return '최대 나이는 최소 나이보다 크거나 같아야 해요.';
  return '';
}
export function initialSetupPath(profileCompleted: boolean) {
  const mock = import.meta.env.DEV;
  if (
    !profileCompleted &&
    !(mock && localStorage.getItem(setupKey('profile-completed')) === 'true')
  )
    return '/profile/edit';
  if (!mock || !localStorage.getItem(setupKey('preferences'))) return '/profile/preferences';
  return localStorage.getItem(setupKey('verified')) === 'true' ? '/' : '/identity-verification';
}
