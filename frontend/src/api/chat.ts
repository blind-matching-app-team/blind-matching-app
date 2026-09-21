import { apiRequest } from '../lib/auth';
import type { Message } from '../lib/chatStore';

export function sendMessage(roomId: number, content: string, clientId: string) {
  return apiRequest<Message>(
    `/api/mock/chat/${roomId}/messages`,
    {
      method: 'POST',
      body: JSON.stringify({ content, clientId }),
    },
    true,
  );
}
export function chatAction(roomId: number, action: 'block' | 'leave' | 'report', body = {}) {
  return apiRequest(
    `/api/mock/chat/${roomId}/${action}`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
    true,
  );
}
export async function connectChat(roomId: number, receive: (message: Message) => void) {
  if (!import.meta.env.DEV) throw new Error('실시간 채팅 연결을 준비 중이에요.');
  const { connectMockChat } = await import('../mocks/chat');
  return connectMockChat(roomId, receive);
}
