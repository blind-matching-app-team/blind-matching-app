# S12 관리자 신고검토 API — BMA-76

BMA-30 확정 정책(신고 유형 5종·즉시검토·누적 3/5/7회 조치·감형·감사 로그)을 실제로 실행하는 관리자 API.
사용자 쪽 신고 접수는 `CHAT_API.md` 3절(BMA-72).

## 1. 관리자 구별 (티켓 미결 사항 → 결정)

- 별도 진입점을 만들지 않고 **일반 로그인(S1)과 같은 토큰**을 쓴다. `US_USER.USER_ROLE='ADMIN'` 인 계정만 `/api/v1/admin/**` 를 통과한다(SecurityConfig `hasRole("ADMIN")`, 그 외 `403 AUTH_005`).
- 역할은 JWT 클레임에 실리므로 승격 후 **다시 로그인**해야 한다. 승격 API 는 두지 않는다(운영 DB 에서 `UPDATE US_USER SET USER_ROLE='ADMIN'`).
- S12-09 관리자 정보는 `GET /api/v1/users/me` 의 `email`/`userRole`, 로그아웃은 기존 `POST /api/v1/auth/logout`.

## 2. 엔드포인트

| 화면 | API |
| --- | --- |
| S12 목록 탭(대기중/처리완료) | `GET /api/v1/admin/reports?status=PENDING\|DONE\|ALL&page=&size=` |
| S12-02 탭 건수 "대기중 (건수)" | `GET /api/v1/admin/reports/counts` → `{pending, done, all}` |
| S12 상세 + S12-08 감사 로그 | `GET /api/v1/admin/reports/{reportId}` |
| S12-06 승인 / S12-07 반려 | `POST /api/v1/admin/reports/{reportId}/review` |
| 직권 조치 | `POST /api/v1/admin/users/{userId}/sanctions` · `GET .../sanctions` · `DELETE /api/v1/admin/sanctions/{sanctionId}` |
| 감사 로그 전체 | `GET /api/v1/admin/audit-logs?targetUserId=&reportId=&page=&size=` |

### 2.1 목록 항목 (`AdminReportSummary`)

```json
{"reportId": 77, "reportType": "FRAUD", "reportTypeName": "사기", "severity": "SEVERE", "status": "PENDING_REVIEW",
 "immediateReview": true, "description": "돈을 요구했어요", "matchId": 44, "messageId": 512, "messagePreview": "돈 좀 빌려줘",
 "reporter": {"userId": 9, "nickname": "검토R1", "email": "r1@bma.test", "userStatus": "ACTIVE"},
 "target": {"userId": 12, "nickname": "검토T", "email": "t@bma.test", "userStatus": "ACTIVE", "reportCount": 2, "activeSanction": null},
 "reportedAt": "2026-09-23T23:01:10", "processedAt": null, "processAdminId": null, "actionCode": null, "reviewNote": null}
```

- `status` 필터: `PENDING` = `PENDING_REVIEW`(기본), `DONE` = `RESOLVED`+`REJECTED`, `ALL` = 자동 반영(`COUNTED`)까지 전부. 최신순.
- `target.reportCount` = BMA-30 누적 카운트(3절). `target.activeSanction` = 지금 효력 있는 가장 무거운 제재(BAN > SUSPEND > WARNING).
- 상세는 여기에 `targetReportHistory`(피신고자의 다른 신고), `targetSanctions`(제재 이력 전부), `auditLogs`(이 신고의 감사 로그, 시간순)가 붙는다.

### 2.2 승인/반려 요청

```json
{"decision": "APPROVE", "action": null, "suspendDays": 7, "note": "누적 확인"}
```

| 필드 | 설명 |
| --- | --- |
| `decision` | `APPROVE`(유효) / `REJECT`(기각) |
| `action` | 승인 시 조치 지정(직권). `NONE`/`WARNING`/`SUSPEND`/`BAN`. **생략하면 누적 단계로 자동 결정** |
| `suspendDays` | `SUSPEND` 일수, 기본 7 |
| `note` | 검토 메모(감사 로그·제재 사유에 남음) |

응답 `ReviewResponse{report, actionTaken, sanction, conversationEnded, reportCountAfter}`.

- 검토 대기(`PENDING_REVIEW`)가 아닌 신고 → `409 SAFE_002`, 없는 신고 → `404 SAFE_003`.

## 3. 승인 시 실행되는 것 (BMA-30)

1. 신고 `RESOLVED` → 누적 카운트에 반영. **누적 = (COUNTED + RESOLVED 신고 수) − 감형**. 저장하지 않고 계산한다.
2. 조치 결정(`action` 생략 시): 누적 **3회 → 경고**, **5회 → 7일 이용 제한**, **7회 이상 → 영구 차단**. 그 사이 값은 조치 없음.
   임계값을 건너뛴 경우(직권 조치 없이 6회 등)는 아직 받은 적 없는 가장 높은 단계를 실행한다.
