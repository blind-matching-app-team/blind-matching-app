export const lifestyleChoices = {
  drinking: { label: '음주', options: ['안 마셔요', '가끔 마셔요', '주 1~2회', '자주 마셔요'] },
  smoking: { label: '흡연', options: ['비흡연', '금연 중', '전자담배', '흡연'] },
  occupation: {
    label: '직업',
    options: [
      '직장인',
      '공무원·공공기관',
      '전문직',
      '자영업·사업',
      '프리랜서',
      '학생',
      '취업 준비 중',
      '쉬는 중',
      '기타',
    ],
  },
  religion: { label: '종교', options: ['무교', '기독교', '천주교', '불교', '기타'] },
  relationship: {
    label: '연애관',
    options: [
      '천천히 알아가고 싶어요',
      '진지한 만남을 원해요',
      '결혼을 염두에 두고 있어요',
      '함께 방향을 찾아가고 싶어요',
    ],
  },
} as const;
export type LifestyleChoice = keyof typeof lifestyleChoices;
export const hobbyOptions = [
  '산책',
  '러닝',
  '헬스',
  '등산',
  '자전거',
  '수영',
  '영화',
  '드라마',
  '독서',
  '게임',
  '보드게임',
  '요리',
  '맛집 탐방',
  '카페',
  '여행',
  '음악 감상',
  '악기',
  '전시',
  '사진',
  '반려동물',
];
export const MAX_HOBBIES = 5;
export const MAX_INTRODUCTION = 100;
export type ProfileLifestyle = Record<LifestyleChoice, string> & {
  hobbies: string[];
  introduction: string;
};
export const emptyLifestyle: ProfileLifestyle = {
  drinking: '',
  smoking: '',
  occupation: '',
  religion: '',
  relationship: '',
  hobbies: [],
  introduction: '',
};

export function validateLifestyle(value: ProfileLifestyle): string {
  for (const key of Object.keys(lifestyleChoices) as LifestyleChoice[]) {
    const choice = value[key];
    if (choice !== '' && !(lifestyleChoices[key].options as readonly string[]).includes(choice))
      return `${lifestyleChoices[key].label} 항목을 다시 선택해주세요.`;
  }
  if (
    !Array.isArray(value.hobbies) ||
    value.hobbies.length > MAX_HOBBIES ||
    new Set(value.hobbies).size !== value.hobbies.length ||
    value.hobbies.some((hobby) => !hobbyOptions.includes(hobby))
  )
    return '취미는 목록에서 최대 5개까지 선택해주세요.';
  if (
    typeof value.introduction !== 'string' ||
    Array.from(value.introduction).length > MAX_INTRODUCTION
  )
    return '한 줄 소개는 100자 이내로 입력해주세요.';
  return '';
}
