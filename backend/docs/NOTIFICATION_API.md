# 알림 API 계약 (S7)

BMA-53 · S7 사양서 v1.7 기준. 프론트(BMA-52) MSW 목은 이 문서를 따른다.

관련 결정
- **S7-09**: 알림 클릭 시 유형별 화면으로 이동. 유형별 이동 대상은 "확정 필요"로 남아 있었다 → 이 문서 4절의 표로 확정 제안.
- **S7-11~14 (v1.3~v1.7)**: 미인증 상대 / 다음 단계 요청 도착 / 경고 조치 / 매칭 종료 알림 신설. 사건 단위 코드(`eventCode`)로 구분한다.
- **BMA-53 제약**: 이 티켓은 조회/읽음처리. 알림 생성은 각 도메인이 담당 — 현재 생성되는 사건은 4절 참고.

---

## 1. 알림 목록

```
GET /api/v1/notifications?page=0&size=20&unreadOnly=false      (인증 필요)
```

최신순 페이지. 없으면 `content: []` (S7-10 빈 상태).

```json
{
  "success": true, "code": "SUCCESS", "message": "성공",
  "data": {
    "content": [
      {
        "notificationId": 41,
        "type": "REVEAL",
        "eventCode": "REVEAL_REQUESTED",
        "title": "???님이 다음 단계를 요청했어요",
        "content": "'실루엣 및 부분 공개' 단계 공개에 동의하면 서로의 정보가 더 공개됩니다.",
        "target": { "screen": "S10", "matchId": 12, "chatRoomId": null, "userId": null },
        "referenceType": "MATCH",
        "referenceId": 12,
        "read": false,
        "readDate": null,
        "createdDate": "2026-09-18T20:31:02.114"
      }
    ],
    "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "last": true
  }
}
```

| 필드 | S7 오브젝트 | 설명 |
|---|---|---|
| `eventCode` | S7-09 아이콘·문구, S7-11~14 구분 | 사건 코드. 4절 표 참고. 프론트 아이콘/색은 이 값 기준 |
| `type` | 아이콘 색 그룹 | 큰 분류 MATCH/MESSAGE/REVEAL/REPORT/SYSTEM (`eventCode` 에서 파생) |
| `title` / `content` | S7-09 알림 문구 | 제목은 사양서 문구 그대로. `content` 는 부가 설명(메시지 미리보기 등), 없으면 `null` |
| `target` | S7-09 클릭 이동 | `screen` 으로 라우트, `matchId`/`chatRoomId`/`userId` 를 경로에 사용. 이동 없음이면 `screen=null` |
| `read` | S7-09 안읽음 점 | |
| `createdDate` | S7-09 시간 | 프론트가 "2시간 전" 으로 가공 |

값이 없는 필드도 `null` 로 항상 존재한다.

---

## 2. 안읽음 수 (사이드바 알림 배지)

```
GET /api/v1/notifications/unread-count      (인증 필요)
→ { "unreadCount": 3 }
```

---

## 3. 읽음 처리

```
PUT /api/v1/notifications/{notificationId}/read      (인증 필요)
→ { "notificationId": 41, "readAt": "2026-09-18T20:40:11.3", "unreadCount": 2 }
```
- 본인 알림만. 이미 읽은 알림이면 처음 읽은 시각 그대로 200 (멱등).
- 남의 알림 / 없는 ID → 404 `COMMON_404` (존재 여부를 드러내지 않기 위해 같은 코드).

```
PUT /api/v1/notifications/read-all      (인증 필요)    ← S7-08 "전체 읽음"
→ { "updatedCount": 4, "unreadCount": 0 }
```
- 안 읽은 것만 바뀐다. 다시 호출하면 `updatedCount: 0`.

---

## 4. 사건 코드와 이동 화면 (S7-09 "유형별 이동 대상 화면" 확정 제안)

| `eventCode` | S7 | 제목(사양서 문구) | 발생 시점 | `target.screen` | 발행 상태 |
|---|---|---|---|---|---|
| `MATCH_LIKED` | — | 누군가 회원님에게 호감을 보냈어요 | 한쪽만 좋아요 | S5 (`userId`) | 발행 중 |
| `MATCH_CREATED` | — | 매칭이 성사되었어요! | 상호 매칭 | S10 (`matchId`) | 발행 중 |
| `MESSAGE_RECEIVED` | — | 새 메시지가 도착했어요 (content = 미리보기) | 상대 메시지 | S11 (`chatRoomId`) | 발행 중 |
| `REVEAL_REQUESTED` | **S7-12** | ???님이 다음 단계를 요청했어요 | 상대가 다음 단계 동의/요청 | S10 (`matchId`), 동의 모달 자동 오픈 | 발행 중 (BMA-69 요청 API 추가 시 그대로 사용) |
| `REVEAL_LEVEL_UP` | — | 프로필 공개 단계가 올라갔어요 | 단계 상승 | S10 (`matchId`) | 발행 중 |
| `MATCH_ENDED` | **S7-14** | 매칭이 종료됐어요 | S5-16 그만두기 (상대에게) | S5 | 발행 중 |
| `MATCH_UNVERIFIED_PARTNER` | **S7-11** | 상대방은 아직 사진 인증을 완료하지 않았어요 | 매칭 성사 직후, 상대 사진인증 미완료 | S10 (`matchId`) | **코드만 정의** — 사진인증(BMA-82) 이후 발행 |
| `WARNING_ISSUED` | **S7-13** | 안전한 이용을 위해 경고 조치가 적용됐어요… | 관리자 경고 조치 | `null` (상세 화면 추후/고객센터) | **코드만 정의** — 신고검토(BMA-76) 이후 발행 |
| `SYSTEM` | — | (공지) | — | `null` | V9 이전 행·모르는 코드의 대체값 |

코드 문자열은 프론트 계약이므로 바꾸지 않는다. 새 사건은 이 표에 추가한다.

---

## 5. 스키마 (V9)

- `NT_NOTIFICATION.EVENT_CODE VARCHAR(40) NULL` 추가. 기존 행은 제목으로 백필, 남은 null 은 응답에서 `SYSTEM`.

---

## 6. 검증하기

```bash
docker compose up -d --build
```

Postman 컬렉션 `postman/BMA-53-notifications.postman_collection.json` 을 Runner 로 순서대로 실행한다.
newman/node 가 없으면:

```bash
BASE=http://localhost:8080 bash backend/scripts/verify-notifications.sh
```

(Windows Git Bash 는 `LANG=C.UTF-8` 을 앞에 붙인다.)

---

## 7. 확정 필요

- **S7-11 / S7-13 발행** — 사진인증(BMA-82), 신고검토(BMA-76)가 없어 코드만 있고 발행되지 않는다. 각 티켓에서 `NotificationEvent` 로 발행하면 된다.
- **결제 알림** — BMA-52 제약대로 스코프 밖(S8-09). 필요해지면 `PAYMENT_*` 코드를 4절에 추가.
- **호감 받음(`MATCH_LIKED`) 이동 화면** — 사양서에 없는 알림이라 S5 로 뒀다. "받은 호감" 화면이 생기면 바꾼다.
