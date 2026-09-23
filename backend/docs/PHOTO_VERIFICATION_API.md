# S16 사진인증 API — BMA-82

S16 사양서 v1.4(S16-01~08, S16-04a~d 대조 실패), BMA-31(사진인증은 선택, 미인증 상대에게 S7-11 알림) 기준.
S8-17 "사진인증 받기" → S16 촬영 → 프로필 사진(S3)과 대조 → 성공 시 "인증됨" 배지. 촬영한 셀피는 저장·공개되지 않는다.

## 1. 엔드포인트 (액세스 토큰 필수)

| 화면 | API |
| --- | --- |
| S8-17 메뉴 표시(인증완료면 재진입 없음) | `GET /api/v1/verification/photo/status` |
| S16-04 촬영하기 | `POST /api/v1/verification/photo` (multipart, `file`) |

### 1.1 상태

```json
{"verified": true, "verifiedAt": "2026-09-24T01:10:03", "provider": "stub", "profileImageId": 41, "attempts": 3, "lastResult": "PASS"}
```

### 1.2 대조

`multipart/form-data` 의 `file` 로 셀피(JPEG/PNG/WebP/HEIC, 최대 10MB)를 보낸다. 서버는 파일 시그니처로 형식을 판별한다(Content-Type 은 믿지 않음).

성공(S16-05~08): `{"verified":true,"similarity":100.0,"threshold":80.0,"provider":"stub","verifiedAt":"…","profileImageId":41}`

| 코드 | HTTP | 상황 | 프론트 |
| --- | --- | --- | --- |
| `VERIFY_011` | 422 | 유사도가 기준 미만이거나 얼굴 미검출 | S16-04a~d 재촬영. 횟수 제한 없음 |
| `VERIFY_010` | 409 | 프로필 사진(S3)이 없음 | S3 로 안내 |
| `VERIFY_012` | 409 | 이미 완료 | S8-17 "인증완료" |
| `VERIFY_013` | 502 | 얼굴 대조 제공자 오류 | 잠시 후 재시도 |
| `USER_005` | 400 | 이미지 파일이 아님·비어 있음·크기 초과 | 재촬영 |

## 2. 촬영 이미지 미저장 (AC, 코드 리뷰 포인트)

`PhotoVerificationService.verify` 가 셀피를 다루는 전부다.

1. `MultipartFile#getBytes()` 로 메모리에만 읽는다. `StorageService.upload` 를 호출하지 않는다.
2. `FaceComparisonGateway.compare(selfie, …)` 에 바이트를 넘긴다. 이것이 셀피가 클래스 밖으로 나가는 유일한 경로다.
3. `finally` 에서 `Arrays.fill(selfieBytes, 0)` 로 내용을 지운다(오류·실패 경로 포함).
4. 시도 이력(`US_PHOTO_VERIFICATION`)에는 결과·유사도·기준·대조한 프로필 사진 ID 만 남는다. 이미지 컬럼·오브젝트 키·URL 이 없다.
5. 로그에는 바이트 길이만 남긴다.

리뷰 체크: `grep -rn "storageService.upload\|Files.write" src/main/java/com/bma/verification` 이 비어 있어야 한다.

## 3. 배지 노출 (요구 3) 과 S7-11 알림

- `photoVerified` 필드 추가: `MaskedProfileResponse`(S5 카드 `/matches`, `/matches/current`, S10 상세 `/matches/{id}` 의 `partner`, S6 채팅방 목록 `partner`, 추천 목록), `GET /users/me`, `GET /users/me/profile`(S8-08).
  공개 단계와 무관하게 항상 노출된다(사진은 여전히 비공개).
- 매칭 성사 시 **상대가 사진인증 미완료**면 `MATCH_UNVERIFIED_PARTNER` 알림(→ S10, matchId)을 보낸다. `MATCH_CREATED` 보다 먼저 발행해 최신 알림은 그대로 `MATCH_CREATED` 다.
- **프로필 사진을 바꾸거나 지우면 인증이 해제**된다(인증 당시 사진 `PHOTO_VERIFIED_IMAGE_ID` 기준). 새 사진으로 다시 인증할 수 있다.

