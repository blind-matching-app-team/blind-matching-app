# WebSocket 실시간 채팅 인프라 — BMA-73

## 1. 기술 결정: STOMP over WebSocket (SockJS 폴백 유지)

순수 WebSocket 대신 **STOMP** 를 쓴다. 결정 근거:

| 항목 | STOMP (채택) | 순수 WebSocket |
| --- | --- | --- |
| 방 단위 구독/발행 | 프로토콜에 내장(`SUBSCRIBE /topic/chat/{roomId}`) | 메시지 라우팅·구독 관리·ACK 를 전부 직접 설계 |
| 인증/인가 지점 | 프레임 단위 인터셉터(CONNECT 에서 JWT, SUBSCRIBE/SEND 마다 방 권한) | 핸드셰이크 시점 1회 + 메시지마다 수동 검사 |
| 개인 푸시 | `/user/queue/...` 사용자 목적지(매칭 대기 결과 S9, 숨김 메시지 에코) | 세션↔사용자 매핑 직접 관리 |
| 하트비트/재연결 | 표준 `heart-beat` 협상, 클라이언트 라이브러리(`@stomp/stompjs`)가 자동 재연결 지원 | ping/pong·백오프 직접 구현 |
| 브로커 확장 | 인스턴스가 늘면 `enableStompBrokerRelay`(RabbitMQ 등)로 교체만 하면 됨 | Redis pub/sub 등 별도 설계 |
| 비용 | 프레임 헤더 수십 바이트 오버헤드 | 최소 |

채팅·매칭 푸시·(추후) 알림 푸시가 모두 "사용자/방 단위 구독" 모델이라 STOMP 의 이점이 크고, 이미 BMA-66 매칭 대기 푸시가 `/user/queue/matching` 으로 동작 중이다.
SockJS 폴백은 유지한다(회사망·구형 프록시에서 WebSocket 업그레이드가 막힐 때 xhr-streaming 으로 자동 전환).

## 2. 방화벽·배포 (오라클 클라우드 VM, BMA-33)

**별도 포트 개방이 필요 없다.** WebSocket 은 REST 와 같은 HTTP(S) 포트(8080 → nginx 80/443)에서 `Upgrade: websocket` 으로 승격되므로,
이미 열린 80/443 만 있으면 된다. 확인할 것은 두 가지다.

1. 오라클 보안 목록/NSG 와 VM 의 iptables 에 80/443 이 열려 있는지 — REST 가 되면 이미 열린 것.
2. nginx 가 업그레이드 헤더를 넘기는지(없으면 SockJS 가 xhr-streaming 으로 폴백해 동작은 하지만 지연이 는다):

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 120s;   # 하트비트 10초보다 충분히 크게. 기본 60초는 idle 연결을 끊을 수 있다
}
```

- `WS_ALLOWED_ORIGINS` 에 프론트 도메인을 넣는다(기본 `http://localhost:3000,http://localhost:5173`).
- HTTPS 면 프론트는 `wss://` 로 붙는다. 인증서는 nginx 에서 종료하므로 앱 설정 변경 없음.

## 3. 채널 설계

| 목적지 | 방향 | 용도 | 권한 |
| --- | --- | --- | --- |
| `/ws` (SockJS) · `/ws/websocket` (원시) | 핸드셰이크 | 엔드포인트 | Origin 검사 |
| `CONNECT` 헤더 `Authorization: Bearer <access>` | C→S | JWT 인증. 리프레시 토큰 거부 | 없으면 ERROR·세션 종료 |
| `/app/chat/{roomId}/send` | C→S | 메시지 전송 `{messageType?, content, replyMessageId?}` | 방 참여자 |
| `/topic/chat/{roomId}` | S→C | 방 브로드캐스트(`MessageResponse`) | 방 참여자만 구독 가능 |
| `/user/queue/chat` | S→C | 차단 상대에게 보낸 숨김 메시지의 발신자 에코(S11-08) | 본인 |
| `/user/queue/matching` | S→C | 매칭 대기 결과(S9, BMA-66) | 본인 |

