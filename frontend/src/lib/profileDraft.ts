import { getUserId } from './auth';
import { emptyLifestyle, type ProfileLifestyle } from './profileLifestyle';

export type ProfileDraft = ProfileLifestyle & {
  nickname: string;
  birthDate: string;
  province: string;
  district: string;
  mbti: string;
  height: string;
  photo: string;
};

export const emptyProfile: ProfileDraft = {
  ...emptyLifestyle,
  nickname: '',
  birthDate: '',
  province: '',
  district: '',
  mbti: '',
  height: '',
  photo: '',
};
export const profileKey = () => `bma_profile_draft_${getUserId() ?? 'guest'}`;
export function readProfile(): ProfileDraft {
  try {
    return {
      ...emptyProfile,
      ...JSON.parse(
        localStorage.getItem(profileKey()) ?? sessionStorage.getItem(profileKey()) ?? '{}',
      ),
    };
  } catch {
    return { ...emptyProfile };
  }
}
export const mbtiOptions = [
  'ISTJ',
  'ISFJ',
  'INFJ',
  'INTJ',
  'ISTP',
  'ISFP',
  'INFP',
  'INTP',
  'ESTP',
  'ESFP',
  'ENFP',
  'ENTP',
  'ESTJ',
  'ESFJ',
  'ENFJ',
  'ENTJ',
];
