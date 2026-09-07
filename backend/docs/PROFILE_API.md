# 프로필 API 계약 (S3)

프론트(S3 프로필 설정 화면)가 MSW 목을 맞출 때 이 문서를 정본으로 삼는다.
공통 응답 봉투와 오류 규약은 [인증 API 계약](AUTH_API.md) 1절과 같다.

> **경로 주의**
> 티켓 초안에는 `/users/me/profile` 로 적혀 있으나 **실제는 `/api/v1/users/me/profile`** 이다.

---

## 1. 화면 흐름과 완료 플래그

```
S1 로그인 → S2 온보딩 → S3 프로필 → S4 매칭 선호조건
```

로그인/토큰 응답의 `profileCompleted` 가 `true` 가 되면 S3를 통과한 것이다.
판정 기준은 **닉네임 · 생년월일 · 지역** 3개다.

### 성별이 완성 판정에 없는 이유

S3 화면에 성별 입력란이 없다(S3-01 ~ S3-16 어디에도 없다). S1 회원가입에도
없다. 그런데 기존 백엔드는 성별을 필수로 보고 있어서, **사용자가 화면을 다
채워도 `profileCompleted` 가 영원히 `false`** 였다. 프론트는 S3 → S3 무한
루프에 빠진다.

그래서 완성 판정에서 성별을 뺐다. 다만 매칭(S4)의 상대 성별 필터에는 성별이
반드시 필요하므로, 조건을 둘로 나눴다.

| 판정 | 조건 | 쓰는 곳 |
| --- | --- | --- |
| `isComplete()` | 닉네임 + 생년월일 + 지역 | `profileCompleted` 응답, 라우팅 |
| `isMatchable()` | 위 + **성별** | 매칭 API 진입 조건 |

성별이 없는 사용자가 매칭을 호출하면 `MATCH_005`(`PROFILE_INCOMPLETE`)로 막힌다.

> **미확정** — 성별을 어디서 받을지. 사양서에 "성인 여부는 S15 본인인증으로
> 별도 검증"이라고 돼 있어 본인인증에서 함께 받을 가능성이 있다. 확정 전까지
> S3만 거친 사용자는 매칭에 들어갈 수 없다.

---

## 2. 프로필 저장

```
PUT /api/v1/users/me/profile      (인증 필요)
```

```json
{
  "nickname": "블라인드",
  "birthDate": "1998-03-14",
  "regionCode": "SEOUL_GANGNAM",
  "mbtiCode": "INFP",
  "heightCm": 175
}
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `nickname` | O | 2~10자. 중복 불가 (S3-06) |
| `birthDate` | O | 과거 날짜 (S3-08) |
| `regionCode` | O | **시/군/구 코드.** 시/도 코드는 거부된다 (S3-10) |
| `mbtiCode` | | 16개 유형 중 하나. 빈 문자열이면 미설정 (S3-12) |
| `heightCm` | | 100~250. 미입력이면 비공개 (S3-16) |
| `genderCode` | | 화면에는 없다. 1절 참고 |
| `occupation` | | 100자 이하. S3 화면에는 없는 기존 필드 |
| `introduction` | | 1000자 이하. S3 화면에는 없는 기존 필드 |

### 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "userId": 1,
    "nickname": "블라인드",
    "birthDate": "1998-03-14",
    "age": 28,
    "genderCode": null,
    "regionCode": "SEOUL_GANGNAM",
    "regionName": "강남구",
    "regionSidoCode": "SEOUL",
    "regionSidoName": "서울특별시",
    "mbtiCode": "INFP",
    "occupation": null,
    "heightCm": 175,
    "introduction": null,
    "profileStatus": "COMPLETE",
    "profileScore": 70
  }
}
```

지역은 코드와 **이름을 함께** 준다. 프론트가 지역 목록을 따로 받아 코드를
이름으로 되짚지 않아도 "서울특별시 · 강남구"를 그릴 수 있다.

> **미입력 항목은 키가 빠지지 않고 `null` 로 온다.** 이 서비스는 전역 Jackson
> 설정이 `non_null` 이라 값이 없는 필드가 응답에서 통째로 사라지는데, 이 응답만
> `@JsonInclude(ALWAYS)` 로 예외를 뒀다. 프론트가 폼에 그대로 바인딩하므로
> 모양이 입력 상태에 따라 흔들리면 안 되기 때문이다.

### profileScore

추천 정렬 가중치다. 필수 3개를 채우면 50, 선택 항목마다 10점씩 붙어 최대 100이다.

| 항목 | 점수 |
| --- | --- |
| 필수(닉네임·생년월일·지역) | 50 |
| 성별 / MBTI / 직업 / 키 / 자기소개(20자 이상) | 각 10 |

### 실패

| 상황 | HTTP | `code` |
| --- | --- | --- |
| 닉네임 중복 | 409 | `USER_003` |
| 지역 코드가 없거나 시/도 코드 | 400 | `COMMON_001` |
| 닉네임 길이/생년월일/키 범위 위반 | 400 | `COMMON_001` |
| 인증 없음 | 401 | `AUTH_001` |

---

## 3. 닉네임 중복 확인

```
GET /api/v1/users/me/profile/nickname-check?nickname=블라인드      (인증 필요)
```

```json
{ "success": true, "code": "SUCCESS", "data": { "nickname": "블라인드", "available": false } }
```

본인이 이미 쓰는 닉네임은 `available: true` 다.

> **이 결과가 저장을 보장하지는 않는다.** 확인과 저장 사이에 다른 사용자가
> 같은 닉네임을 선점할 수 있어, 저장 시점에도 같은 검사를 한다. 프론트는
> 저장에서 `USER_003` 이 오는 경우를 여전히 처리해야 한다.

---

