# 매칭 상태 API 계약 (S5 메인 허브)

BMA-47 · S5 사양서 v1.7 기준. 프론트(BMA-46) MSW 목은 이 문서를 따른다.

S5 는 "진행 중인 매칭이 있으면 상대 카드, 없으면 매칭 시작 CTA" 두 상태다. 카드(S5-09)에는
상대의 블러 아바타·"???"·MBTI·지역·공통관심사가, 그 아래 Reveal 진행바(S5-10)와
대화 시작하기(S5-11), 매칭 그만두기(S5-16)가 있다. 이 API 는 그걸 그리는 데 필요한 것을 전부 준다.

관련 결정
- **S5-09 [v1.7]**: 카드 클릭은 S10(상세), 대화 시작하기 버튼은 S11(채팅). 백엔드는 둘 다 `matchId`/`chatRoomId` 로 연결한다.
- **S5-16**: 매칭 그만두기는 신고·차단과 다른 완충 수단. 상대에게는 종료 알림만 가고 사유·주체는 비공개.
- **S5-12 재매칭권 보유 개수**: 이용권/결제(BMA-84) 범위라 이 API 에 없다.
- 사이드바 안읽음 배지(S5-03, S5-04)는 티켓 범위 밖 — BMA-50(채팅목록), BMA-53(알림)에서 처리.

---

## 1. 현재 매칭 조회 (S5 가 쓰는 것)

```
GET /api/v1/matches/current      (인증 필요)
```

가장 최근 진행 중인 매칭 1건. **매칭 없음은 오류가 아니라 `hasMatch=false`** 로 200 이다.

### 매칭 없음

```json
{ "success": true, "code": "SUCCESS", "message": "성공",
  "data": { "hasMatch": false, "match": null } }
```

`match` 키는 값이 `null` 이어도 항상 존재한다(이 응답만 전역 non_null 설정을 끈다).

### 매칭 있음

```json
{
  "success": true, "code": "SUCCESS", "message": "성공",
  "data": {
    "hasMatch": true,
    "match": {
      "matchId": 12,
      "partnerUserId": 34,
      "matchStatus": "ACTIVE",
      "matchType": "LIKE",
      "matchDate": "2026-09-18T14:02:11.123456",
      "revealLevel": 0,
      "chatRoomId": 7,
      "partner": {
        "userId": 34,
        "revealLevel": 0,
        "nickname": null,
        "age": null,
        "ageGroup": "20대 후반",
        "genderCode": "F",
        "regionCode": "SEOUL",
        "regionName": "서울특별시",
        "mbtiCode": "INFP",
        "occupation": null,
        "heightCm": null,
        "introduction": null,
        "imageKeys": []
      },
      "commonInterests": ["문화생활", "영화"],
      "reveal": {
        "currentLevel": 0,
        "currentLevelName": "미공개",
        "nextLevel": 1,
        "nextLevelName": "실루엣 및 부분 공개",
        "messageCount": 3,
        "requiredMessageCount": 20,
        "chatMinutes": 1,
        "requiredChatMinutes": 10,
        "progressRate": 10,
        "mutualConsentNeeded": false
      }
    }
  }
}
```

| 필드 | 카드 오브젝트 | 설명 |
|---|---|---|
| `partner` | S5-09 | 현재 공개 단계로 마스킹된 상대 프로필. 단계 0/1 에서는 `nickname=null` → 화면에 "???". 지역은 전체 공개 전까지 시/도(`regionName` 그대로 표시) |
| `partner.imageKeys` | S5-09 블러 아바타 | 단계 0 은 실루엣 키, 1 은 블러 키, 2 는 원본 키. 가공 이미지가 없으면 빈 배열 → 기본 아바타 |
| `commonInterests` | S5-09 공통관심사 | 온보딩 관심사 문항에서 양쪽이 함께 고른 보기 이름. 대분류·세부 모두 포함, 보기 정렬 순서. 없으면 `[]` |
| `reveal.progressRate` | S5-10 진행바 | 다음 단계까지 0~100. 메시지 수·대화 시간 중 **덜 채워진 쪽** 기준. 최고 단계면 100 |
| `chatRoomId` | S5-11 | 대화 시작하기 → S11 |
| `matchId` | S5-09 클릭, S5-16 | 카드 클릭 → S10, 그만두기 → 3절 |

Reveal 단계 상승 조건(24h + 각자 10개 + 상호동의, BMA-19 안건1)은 BMA-69 에서 정책 데이터를 바꾸면
`requiredMessageCount`/`requiredChatMinutes`/`progressRate` 가 그대로 따라온다. 계약은 바뀌지 않는다.

---

## 2. 매칭 목록 조회

```
GET /api/v1/matches      (인증 필요)
```

진행 중인 매칭 전부, 최신순. 항목 모양은 1절의 `match` 와 같다. 없으면 `data: []`.
S5 는 1절을 쓰고, 이 API 는 여러 매칭을 다루는 화면(S6 채팅목록 등)이 쓴다.

---

## 3. 매칭 그만두기 (S5-16)

```
DELETE /api/v1/matches/{matchId}      (인증 필요)
```

- 매칭을 `UNMATCHED` 로 종료하고 채팅방을 닫는다. 이후 `/matches/current` 는 `hasMatch=false`.
- 상대에게 알림 1건: 제목 "매칭이 종료되었어요" (누가·왜는 담지 않는다).
- 이미 종료된 매칭에 다시 호출해도 200 (알림 중복 없음).

| HTTP | code | 언제 |
|---|---|---|
| 404 | `MATCH_001` | 내가 참여자가 아니거나 없는 매칭. 존재 여부를 드러내지 않기 위해 둘 다 404 |
| 401 | `AUTH_001` | 토큰 없음/만료 |

---

## 4. 검증하기

```bash
docker compose up -d --build
```

Postman 컬렉션 `postman/BMA-47-match.postman_collection.json` 을 Runner 로 순서대로 실행한다.
newman/node 가 없으면:

```bash
BASE=http://localhost:8080 bash backend/scripts/verify-match.sh
```

(Windows Git Bash 는 `LANG=C.UTF-8` 을 앞에 붙인다.)

메시지 전송은 WebSocket(STOMP) 전용이라 REST 검증에서는 `messageCount=0`, `progressRate=0` 까지만 본다.
진행바 계산 자체는 단위 테스트(`RevealPolicyProgressTest`)로 고정했다.

---

## 5. 확정 필요

- **재매칭권 보유 개수(S5-12)** — BMA-84 결제 API 에서 별도 제공.
- **가공 이미지(실루엣/블러) 생성** — 업로드 시 원본만 저장한다. 생성 파이프라인이 없어 단계 0/1 의 `imageKeys` 는 지금 빈 배열이다. 프론트는 빈 배열이면 기본 아바타를 쓴다.
- **BMA-12 5장 `GET /matches/{matchId}` 상세** — S10 범위(BMA-69).
