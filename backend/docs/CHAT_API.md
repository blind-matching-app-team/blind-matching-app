# S11 채팅 REST API — BMA-72

S11 사양서 v1.4(2026-08-22) 와 BMA-30 신고 정책 기준. 채팅방 목록(S6)은 `CHAT_ROOM_API.md`,
실시간 송수신(WebSocket)은 `WEBSOCKET.md` 를 본다.

## 1. 요약

| 화면 요소 | API |
| --- | --- |
| S11-06 메시지 리스트(과거 메시지 무한스크롤) | `GET /api/v1/chat/rooms/{roomId}/messages?page=&size=` |
| S11-07/08 전송(REST 대체 경로) | `POST /api/v1/chat/rooms/{roomId}/messages` — 실시간은 STOMP `/app/chat/{roomId}/send` |
| S11-09 신고하기 → S11-10~12 신고 모달 | `POST /api/v1/reports` |
| S11-09 차단하기 | `POST /api/v1/users/{id}/block` (기존, 동작 변경) |
| S11-09 채팅방 나가기 | `DELETE /api/v1/chat/rooms/{roomId}` (BMA-50) |
| 읽음 처리 | `PUT /api/v1/chat/rooms/{roomId}/read?messageId=` |

모든 엔드포인트는 액세스 토큰 필수. 응답은 공통 `ApiResponse{success, code, message, data, timestamp}`.

## 2. 메시지 이력 — `GET /chat/rooms/{roomId}/messages`

| 파라미터 | 기본 | 설명 |
| --- | --- | --- |
| `page` | 0 | 0부터. 최신 메시지가 0페이지 |
| `size` | 30 | 1~100. 넘기면 100 으로 절삭 |

```json
{
  "content": [
    {"messageId": 512, "chatRoomId": 31, "senderUserId": 9, "messageType": "TEXT",
     "content": "주말에 뭐 하세요?", "replyMessageId": null, "sendDate": "2026-09-23T21:10:03.123"}
  ],
  "page": 0, "size": 30, "totalElements": 5, "totalPages": 1, "last": true
}
```

- `content` 는 **ID 내림차순(최신순)**. 무한스크롤은 `page` 를 올리며 위로 붙인다.
- 재접속 후 따라잡기: 마지막으로 받은 `messageId` 보다 큰 항목만 붙이면 된다(`WEBSOCKET.md` 5절).
- 참여자가 아니거나 없는 방 → `403 CHAT_002`(존재 여부 비노출). 미인증 → 401.
- 차단 상대에게 보낸 **숨김 메시지는 발신자 본인에게만** 포함된다(4절).

## 3. 신고 — `POST /reports`

```json
{"targetUserId": 12, "reportType": "ABUSE", "description": "욕설을 했어요", "matchId": 44, "messageId": 512}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `targetUserId` | O | 피신고자 |
| `reportType` | O | `ABUSE` 욕설 / `FAKE` 허위프로필 / `FRAUD` 사기 / `SEXUAL` 부적절한 콘텐츠 / `ETC` 기타 (S11-10, BMA-30 5종) |
| `description` | X | 상세 사유 ≤2000자 (S11-11) |
| `matchId` | X | 생략하면 두 사람의 매칭을 찾아 채운다. 지정하면 두 사람의 매칭이어야 한다(아니면 400) |
| `messageId` | X | 지정하면 피신고자가 보낸 메시지여야 한다(아니면 400) |

응답(`data`):

```json
{"reportId": 77, "targetUserId": 12, "reportType": "FRAUD", "severity": "SEVERE", "status": "PENDING_REVIEW",
 "immediateReview": true, "matchId": 44, "chatRoomLeft": true, "reportedAt": "2026-09-23T21:12:40.5"}
