import { useSyncExternalStore } from 'react';
import { getUserId } from './auth';

export type Message = { id: string; sender: 'me' | 'partner'; content: string; sentAt: string };
export function revealBlur(stage: number) {
  return Math.max(0, 5 - stage) * 2;
}
export type Room = {
  id: number;
  partnerUserId: number;
  partnerName: string;
  lastMessage: string;
  lastSeenAt: string;
  unreadCount: number;
  isClosed: boolean;
  revealStage: number;
  revealPercent: number;
  isOnline: boolean;
  messages: Message[];
};
type ChatState = {
  rooms: Room[];
  readNotifications: number[];
};
const listeners = new Set<() => void>();
let owner = '';
let state: ChatState;
const seed = (): ChatState => ({
  readNotifications: [],
  rooms: ['김서윤', '박지훈', '이도윤'].map((partnerName, index) => ({
    id: index + 1,
    partnerUserId: index + 101,
    partnerName,
    lastMessage: [
      '오늘 저녁에 시간 괜찮으세요?',
      '추천 장소를 보냈어요.',
      '매칭이 종료된 대화입니다.',
    ][index],
    lastSeenAt: ['오전 9:48', '어제', '2일 전'][index],
    unreadCount: index === 0 ? 3 : 0,
    isClosed: index === 2,
    revealStage: [2, 4, 5][index],
    revealPercent: [72, 85, 100][index],
    isOnline: index === 0,
    messages:
      index === 2
        ? []
        : [
            {
              id: `seed-${index}-1`,
              sender: 'partner',
              content: '안녕하세요! 만나서 반가워요.',
              sentAt: '2026-09-21T09:40:00',
            },
            {
              id: `seed-${index}-2`,
              sender: 'me',
              content: '반가워요. 오늘 하루는 어떠세요?',
              sentAt: '2026-09-21T09:42:00',
            },
            {
              id: `seed-${index}-3`,
              sender: 'partner',
              content: index === 0 ? '오늘 저녁에 시간 괜찮으세요?' : '추천 장소를 보냈어요.',
              sentAt: '2026-09-21T09:48:00',
            },
          ],
  })),
});
function snapshot() {
  const key = `bma-chat-v1-${getUserId() ?? 'guest'}`;
  if (owner !== key) {
    owner = key;
    try {
      state = JSON.parse(sessionStorage.getItem(key) || 'null') || seed();
    } catch {
      state = seed();
    }
  }
  return state;
}
function update(next: ChatState) {
  state = next;
  try {
    sessionStorage.setItem(owner, JSON.stringify(state));
  } catch {
    /* In-memory fallback. */
  }
  listeners.forEach((listener) => listener());
}
export function useChatStore() {
  return useSyncExternalStore((listener) => {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, snapshot);
}
export const getRooms = () => snapshot().rooms;
export function markRoomRead(id: number) {
  const current = snapshot();
  if (!current.rooms.some((room) => room.id === id && room.unreadCount)) return;
  update({
    ...current,
    rooms: current.rooms.map((room) => (room.id === id ? { ...room, unreadCount: 0 } : room)),
  });
}
export function leaveRoom(id: number) {
  const current = snapshot();
  update({ ...current, rooms: current.rooms.filter((room) => room.id !== id) });
}
export function markNotificationsRead(ids: number[]) {
  const current = snapshot();
  update({ ...current, readNotifications: [...new Set([...current.readNotifications, ...ids])] });
}
export function appendMessage(roomId: number, message: Message) {
  const current = snapshot();
  update({
    ...current,
    rooms: current.rooms.map((room) => {
      if (room.id !== roomId || room.messages.some((item) => item.id === message.id)) return room;
      return {
        ...room,
        messages: [...room.messages, message],
        lastMessage: message.content,
        lastSeenAt: new Date(message.sentAt).toLocaleTimeString('ko-KR', {
          hour: '2-digit',
          minute: '2-digit',
        }),
        unreadCount: room.unreadCount + (message.sender === 'partner' ? 1 : 0),
      };
    }),
  });
}

export const reportTypes = [
  ['ABUSE', '욕설 및 괴롭힘'],
  ['FAKE', '허위 프로필'],
  ['FRAUD', '사기 및 금전 요구'],
  ['SEXUAL', '부적절한 성적 콘텐츠'],
  ['ETC', '기타'],
] as const;