3. **감형(1회 한정)**: 가장 먼저 부과된 7일 제한이 끝나면 누적에서 2 를 뺀다(5→3). 두 번째 제한부터는 감형 없음. 리셋 없음(평생 누적).
4. 조치 실행
   - `WARNING`: 제재 행 + 알림 `WARNING_ISSUED`(S7-13).
   - `SUSPEND`/`BAN`: 제재 행 + 계정 `SUSPENDED`(`SUSPENDED_UNTIL`) + 리프레시 토큰 전부 폐기 + 대기 중 매칭 취소. 로그인은 `403 AUTH_009`(`restrictionType` TEMPORARY/PERMANENT, `restrictedUntil`). 이미 발급된 액세스 토큰은 만료(최대 30분)까지 유효하다.
   - 기간이 끝났거나 해제되면 **다음 로그인 때 자동으로 활성화**된다(`SanctionService.liftExpiredSuspension`).
5. 신고에 매칭이 연결돼 있으면 **대화 종료**: 매칭 `BLOCKED`(사유 `ADMIN_REPORT`), 채팅방 `CLOSED`, 방에 `SYSTEM` 메시지 "관리자에 의해 대화가 종료되었습니다.", 피신고자에게 `MATCH_ENDED` 알림. 신고자는 신고 시점에 이미 나가 있다.
6. 반려: `REJECTED`, 누적 미반영. 중대 유형으로 막혀 있던 새 매칭 진입(`MATCH_007`)이 풀린다.

## 4. 감사 로그 (S12-08)

`SF_ADMIN_AUDIT_LOG` — 누가(`adminUserId`/`adminEmail`) / 언제(`actionDate`) / 무슨 근거(`reportId`, `sanctionId`, `detail`) / 무슨 조치(`actionCode`).

| actionCode | 시점 |
| --- | --- |
| `REPORT_APPROVE` / `REPORT_REJECT` | 승인/반려 (detail 에 누적 횟수·조치·메모) |
| `SANCTION_WARNING` / `SANCTION_SUSPEND` / `SANCTION_BAN` | 제재 부과(승인 자동 조치·직권 모두) |
| `SANCTION_LIFT` | 제재 해제 |
| `CONVERSATION_END` | 승인으로 매칭·채팅방 종료 |

행은 갱신·삭제하지 않는다.

## 5. 직권 제재

`POST /admin/users/{userId}/sanctions` `{"type":"WARNING|SUSPEND|BAN","days":7,"reason":"...","sourceReportId":null}` → `SanctionResponse`.
`DELETE /admin/sanctions/{id}` 로 해제하면 로그인을 막는 제재가 더 없을 때 계정이 활성화된다. 이미 해제된 제재 → `404 SAFE_004`.

## 6. 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| `AUTH_005` | 403 | 관리자가 아닌 토큰 |
| `SAFE_002` | 409 | 검토 대기가 아닌 신고를 승인/반려 |
| `SAFE_003` | 404 | 없는 신고 |
| `SAFE_004` | 404 | 없는/이미 해제된 제재 |
| `USER_001` | 404 | 없는 사용자에게 직권 제재 |
| `COMMON_001` | 400 | decision/action/type 값 오류, 일수 범위 |

## 7. 스키마 (V14)

- `SF_ADMIN_AUDIT_LOG` 신설(BMA-14 감사로그 항목).
- `SF_USER_REPORT.REVIEW_NOTE` 추가, `ACTION_CODE` 값 `NONE/WARNING/SUSPEND/BAN`. 기존 `PROCESS_USER_ID`/`PROCESS_DATE` 를 승인/반려에 사용.

## 8. 검증

```bash
ADMIN_PROMOTE_CMD="docker compose exec -T mysql mysql -ubma -pbma1234 bma -e" \
DB_EXEC_CMD="$ADMIN_PROMOTE_CMD" BASE=http://localhost:8080 bash backend/scripts/verify-admin-reports.sh   # 14단계
```

Postman: `postman/BMA-76-admin-reports.postman_collection.json`(관리자 승격·7일 제한 종료는 DB 작업이라 컬렉션에서는 해당 단계 전에 수동 실행).

## 9. 결정 사항 (티켓에 없어 정한 것)

- 관리자 구별은 1절. 별도 로그인 화면·승격 API 없음.
- 누적 카운트는 계산값(신고·제재 행만 정본). 감형은 "첫 7일 제한 행의 종료 시각이 지났는가"로 판정.
- 승인 시 `action` 을 주면 단계 규칙을 무시하고 그 조치만 실행(관리자 직권). 중대 유형 1회 승인도 기본은 단계 규칙(누적 1이면 조치 없음)이라, 즉시 조치가 필요하면 `action` 을 지정한다.
- 정지 중 발급된 액세스 토큰은 즉시 무효화하지 않는다(요청마다 DB 조회를 피함). 리프레시는 폐기하므로 최대 30분.