## 4. 얼굴 대조 기술 결정 (허익님 논의 요청 답변)

**결정: 자체 모델을 만들지 않고 외부 API — AWS Rekognition `CompareFaces` 를 목표 제공자로 한다.** 업체 연동은 `FaceComparisonGateway` 뒤에 있고 지금은 스텁이다.

| 선택지 | 정확도·기능 | 비용(대략, 콘솔 확인 필요) | 운영 부담 | 판단 |
| --- | --- | --- | --- | --- |
| 자체 구축(InsightFace/ArcFace 등 오픈소스) | 모델 자체는 우수하나 얼굴 검출·정렬·임계값 튜닝·라이브니스를 직접 책임 | 서버(GPU 또는 CPU 추론) 상시 비용 + 개발·운영 인력 | 높음 | MVP 부적합 |
| **AWS Rekognition CompareFaces** | 1:1 대조에 특화, `Similarity`(0~100) 반환, 얼굴 미검출 응답 구분, 별도 Face Liveness 기능 있음 | 이미지 분석 건당 약 0.001 USD 수준(첫 12개월 월 5,000장 무료 티어), 대조 1회 = 1건 | 낮음(호출만) | **채택** |
| Azure Face API (verify) | 정확도 좋음 | 유사 종량제 | 2022년부터 얼굴 검증 기능은 Limited Access 승인 필요 — 스타트업이 바로 쓰기 어려움 | 보류 |
| 네이버 클라우드 CLOVA Face | 얼굴 감지·유명인 인식 위주 | 종량제 | 1:1 대조(compare) API 제공 여부 확인 필요 | 보류 |

- 임계값: Rekognition 권장대로 **80~90** 에서 시작(`PHOTO_VERIFY_THRESHOLD`, 기본 80). 운영 데이터로 오탐/미탐을 보며 조정.
- 라이브니스(S16-03 비고): MVP 는 실시간 카메라 촬영만 요구하고 서버 라이브니스는 두지 않는다. 필요해지면 Rekognition Face Liveness 를 같은 게이트웨이 뒤에 추가.
- 리전: 서울(ap-northeast-2) 에서 Rekognition 사용 가능. 이미지는 요청에 바이트로 실어 보내고 S3 에 올리지 않는다(미저장 원칙 유지).

`RekognitionFaceComparisonGateway` 구현 + `PHOTO_VERIFY_PROVIDER=rekognition` 전환이 남은 작업이며, 프론트 계약은 바뀌지 않는다.

## 5. 스키마 (V16)

- `US_USER.PHOTO_VERIFIED_YN/DATE`, `PHOTO_VERIFY_PROVIDER`, `PHOTO_VERIFIED_IMAGE_ID`
- `US_PHOTO_VERIFICATION`: 시도 이력(결과 PASS/FAIL/ERROR, 유사도, 기준, 프로필 사진 ID, 실패 사유). 이미지 없음.

## 6. 검증

```bash
BASE=http://localhost:8080 DB_EXEC_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e" bash backend/scripts/verify-photo.sh   # 12단계
```

Postman `postman/BMA-82-photo.postman_collection.json`(셀피는 `sample-profile.png`/`sample-other.png` 파일 선택). BMA-53 알림 스위트에는 매칭 전 사진인증 단계를 추가했다(미인증 알림으로 건수가 바뀌지 않게).

## 7. 결정 사항 (티켓에 없어 정한 것)

- 대조 실패는 200 이 아니라 `422 VERIFY_011` 로 응답(성공/실패를 상태 코드로 구분, S16-04a~d 분기).
- 사진 교체·삭제 시 인증 자동 해제. 재인증은 새 사진으로.
- 스텁은 "같은 파일이면 통과"라 개발·검증 전용. 운영 전 `PHOTO_VERIFY_PROVIDER` 를 반드시 바꾼다.
- 미인증 상대 알림은 미인증인 *상대를 가진* 쪽에게 간다(S7-11 "상대가 사진인증 미완료 → S10").
