# 온보딩 API 계약 (S2)

프론트(BMA-37)가 MSW 목을 맞출 때 이 문서를 정본으로 삼는다.
공통 응답 봉투와 오류 규약은 [인증 API 계약](AUTH_API.md) 1절과 같다.

> **경로 주의**
> 티켓 초안에는 `/onboarding/questions` 로 적혀 있으나 **실제는 `/api/v1/onboarding/questions`** 다.

---

## 1. 화면 흐름과 완료 플래그

```
S1 로그인 → S2 온보딩 → S3 프로필 → 메인 허브
```

로그인/토큰 응답에 완료 플래그가 **두 개** 온다. 두 단계가 서로 독립이라
boolean 하나로는 "온보딩은 했고 프로필은 아직" 인 상태를 표현할 수 없다.

| `onboardingCompleted` | `profileCompleted` | 프론트 이동 |
| --- | --- | --- |
| `false` | — | **S2** (온보딩) |
| `true` | `false` | **S3** (프로필) |
| `true` | `true` | 메인 허브 |

- `onboardingCompleted` — 사용 중인 **필수 문항을 모두** 답했는지 (`ON_USER_ANSWER` 기준)
- `profileCompleted` — 프로필 필수 4개 항목(닉네임·생년월일·성별·지역) 충족 여부

> **질문 콘텐츠가 없으면 `onboardingCompleted` 는 `false`** 다.
> 필수 질문이 0건일 때 완료로 판정하면 신규 사용자가 설문을 건너뛴 채 매칭에 들어간다.

---

## 2. 질문 목록 조회

```
GET /api/v1/onboarding/questions      (인증 필요)
```

사용 중인(`USE_YN='Y'`) 질문을 정렬 순서대로 준다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": [
    {
      "questionId": 1,
      "questionText": "평소 관심 있는 분야를 골라주세요 (중복 가능)",
      "questionType": "MULTI",
      "categoryCode": "INTEREST",
      "required": true,
      "sortOrder": 10,
      "stepNo": 1,
      "options": [
        {
          "optionId": 1,
          "optionCode": "CULTURE",
          "optionText": "문화생활",
          "sortOrder": 1,
          "children": [
            { "optionId": 8, "optionCode": "CULTURE_MOVIE", "optionText": "영화", "sortOrder": 1, "children": [] }
          ]
        }
      ]
    }
  ]
}
```

### stepNo — 진행률의 기준

**"N/7" 은 문항 수가 아니라 구간 수 기준이다**(S2 사양서 v1.4). 문항 22개를
7구간에 재배치하며, 한 구간에 여러 문항이 들어갈 수 있다.

`stepNo` 로 문항을 묶어 화면을 구성한다. 프론트가 구간을 하드코딩하면 문항이
바뀔 때마다 화면을 고쳐야 하므로 서버가 내려준다. `0` 은 미배정이다.

### options — 계층

관심사 문항은 대분류 아래 세부 항목이 붙는다(S2-10 / S2-11).
**최상위 목록에는 대분류만 담기고, 세부는 각 대분류의 `children` 에 들어간다.**
계층이 없는 일반 문항은 모든 보기가 최상위이고 `children` 은 빈 배열이다.

### 내려주지 않는 것

보기의 내부 점수(`SCORE_VALUE`)는 매칭 알고리즘 값이라 응답에 포함하지 않는다.

### questionType

| 값 | 의미 | 필요한 답변 |
| --- | --- | --- |
| `SINGLE` | 단일 선택 | `optionId` 1개 |
| `MULTI` | 다중 선택 | `optionId` 여러 개 |
| `TEXT` | 주관식 | `answerText` |
| `SCALE` | 척도 | `answerNumber` |

---

## 3. 답변 제출

```
POST /api/v1/onboarding/answers       (인증 필요)
```

```json
{
  "answers": [
    { "questionId": 1, "optionId": 1, "rank": 1 },
    { "questionId": 1, "optionId": 6, "rank": 2 },
    { "questionId": 1, "optionId": 8 },
    { "questionId": 2, "optionId": 15 }
  ]
}
```

| 필드 | 설명 |
| --- | --- |
| `questionId` | 필수 |
| `optionId` | 선택형(`SINGLE`/`MULTI`)에서 필수 |
| `answerText` | 주관식. 2000자 이하 |
| `answerNumber` | 척도 |
| `rank` | **우선순위(1부터).** 관심사 대분류 정렬에만 쓴다. 없으면 생략 |

### rank — 우선순위

S2-13 의 드래그 정렬 결과다. 사용자가 매긴 순서가 매칭 스코어링에 쓰이므로
척도 답변(`answerNumber`)과 섞지 않고 별도 필드로 받는다.
세부 항목에는 순위를 매기지 않는다(대분류만 정렬 대상).

### 재제출 동작

- 같은 질문에 다시 제출하면 **이번 요청에 없는 기존 답변은 논리 삭제**된다.
  물리 삭제하지 않는 이유는 응답 변경 이력을 남기기 위함이다.
- 같은 `(questionId, optionId)` 가 한 요청에 여러 번 들어오면 **마지막 것만 남긴다.**
  답변은 선택 집합이라 중복이 의미가 없다.
- 한 번에 전체를 보내도 되고 문항별로 나눠 보내도 된다.

### 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "savedCount": 4,
    "answeredCount": 4,
    "completed": false,
    "totalSteps": 4,
    "completedSteps": 1,
    "completionRate": 25
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `savedCount` | 이번 요청으로 저장된 답변 수 |
| `answeredCount` | 지금까지 답변한 총 건수 |
| `completed` | 필수 문항을 모두 채웠는지 |
| `totalSteps` | 전체 구간 수 |
| `completedSteps` | 필수 문항을 모두 채운 구간 수 |
| `completionRate` | 완료율(0~100 정수) |

**진행률은 구간 기준이다.** 한 구간은 그 안의 필수 문항을 전부 채워야 완료로 센다.
`STEP_NO` 가 배정되지 않은 경우(콘텐츠 확정 전)에는 문항 하나를 한 구간으로 취급한다.

### 실패

| 상황 | HTTP | `code` |
| --- | --- | --- |
| 존재하지 않거나 비활성 질문 | 404 | `QUESTION_NOT_FOUND` 계열 |
| 존재하지 않는 보기 / 다른 질문의 보기 | 4xx | `OPTION_MISMATCH` 계열 |
| 선택형인데 `optionId` 없음 | 400 | `COMMON_001` |
| 단일 선택인데 보기를 2개 이상 | 400 | `COMMON_001` |
| 주관식인데 `answerText` 없음 | 400 | `COMMON_001` |
| 인증 없음 | 401 | `AUTH_001` |

---

## 4. 질문 콘텐츠

실제 문항은 아직 미확정이다(허익 단독 판단으로 추후 확정).
현재는 `V5__seed_onboarding_sample.sql` 이 **구조 검증용 예시 4문항**을 넣는다.

- 1구간: 관심사(`MULTI`) — 대분류 7개 + 세부 12개, 우선순위 대상
- 2~4구간: 2~3지선다 단일 선택

두 유형이 다 있어야 프론트가 관심사 통합화면과 일반 문항을 모두 검증할 수 있다.
콘텐츠가 확정되면 이 데이터를 지우고 새 마이그레이션으로 실제 문항을 넣는다.
**적용이 끝난 마이그레이션은 수정하지 않는다**([DB 마이그레이션 가이드](DB_MIGRATION.md) 참고).

---

## 5. 검증하기

```bash
docker compose up -d --build
```

Postman 컬렉션 `postman/BMA-38-onboarding.postman_collection.json` 을 Import 한 뒤
**Runner 로 위에서부터 순서대로** 실행한다. 요청 9개, 검증 30건이다.

설치 없이 돌리려면 newman 컨테이너를 쓴다.

```bash
docker run --rm -v "$(pwd)/backend/docs/postman:/etc/newman" postman/newman:alpine \
  run BMA-38-onboarding.postman_collection.json \
  --env-var "baseUrl=http://host.docker.internal:8080"