```

| 필드 | 의미 |
| --- | --- |
| `severity` | `NORMAL`(욕설·허위프로필·기타, 누적 기반) / `SEVERE`(사기·부적절한 콘텐츠) |
| `status` | `COUNTED` 자동 반영(일반 유형 누적 1~2회) / `PENDING_REVIEW` 관리자 검토 대기(일반 3회째부터, 또는 중대 유형) |
| `immediateReview` | 중대 유형이라 즉시 관리자 검토 큐(S12)로 갔는지. 완료 토스트 문구 분기용 |
| `chatRoomLeft` | 신고로 **신고자만** 채팅방에서 나갔는지. 상대는 무변화 |

BMA-30 정책 반영:

- **누적**: 피신고자 기준 `COUNTED`/`RESOLVED`(관리자 유효 판정) 건수가 2 이상이면 다음 일반 신고는 `PENDING_REVIEW`. 리셋 없음.
- **중대 유형**은 누적과 무관하게 1회로 `PENDING_REVIEW`. 이 상태인 피신고자는 앱은 평소처럼 쓰지만 **새 매칭에 들어갈 수 없다**:
  대기열 진입 `POST /matching/queue`, 재매칭 `POST /matches/{id}/rematch`, 좋아요 `POST /matching/actions` → `409 MATCH_007`.
  이미 대기 중이던 항목도 짝을 맺지 않는다. 일반 유형 3회째(`PENDING_REVIEW` 이지만 `NORMAL`)는 매칭을 막지 않는다.
- **중복**: 같은 신고자→같은 대상은 **매칭 1건당 1회** → `409 SAFE_001`. 매칭이 없는 신고(추천 화면 등)는 24시간 창.
- **채팅방**: 신고자만 나간다(S6-11 나가기와 같은 처리). 상대가 새 메시지를 보내면 방은 다시 나타난다. 다시 보고 싶지 않으면 차단을 함께 쓴다.
- 관리자 검토·조치(경고/7일 제한/영구 차단, 감형, "관리자에 의해 대화 종료됨" 안내)는 별도 티켓(관리자 API/화면).

오류: `400 COMMON_001`(유형·근거 오류, 대상 누락) · `400 MATCH_001`(자기 자신) · `404 USER_001` · `409 SAFE_001`.

구 경로 `POST /users/{id}/report`(본문에 `targetUserId` 없음)도 같은 처리로 유지한다.

## 4. 차단과 S11-08 (숨김 메시지)

`POST /users/{id}/block` 의 동작을 BMA-30·S11-08 에 맞췄다.

| | 차단자 | 차단당한 사람 |
| --- | --- | --- |
| 채팅방 | 나간다(목록에서 사라짐, 전송 403 `CHAT_002`) | 무변화 |
| 매칭 | 목록·현재 매칭에서 사라짐(매칭 행은 `ACTIVE` 그대로) | 무변화(목록·상세·Reveal 그대로) |
| 메시지 전송 | 불가(방을 나갔으므로) | **200, 정상 전송처럼 보임** — 저장은 되지만 `HIDDEN_YN=Y` |
| 숨김 메시지 노출 | 이력·미리보기·안읽음·알림·`/topic` 브로드캐스트 모두 제외 | 본인 이력·미리보기에 보임, `/user/queue/chat` 로 에코 |
| 매칭 알고리즘 | 서로 영구 제외(기존) | |

- 차단자는 상대의 숨김 메시지로 **재입장하지 않는다**(나가기 후 새 메시지 재입장 규칙의 예외).
- 차단 해제는 방을 되돌리지 않는다. 해제 후 상대가 보내는 메시지부터 정상 전달되고 그때 방이 다시 나타난다. 이때도 해제 전 숨김 메시지는 차단자에게 보이지 않는다.
- 응답 `BlockResult{targetUserId, blocked, chatRoomLeft}` — 기존 `matchEnded` 는 없어졌다(차단이 매칭을 끝내지 않으므로).

## 5. 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| `CHAT_002` | 403 | 참여자가 아니거나 없는 방, 차단 후 나간 방에 전송 |
| `CHAT_003` | 409 | 종료된 방(매칭 그만두기·재매칭)에 전송 |
| `SAFE_001` | 409 | 같은 매칭 재신고(매칭 없으면 24시간 내 재신고) |
| `MATCH_007` | 409 | 즉시검토 대기 중 새 매칭 진입 |
| `MATCH_001` | 400 | 자기 자신 신고·차단 |
| `USER_001` | 404 | 없는 사용자 |

## 6. 스키마 (V13)

- `CH_CHAT_MESSAGE.HIDDEN_YN CHAR(1) DEFAULT 'N'` — 숨김 메시지.
- `SF_USER_REPORT.SEVERITY VARCHAR(10) DEFAULT 'NORMAL'`, `REPORT_STATUS` 기본값 `COUNTED`(구 `RECEIVED` 는 `COUNTED` 로 이관).
  상태: `COUNTED` / `PENDING_REVIEW` / `RESOLVED` / `REJECTED`.

## 7. 검증

Postman: `postman/BMA-72-chat.postman_collection.json` (Runner 순서 실행). curl:

```bash
BASE=http://localhost:8080 bash backend/scripts/verify-chat.sh        # REST 16단계
BASE=http://localhost:8080 python3 backend/scripts/verify-ws-chat.py  # WebSocket 12검증 (표준 라이브러리만)
```

## 8. 결정 사항 (티켓에 없어 정한 것)

- 신고 시 채팅방 처리는 "나가기"와 동일(재입장 가능). 신고와 동시에 영구 차단을 원하면 차단을 같이 호출한다.
- 매칭이 없는 신고의 중복 창 24시간은 기존 값 유지.
- 새 매칭 진입 차단 범위: 대기열·재매칭·좋아요(성사 경로). 이미 성사된 매칭의 대화는 그대로 가능("앱은 평소처럼").
- 차단이 매칭을 종료하지 않게 바꿨다(차단당한 쪽 화면 무변화가 우선). 기존 `BLOCKED` 매칭 종료 코드는 관리자 조치용으로 남긴다.