발신자는 항상 세션 주체다(페이로드에 발신자 필드 없음). REST `POST /chat/rooms/{id}/messages` 도 저장 후 같은 토픽으로 브로드캐스트하므로 두 경로가 섞여도 순서·중복 문제가 없다(모든 메시지에 `messageId`).

## 4. 하트비트

서버 `10000,10000`(ms). 클라이언트는 CONNECT 에 `heart-beat:10000,10000` 을 보내야 협상된다(`@stomp/stompjs` 는 `heartbeatIncoming/Outgoing` 옵션).
협상되면 서버가 10초마다 빈 프레임을 보내고, 클라이언트 프레임이 10초×3 동안 없으면 서버가 세션을 닫는다.
모바일 백그라운드·NAT 타임아웃·프록시 idle 로 죽은 연결을 양쪽이 감지해 재연결로 넘어가게 하는 장치다.

## 5. 재연결 (클라이언트 규약)

서버는 상태를 갖지 않는다(세션이 끊기면 구독도 사라짐). 클라이언트가 할 일:

1. `onWebSocketClose`/에러 → 지수 백오프(1s → 2s → 4s … 최대 30s)로 `CONNECT` 재시도. 토큰이 만료됐으면 리프레시 후 재시도(`AUTH_00x` 로 ERROR 프레임을 받으면 즉시 갱신).
2. `CONNECTED` 후 **구독을 다시** 건다(`/topic/chat/{roomId}`, `/user/queue/chat`, `/user/queue/matching`).
3. 끊긴 사이 메시지는 `GET /chat/rooms/{roomId}/messages?page=0&size=30` 으로 받아 마지막으로 표시한 `messageId` 보다 큰 것만 붙인다(최신순이므로 필요하면 다음 페이지).
4. 전송 중 끊긴 메시지는 REST `POST /chat/rooms/{id}/messages` 로 재전송해도 된다(같은 토픽으로 브로드캐스트).

```ts
const client = new Client({
  webSocketFactory: () => new SockJS(`${API}/ws`),
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
  heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
  reconnectDelay: 2000,                       // stompjs 가 자동 재연결
  onConnect: () => { subscribeRoom(roomId); subscribeEcho(); catchUp(roomId, lastMessageId); },
});
```

## 6. 저장 + 푸시 흐름

`SEND` → 인터셉터(세션 주체·방 권한) → `ChatSocketController` → `ChatService.sendMessage`(트랜잭션: 저장, 방 마지막 메시지, 발신자 읽음, Reveal 진행, 상대 알림)
→ `ChatMessagePublisher`(일반: `/topic/chat/{roomId}`, 숨김: 발신자 `/user/queue/chat`) → 구독자 즉시 수신.

## 7. 검증

- 단위/통합: `ChatStompIntegrationTest` — 내장 톰캣에 실제 `WebSocketStompClient` 2개로 접속. 토큰 없음/오류/리프레시 거부, 하트비트 협상, 두 클라이언트 실시간 교환, 비참여 방 구독 ERROR, 숨김 메시지 발신자 전용 에코, 재접속 후 수신 복구.
- 종단(컨테이너, DB 포함): `backend/scripts/verify-ws-chat.py` — 표준 라이브러리로 RFC 6455 프레이밍을 구현해 `/ws/websocket` 에 붙는다. 12 검증(인증, 하트비트, STOMP↔REST 교차 수신, DB 저장 확인, 비참여자 SUBSCRIBE/SEND 거부, 재접속·이력 따라잡기, 서버 하트비트 수신, 차단 후 S11-08).

```bash
BASE=http://localhost:8080 python3 backend/scripts/verify-ws-chat.py
```

## 8. 확장 시 고려

- 앱 인스턴스가 2대 이상이면 심플 브로커를 `enableStompBrokerRelay("/topic","/queue")` + RabbitMQ(STOMP 플러그인)로 바꾼다. 클라이언트 프로토콜은 그대로.
- 메시지 유형 `IMAGE` 는 업로드 API(BMA-41 저장소)로 키를 받은 뒤 `content` 에 키를 실어 보내는 방식으로 확장한다.