```

검증 항목: 계층 구조, `stepNo` 배정, 내부 점수 비노출, 우선순위 저장,
구간 기준 완료율, `onboardingCompleted` 반영, 인증 요구, 잘못된 질문/보기 거부.

### 리눅스 Docker Engine 을 쓰는 경우

`host.docker.internal` 은 Docker Desktop(윈도우/맥)이 만들어 주는 이름이라
리눅스 Docker Engine 에는 없다. 둘 중 하나로 바꾼다.

```bash
# 방법 1: 호스트 네트워크를 그대로 쓴다(리눅스에서만 동작)
docker run --rm --network host -v "$(pwd)/backend/docs/postman:/etc/newman" postman/newman:alpine run <컬렉션> --env-var "baseUrl=http://localhost:8080"

# 방법 2: 이름을 직접 만들어 준다(명령 형태를 그대로 두고 싶을 때)
docker run --rm --add-host=host.docker.internal:host-gateway -v "$(pwd)/backend/docs/postman:/etc/newman" postman/newman:alpine run <컬렉션> --env-var "baseUrl=http://host.docker.internal:8080"
```


---

## 6. 확정 필요

- **미입력 처리** — 사양서에 "선택 안 하고 다음 클릭 시 안내(미입력 처리는 별도 확정 필요)"
  로 남아 있다. 현재 서버는 필수 문항 미답변 상태를 그냥 미완료로 둘 뿐,
  건너뛰기를 따로 구분하지 않는다.
- **실제 문항 콘텐츠와 `STEP_NO` 배치** — 22문항을 7구간에 어떻게 나눌지.
- **세부 관심사 0개 선택 허용** — 사양서상 허용이다. 현재 서버도 대분류만 보내도 통과한다.
  대분류 없이 세부만 보내는 경우를 막을지는 정해지지 않았다.
