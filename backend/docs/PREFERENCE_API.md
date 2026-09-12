# 매칭 선호조건 API 계약 (S4)

BMA-44 · S4 사양서 v1.5 기준. 프론트(BMA-43) MSW 목은 이 문서를 따른다.

관련 결정
- **BMA-19 안건2 (2026-08-18)**: 나이는 필수 입력 + 매칭 필터, **키는 필터에서 영구 제외**
  (서비스 정체성 "외모보다 대화"와 충돌). 기술 미확정이 아니라 의도된 설계다. S4-05 안내 문구가 이걸 설명한다.
- **S4-04 [결정 v1.5]**: 희망 지역은 S3-10과 같은 행안부 표준 2단계(시/도 → 시/군/구).
  "서울 전체"처럼 **시/도 단위 전체 선택도 가능**하다.
- **S4-09 [결정 v1.5]**: 나이대 19~99, 최소값 19 고정(미성년자 범위 설정 원천 차단), 기본값은 제한 없음.

---

## 1. 화면 흐름

S3 프로필 → **S4 선호조건** → (S4-06 매칭 시작하기) → 본인인증(S15) 미완료면 S15, 완료면 메인 허브(S5)

- S4-06 은 `PUT /api/v1/users/me/preference` 를 호출한 뒤 라우팅한다. 본인인증 여부 판단은 BMA-79 범위라
  이 API 응답에는 없다. 프론트는 로그인/내 정보 응답의 인증 플래그(BMA-79 에서 추가)를 본다.
- S4-07 이전 버튼은 API 호출 없이 S3 로 돌아간다(입력값은 프론트가 유지).

---

## 2. 선호조건 저장

```
PUT /api/v1/users/me/preference      (인증 필요)
Content-Type: application/json
```

```json
{
  "preferredRegionCode": "SEOUL_GANGNAM",
  "minAge": 25,
  "maxAge": 35
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `preferredRegionCode` | **필수** | `GET /api/v1/regions` 의 코드. **시/군/구 코드 또는 시/도 코드** 둘 다 허용. 시/도 코드면 "그 시/도 전체"를 뜻한다 |
| `minAge` | 선택 | 19~99. `null` 이면 하한 없음 |
| `maxAge` | 선택 | 19~99, `minAge` 이상. `null` 이면 상한 없음 |
| `preferredGenderCode` | 선택 | S4 화면엔 없다. 매칭(S5)에 필요해 API 만 열어 둔다 |
| `maxDistanceKm` | 선택 | 1~500. 생략하면 기존 값 유지(기본 50) |
| `matchingEnabled` | 선택 | 생략하면 기존 값 유지(기본 true) |

**키 관련 필드는 없다.** `minHeightCm`/`maxHeightCm` 를 보내도 무시된다(오류 아님). 다시 추가하지 말 것.

### 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "성공",
  "data": {
    "preferredRegionCode": "SEOUL_GANGNAM",
    "preferredRegionName": "강남구",
    "preferredRegionScope": "SIGUNGU",
    "regionSidoCode": "SEOUL",
    "regionSidoName": "서울특별시",
    "minAge": 25,
    "maxAge": 35,
    "preferredGenderCode": null,
    "maxDistanceKm": 50,
    "matchingEnabled": true
  }
}
```

- `preferredRegionScope`: `SIGUNGU`(시/군/구 하나) 또는 `SIDO`(시/도 전체). 시/도 전체를 고르면
  `preferredRegionCode`/`regionSidoCode` 가 같은 값이고 `preferredRegionName` 은 시/도명이다.
- 값이 없는 필드도 `null` 로 **항상 존재**한다(전역 non_null 설정을 이 응답에서만 끈다). 프론트가 폼에 그대로 바인딩한다.

"서울 전체" 예:

```json
{ "preferredRegionCode": "SEOUL" }
→ "preferredRegionName": "서울특별시", "preferredRegionScope": "SIDO",
  "regionSidoCode": "SEOUL", "regionSidoName": "서울특별시", "minAge": null, "maxAge": null
```

### 실패

| HTTP | code | 언제 |
|---|---|---|
| 400 | `COMMON_001` | 지역 누락, 없는 지역 코드, 나이 범위 밖(18 이하·100 이상), `minAge > maxAge` |
| 401 | `AUTH_001` | 토큰 없음/만료 |

실패 응답의 `message` 에 어느 필드가 왜 틀렸는지 들어 있다.

---

## 3. 선호조건 조회

```
GET /api/v1/users/me/preference      (인증 필요)
```

응답 모양은 저장과 같다. 아직 저장한 적이 없으면 **기본값**을 200 으로 준다(404 아님):
지역 관련 5개 필드 `null`, `minAge`/`maxAge` `null`, `maxDistanceKm` 50, `matchingEnabled` true.

---

## 4. 매칭에 어떻게 반영되나

`GET /api/v1/matching/recommendations` 가 이 조건으로 후보를 거른다.

- 지역: 후보의 활동 지역(`US_USER_PROFILE.REGION_CODE`, 시/군/구)이 희망 지역 **자체이거나 그 하위**.
  시/도 전체를 골랐으면 `CM_REGION` 의 상위 관계로 하위 시/군/구를 전부 포함한다(문자열 접두 일치가 아니다).
- 나이: 후보의 생년월일을 만 나이 범위로 환산해 거른다. `null` 인 쪽은 제한 없음.
- 성별: `preferredGenderCode` 가 있을 때만.
- **키: 거르지 않는다.**

---

## 5. 스키마 (V8)

- `US_USER_PREFERENCE.MIN_HEIGHT_CM`, `MAX_HEIGHT_CM` 컬럼과 `CK_US_PREF_HEIGHT` 제약 삭제
- `PREFERRED_REGION_CODE` 주석 갱신(시/도 코드 허용). 타입은 V6 에서 이미 VARCHAR(30)

---

## 6. 검증하기

```bash
docker compose up -d --build
```

Postman 컬렉션 `postman/BMA-44-preference.postman_collection.json` 을 Runner 로 위에서부터 순서대로 실행한다.
newman/node 가 없으면:

```bash
BASE=http://localhost:8080 bash backend/scripts/verify-preference.sh
```

(Windows Git Bash 는 `LANG=C.UTF-8` 을 앞에 붙인다.)

---

## 7. 확정 필요

- **본인인증 플래그** — S4-06 의 S15 분기에 필요한 값. BMA-79 에서 로그인/내 정보 응답에 추가한다.
- **선호 성별 수집 시점** — S4 에 입력란이 없어 `preferredGenderCode` 는 항상 null 로 들어온다.
  성별 없이 추천하면 동성도 섞인다. 프로필의 성별 수집 시점(PROFILE_API.md 7절)과 함께 정해야 한다.
- **BMA-14 스키마 반영** — 키 컬럼 삭제를 ERD 에도 반영해야 한다(허익).
