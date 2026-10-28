import { http, HttpResponse } from 'msw';
import { getUserId } from '../lib/auth';
import { getRooms } from '../lib/chatStore';
import type { RevealStage, RevealState } from '../api/reveal';
import { endMatch } from './matching';

// BMA-19: 24 hours after matching + 20 messages, then mutual consent.
const REQUIRED_MS = 24 * 60 * 60 * 1000;
const REQUIRED_MESSAGES = 20;
const DEFAULT_TICKETS = 2;

// Scenarios: stage1 (default, unmet) | stage1-met | consent | stage2 | no-ticket
type MockReveal = {
  stage: RevealStage;
  matchedAt: number;
  bonusMessages: number;
  requestedByMe: boolean;
  requestedByPartner: boolean;
};
const profiles = [
  { mbti: 'ENFP', region: '서울 마포구', commonInterestCount: 3, height: 165 },
  { mbti: 'ISTJ', region: '경기 성남시', commonInterestCount: 2, height: null },
  { mbti: 'INFJ', region: '부산 해운대구', commonInterestCount: 4, height: 178 },
];
const scenario = () => sessionStorage.getItem('bma-reveal-scenario') || 'stage1';
const roomKey = (roomId: number) => `bma-reveal-${getUserId()}-${roomId}`;
const ticketKey = () => `bma-rematch-tickets-${getUserId()}`;
const fail = (message: string, status = 400, code?: string) =>
  HttpResponse.json({ message, code }, { status });
const notFound = () => fail('종료되었거나 존재하지 않는 매칭이에요.', 404, 'MATCH_NOT_FOUND');

function readTickets() {
  const stored = sessionStorage.getItem(ticketKey());
  if (stored !== null) return Number(stored);
  return scenario() === 'no-ticket' ? 0 : DEFAULT_TICKETS;
}

function readRoom(roomId: number): MockReveal {
  const stored = sessionStorage.getItem(roomKey(roomId));
  if (stored) return JSON.parse(stored);
  const current = scenario();
  const met = current === 'stage1-met' || current === 'consent';
  return {
    stage: current === 'stage2' ? 2 : 1,
    // Unmet default mirrors the spec example: 18h 12m and 7 messages to go.
    matchedAt: met ? Date.now() - REQUIRED_MS - 60_000 : Date.now() - (5 * 60 + 48) * 60_000,
    bonusMessages: met ? REQUIRED_MESSAGES : 10,
    requestedByMe: false,
    requestedByPartner: current === 'consent',
  };
}

function writeRoom(roomId: number, value: MockReveal) {
  sessionStorage.setItem(roomKey(roomId), JSON.stringify(value));
}

function maskName(name: string, stage: RevealStage) {
  if (stage === 3) return name;
  return stage === 2 ? `${name.slice(0, 2)}??` : '???';
}

function view(roomId: number): RevealState | null {
  const room = getRooms().find((item) => item.id === roomId && !item.isClosed);
  if (!room) return null;
  const reveal = readRoom(roomId);
  const profile = profiles[(roomId - 1) % profiles.length];
  const messages = room.messages.length + reveal.bonusMessages;
  return {
    roomId,
    stage: reveal.stage,
    partner: {
      displayName: maskName(room.partnerName, reveal.stage),
      mbti: profile.mbti,
      region: profile.region,
      commonInterestCount: profile.commonInterestCount,
      height: reveal.stage >= 2 ? profile.height : null,
      photoUrl: null,
    },
    nextStage:
      reveal.stage === 3
        ? null
        : {
            remainingMinutes: Math.max(
              0,
              Math.ceil((reveal.matchedAt + REQUIRED_MS - Date.now()) / 60_000),
            ),
            remainingMessages: Math.max(0, REQUIRED_MESSAGES - messages),
            requestedByMe: reveal.requestedByMe,
            requestedByPartner: reveal.requestedByPartner,
          },
    rematchTickets: readTickets(),
  };
}

const respond = (data: RevealState) => HttpResponse.json({ data });

export const revealHandlers = [
  http.get('/api/mock/reveal/:roomId', ({ params }) => {
    const data = view(Number(params.roomId));
    return data ? respond(data) : notFound();
  }),
  http.post('/api/mock/reveal/:roomId/request', ({ params }) => {
    const roomId = Number(params.roomId);
    const data = view(roomId);
    if (!data?.nextStage) return fail('요청할 수 없는 매칭이에요.', 404);
    if (data.nextStage.remainingMinutes || data.nextStage.remainingMessages)
      return fail('아직 다음 단계 조건을 채우지 못했어요.', 409);
    writeRoom(roomId, { ...readRoom(roomId), requestedByMe: true });
    return respond(view(roomId)!);
  }),
  http.post('/api/mock/reveal/:roomId/consent', ({ params }) => {
    const roomId = Number(params.roomId);
    const reveal = readRoom(roomId);
    if (!view(roomId) || !reveal.requestedByPartner || reveal.stage === 3)
      return fail('동의할 요청이 없어요.', 409);
    writeRoom(roomId, {
      ...reveal,
      stage: (reveal.stage + 1) as RevealStage,
      requestedByMe: false,
      requestedByPartner: false,
    });
    return respond(view(roomId)!);
  }),
  http.post('/api/mock/reveal/:roomId/rematch', ({ params }) => {
    const roomId = Number(params.roomId);
    if (!view(roomId)) return notFound();
    const tickets = readTickets();
    if (tickets <= 0) return fail('재매칭권이 없어요.', 402, 'NO_REMATCH_TICKET');
    sessionStorage.setItem(ticketKey(), String(tickets - 1));
    endMatch(roomId);
    return HttpResponse.json({ data: { remainingTickets: tickets - 1 } });
  }),
];