## 4. 프로필 사진

**사진은 1장만 갖는다.** 여러 장 업로드·순서변경·대표지정은 범위에서 빠졌다.
Reveal 이 "여러 장 중 선택 공개"가 아니라 "1장을 블러 → 선명"이라 기능적으로도
여러 장이 필요 없다.

### 조회

```
GET /api/v1/users/me/image        (인증 필요)
```

아직 올리지 않았으면 **오류가 아니다.** 미등록은 정상 상태다.
다만 봉투의 `data` 는 전역 `non_null` 설정 때문에 **키 자체가 빠진다.**

```json
{ "success": true, "code": "SUCCESS" }
```

자바스크립트에서 `body.data` 는 `undefined` 다. `=== null` 로 비교하면 어긋난다.

사진이 있으면 이렇게 온다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "imageId": 12,
    "objectKey": "profile/1/9f2c...jpg",
    "originalName": "photo.jpg",
    "contentType": "image/jpeg",
    "fileSize": 284913,
    "reviewStatus": "PENDING"
  }
}
```

### 등록 / 교체

```
POST /api/v1/users/me/image       (인증 필요, multipart/form-data)
파트 이름: file
```

**이미 사진이 있으면 교체된다.** 새 파일을 저장소에 먼저 올린 뒤 기존 것을
지우므로, 업로드가 검증에서 실패하면 기존 사진이 그대로 남는다.

| 제약 | 값 |
| --- | --- |
| 장수 | 1장 (교체) |
| 최대 크기 | 10MB |
| 허용 형식 | jpeg / png / webp / **heic** / heif |

MIME 타입만 믿지 않고 **파일 시그니처까지 검사**한다. HEIC/HEIF는 ISO base
media 컨테이너라 `ftyp` 박스의 브랜드(`heic`/`heix`/`mif1` 등)까지 확인한다.
브랜드를 보지 않으면 같은 컨테이너인 mp4 동영상이 프로필 사진으로 들어온다.

### 삭제

```
DELETE /api/v1/users/me/image     (인증 필요)
```

기본 아바타로 돌아간다. DB 플래그만 바꾸지 않고 저장소의 실제 파일도 지운다.
등록된 사진이 없으면 `404 COMMON_404` 다.

---

## 5. 지역 목록

```
GET /api/v1/regions               (인증 필요)
```

시/도와 하위 시/군/구를 **트리로 한 번에** 준다. 전체가 246건이라 2차 요청을
왕복시킬 이유가 없다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": [
    {
      "code": "SEOUL",
      "name": "서울특별시",
      "children": [
        { "code": "SEOUL_JONGNO", "name": "종로구" },
        { "code": "SEOUL_GANGNAM", "name": "강남구" }
      ]
    }
  ]
}
```

프로필에 저장할 수 있는 것은 **`children` 안의 코드뿐**이다. 시/도 코드를
`regionCode` 로 보내면 400 이다. 사양서상 지역 선택이 시/도 → 시/군/구
2단계를 모두 거치도록 돼 있어서다.

### 지역 코드에 대해

`REGION_CODE` 는 **내부 식별자**(로마자)이지 행정안전부 표준 코드가 아니다.
표준 코드는 데이터 소스가 확정되면 `LEGAL_CODE` 컬럼에 채운다(티켓 기준 미확정).
프론트는 코드를 **불투명한 문자열**로 다뤄야 한다. `SEOUL_` 접두어를 파싱해
시/도를 알아내는 식의 코드는 나중에 깨진다 — 상위는 응답의 `regionSidoCode` 를 쓴다.

세종특별자치시는 단층제라 하위 행정구역이 없지만, 화면의 2단계 선택을 일관되게
유지하려고 자기 자신을 하위로 한 건 넣어 뒀다.

행정구역은 개편이 잦다(2023 강원특별자치도, 군위군 대구 편입 / 2024 전북특별자치도).
개편이 생기면 시드 파일을 고치지 말고 **새 마이그레이션**을 만든다.

---

## 6. 검증하기

```bash
docker compose up -d --build
```

Postman 컬렉션 `postman/BMA-40-profile.postman_collection.json` 을 Import 한 뒤
**Runner 로 위에서부터 순서대로** 실행한다.

설치 없이 돌리려면 newman 컨테이너를 쓴다.

```bash
docker run --rm --network host -v "$(pwd)/backend/docs/postman:/etc/newman" \
  postman/newman:alpine run BMA-40-profile.postman_collection.json \
  --env-var "baseUrl=http://localhost:8080"
```

Docker Desktop(윈도우/맥)에서는 `--network host` 대신
`--env-var "baseUrl=http://host.docker.internal:8080"` 을 쓴다.

---

## 7. 확정 필요

- **성별 수집 시점** — 1절 참고. 정해지기 전까지 매칭 진입이 막힌다.
- **행정안전부 표준 코드 매핑** — `LEGAL_CODE` 가 비어 있다. 데이터 소스
  (법정동 API 등) 확정 후 새 마이그레이션으로 채운다.
- **`US_PROFILE_IMAGE` 구조 단순화** — `DISPLAY_ORDER`, `PRIMARY_YN` 은 다중
  이미지 전제로 만들어진 컬럼이다. 사진이 1장이라 항상 `1` / `Y` 라 정보를
  담지 않는다. 응답에서는 이미 뺐고, 컬럼 제거는 BMA-14 확인 후 별도 처리한다.
- **생년월일 검증** — BMA-19(미성년자 처리) 결정 대기. 지금은 "과거 날짜"만 본다.
- **사진 검수(`REVIEW_STATUS`)** — 업로드 직후 `PENDING` 으로 들어가지만 검수
  절차가 없어 상태가 바뀌지 않는다. 지금은 검수와 무관하게 노출된다.
