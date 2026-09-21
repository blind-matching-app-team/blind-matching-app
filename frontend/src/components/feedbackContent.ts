import type { ConfirmModalContent } from './ConfirmModal';

export const confirmationContent = {
  logout: { title: '로그아웃 하시겠어요?', confirmLabel: '로그아웃', variant: 'normal' },
  deleteAccount: {
    title: '정말 탈퇴하시겠어요?',
    description: '탈퇴하면 모든 정보가 삭제되고 복구할 수 없어요.',
    confirmLabel: '탈퇴하기',
    variant: 'danger',
  },
  block: {
    title: '이 사람을 차단하시겠어요?',
    description: '차단하면 서로 매칭에서 제외되고 대화할 수 없어요.',
    confirmLabel: '차단하기',
    variant: 'danger',
  },
  rematch: {
    title: '재매칭 하시겠어요?',
    description: '재매칭권 1개가 소모되고, 지금 매칭은 종료돼요.',
    confirmLabel: '재매칭하기',
    variant: 'normal',
  },
} satisfies Record<string, ConfirmModalContent>;

export const toastMessages = {
  loginCancelled: '로그인이 취소됐어요',
  comingSoon: '준비 중인 기능이에요',
  saved: '저장됐어요',
  sessionExpired: '세션이 만료됐어요, 다시 로그인해주세요',
  networkError: '연결이 원활하지 않아요, 다시 시도해주세요',
  paymentCompleted: '결제가 완료됐어요',
  paymentFailed: '결제에 실패했어요, 다시 시도해주세요',
  subscriptionStarted: '구독이 시작됐어요',
} as const;
