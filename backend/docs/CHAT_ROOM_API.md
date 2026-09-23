# 채팅방 목록 API 계약 (S6)

BMA-50 · S6 사양서 v1.4 기준. 프론트(BMA-49) MSW 목은 이 문서를 따른다.

관련 결정
- **BMA-49 [결정]**: 매칭이 종료돼도(그만두기·신고차단·재매칭) 채팅방을 목록에서 삭제하지 않는다. S6-12 "종료됨" 배지로 구분하고
  읽기 전용으로 남긴다 — 별도 매칭 히스토리 화면 없이 채팅목록이 그 역할을 겸한다.
- **BMA-50 [2026-08-05 갱신]**: 응답에 `status: ACTIVE | ENDED` 필드. 종료된 방은 안읽음 카운트에 포함하지 않는다.
- **S6-11**: 채팅방 나가기 = 내 목록에서만 제거(상대 목록엔 유지, 카카오톡 패턴).
- 이 API 의 안읽음 합계 = 사이드바 채팅 배지 (BMA-46).

---

## 1. 채팅방 목록

```
GET /api/v1/chat/rooms      (인증 필요)
```

내가 참여 중인 방 전부, **최근 메시지순**(메시지가 없으면 생성순). 없으면 `data: []` (S6-10 빈 상태).

```json
{
  "success": true, "code": "SUCCESS", "message": "성공",
  "data": [
    {
      "chatRoomId": 7,
      "matchId": 12,
      "partnerUserId": 34,
      "status": "ACTIVE",
      "partner": {
        "userId": 34, "revealLevel": 0, "nickname": null, "age": null, "ageGroup": "20대 후반",
        "genderCode": "F", "regionCode": "SEOUL", "regionName": "서울특별시", "mbtiCode": "INFP",
        "occupation": null, "heightCm": null, "introduction": null, "imageKeys": []
      },
      "lastMessage": "오늘 저녁에 시간 괜찮으세요?",
      "lastMessageType": "TEXT",
      "lastMessageDate": "2026-09-18T20:11:03.120456",
      "unreadCount": 3,
      "revealLevel": 0
    }
  ]
}
```

| 필드 | S6 오브젝트 | 설명 |
|---|---|---|
| `status` | S6-12 종료됨 배지 | `ACTIVE` 대화 가능 / `ENDED` 매칭 종료·읽기 전용. 프론트는 이 값으로 배지와 입력창 비활성화를 결정 |
| `partner` | S6-09 블러 아바타·"???" | 현재 공개 단계로 마스킹. 단계 0/1 은 `nickname=null` → "???", 2 는 닉네임. 이미지 키는 단계별(없으면 빈 배열 → 기본 아바타) |
| `lastMessage` | S6-09 미리보기 | 마지막 메시지 앞 50자(넘으면 `...`). 메시지 없으면 `null` |
| `lastMessageDate` | S6-09 시간 | 프론트가 "오전 9:48 / 어제 / 2일 전" 으로 가공 |
| `unreadCount` | S6-09 안읽음 배지 | 내 읽음 위치 이후 상대가 보낸 메시지 수. **`ENDED` 방은 항상 0** |
| `chatRoomId` | S6-09 클릭 → S11 | |

값이 없는 필드도 `null` 로 항상 존재한다(이 응답만 전역 non_null 설정을 끈다).

---

## 2. 안읽음 합계 (사이드바 배지)

```
GET /api/v1/chat/rooms/unread-count      (인증 필요)
→ { "unreadCount": 3 }
```

1절 목록의 `unreadCount` 합과 **항상 같다** (같은 규칙으로 센다). `ENDED` 방은 제외.

---

## 3. 채팅방 나가기 (S6-11)

```
DELETE /api/v1/chat/rooms/{roomId}      (인증 필요)
```

- 내 목록에서만 빠진다. 상대 목록에는 남고, 방·메시지는 지워지지 않는다.
- 나갈 때 내 읽음 위치를 마지막 메시지로 옮긴다.
- **상대가 새 메시지를 보내면 방이 다시 나타난다** (카카오톡 패턴). 그때 안읽음은 나간 이후 메시지만 센다.
- `ENDED` 방도 나갈 수 있다(히스토리에서 지우고 싶을 때). 종료된 방은 새 메시지가 올 수 없어 다시 나타나지 않는다.

| HTTP | code | 언제 |
|---|---|---|
| 403 | `CHAT_002` | 참여자가 아니거나 없는 방 (존재 여부를 드러내지 않기 위해 같은 코드) |

---

## 4. 메시지 전송 (REST) — 검증·폴백 경로

```
POST /api/v1/chat/rooms/{roomId}/messages      (인증 필요)
{ "messageType": "TEXT", "content": "안녕하세요" }
```

실시간 경로는 STOMP `/app/chat/{roomId}/send` 다(BMA-12 6장). 이 엔드포인트는 같은 서비스 로직을 REST 로 노출한 것이고,
저장 후 `/topic/chat/{roomId}` 로 브로드캐스트하므로 구독 중인 클라이언트도 똑같이 받는다.
목록·안읽음·Reveal 진행을 REST 만으로 검증할 수 있게 하려고 추가했다. S11(BMA-72)에서 그대로 쓸 수 있다.

| HTTP | code | 언제 |
|---|---|---|
| 409 | `CHAT_003` | `ENDED` 방(읽기 전용) |
| 403 | `CHAT_002` | 참여자가 아님 |
| 403 | 차단 코드 | 차단 관계 |

---

## 5. 기존 API (변경 없음)

- `GET /api/v1/chat/rooms/{roomId}/messages?page=&size=` — 이력(최신순). `ENDED` 방도 읽을 수 있다.
- `PUT /api/v1/chat/rooms/{roomId}/read?messageId=` — 읽음 위치 갱신. 이후 `unreadCount` 가 줄어든다.

---

## 6. 검증하기

```bash
docker compose up -d --build
```

Postman 컬렉션 `postman/BMA-50-chat-rooms.postman_collection.json` 을 Runner 로 순서대로 실행한다.
newman/node 가 없으면:

```bash
BASE=http://localhost:8080 bash backend/scripts/verify-chat-rooms.sh
```

(Windows Git Bash 는 `LANG=C.UTF-8` 을 앞에 붙인다.)

---

## 7. 확정 필요

- **재입장 규칙** — 나간 뒤 상대가 메시지를 보내면 자동으로 다시 나타나게 했다(카카오톡). 나간 방은 영구 숨김이어야 한다면 알려 달라.
- **차단 시 채팅방 상태** — BMA-72 에서 확정: 차단은 매칭을 끝내지 않고 차단자만 방에서 나간다(상대 무변화, S11-08). 차단당한 쪽의 메시지는 숨김 저장되어 차단자에게 전달되지 않는다. 상세는 `CHAT_API.md` 4절.
- **가공 이미지(실루엣/블러)** — 생성 파이프라인이 없어 단계 0/1 의 `imageKeys` 는 빈 배열(MATCH_API.md 와 동일).
