import { delay, http, HttpResponse } from 'msw';
import { appendMessage, getRooms, reportTypes, type Message } from '../lib/chatStore';
import { getUserId } from '../lib/auth';

// Phase 2 mock socket: subscribers receive server events independently of REST responses.
const connections = new Map<number, Set<(message: Message) => void>>();
export function connectMockChat(roomId: number, receive: (message: Message) => void) {
  const subscribers = connections.get(roomId) ?? new Set();
  connections.set(roomId, subscribers);
  subscribers.add(receive);
  return () => {
    subscribers.delete(receive);
  };
}
function safetyKey() {
  return `bma-chat-safety-${getUserId()}`;
}
type SafetyState = {
  blocked: number[];
  blockedBy: number[];
  excludedPartnerIds?: number[];
  reports: { roomId: number; type: string; detail: string; queue: string }[];
};
function safety(): SafetyState {
  try {
    return (
      JSON.parse(sessionStorage.getItem(safetyKey()) || 'null') || {
        blocked: [],
        blockedBy: [],
        reports: [],
      }
    );
  } catch {
    return { blocked: [], blockedBy: [], reports: [] };
  }
}
const fail = (message: string, status = 400) => HttpResponse.json({ message }, { status });
export const chatHandlers = [
  http.post('/api/mock/chat/:id/messages', async ({ params, request }) => {
    const roomId = Number(params.id);
    const room = getRooms().find((item) => item.id === roomId);
    if (!room || room.isClosed) return fail('대화할 수 없는 채팅방이에요.', 404);
    const body = (await request.json()) as { content?: string; clientId?: string };
    if (typeof body.content !== 'string' || !body.content.trim() || body.content.length > 4000)
      return fail('메시지는 1~4000자로 입력해주세요.');
    const user = getUserId();
    const message: Message = {
      id: body.clientId || crypto.randomUUID(),
      sender: 'me',
      content: body.content.trim(),
      sentAt: new Date().toISOString(),
    };
    await delay(180);
    const policy = safety();
    // A blocked sender gets exactly the same successful response, but no delivery/reply.
    if (!policy.blocked.includes(roomId) && !policy.blockedBy.includes(roomId)) {
      window.setTimeout(() => {
        const current = safety();
        if (
          getUserId() !== user ||
          current.blocked.includes(roomId) ||
          current.blockedBy.includes(roomId) ||
          !getRooms().some((item) => item.id === roomId)
        )
          return;
        const reply: Message = {
          id: crypto.randomUUID(),
          sender: 'partner',
          content: '좋아요! 조금 더 이야기 나눠요 😊',
          sentAt: new Date().toISOString(),
        };
        appendMessage(roomId, reply);
        connections.get(roomId)?.forEach((receive) => receive(reply));
      }, 900);
    }
    return HttpResponse.json(message);
  }),
  http.post('/api/mock/chat/:id/:action', async ({ params, request }) => {
    const roomId = Number(params.id);
    if (!getRooms().some((room) => room.id === roomId))
      return fail('채팅방을 찾을 수 없어요.', 404);
    const current = safety();
    if (params.action === 'block') {
      current.blocked = [...new Set([...current.blocked, roomId])];
      const partnerUserId = getRooms().find((room) => room.id === roomId)!.partnerUserId;
      current.excludedPartnerIds = [
        ...new Set([...(current.excludedPartnerIds ?? []), partnerUserId]),
      ];
    } else if (params.action === 'report') {
      const body = (await request.json()) as { type: string; detail: string };
      if (
        !reportTypes.some(([type]) => type === body.type) ||
        typeof body.detail !== 'string' ||
        body.detail.length > 1000
      )
        return fail('신고 내용을 확인해주세요.');
      if (body.type === 'ETC' && !body.detail.trim())
        return fail('기타 신고의 상세사유를 입력해주세요.');
      current.reports.push({
        roomId,
        ...body,
        queue: ['FRAUD', 'SEXUAL'].includes(body.type) ? 'IMMEDIATE_REVIEW' : 'STANDARD_REVIEW',
      });
    } else if (params.action !== 'leave') return fail('지원하지 않는 요청이에요.', 404);
    sessionStorage.setItem(safetyKey(), JSON.stringify(current));
    return HttpResponse.json({ success: true });
  }),
];
