-- =====================================================================
-- V2: 초기 공통 코드 및 Reveal 정책 시드 데이터
--
-- ON DUPLICATE KEY UPDATE 를 쓰므로 재실행해도 안전하지만,
-- Flyway 는 성공한 마이그레이션을 다시 실행하지 않는다.
-- 코드 값을 추가/변경할 때는 이 파일을 고치지 말고 새 마이그레이션을 만든다.
-- =====================================================================

-- 초기 공통 코드 예시
INSERT INTO `CM_CODE_GROUP`
(`CODE_GROUP`, `CODE_GROUP_NAME`, `DESCRIPTION`, `USE_YN`, `INSERT_USER`)
VALUES
('GENDER', '성별', '사용자 성별 코드', 'Y', 'SYSTEM'),
('USER_STATUS', '회원 상태', '회원 서비스 상태', 'Y', 'SYSTEM'),
('ACTION_TYPE', '사용자 행동', '좋아요 및 패스 유형', 'Y', 'SYSTEM'),
('MATCH_STATUS', '매칭 상태', '매칭 진행 상태', 'Y', 'SYSTEM'),
('REPORT_TYPE', '신고 유형', '사용자 신고 분류', 'Y', 'SYSTEM')
ON DUPLICATE KEY UPDATE `UPDATE_USER`='SYSTEM', `UPDATE_DATE`=CURRENT_TIMESTAMP(6);

INSERT INTO `CM_CODE`
(`CODE_GROUP`, `CODE`, `CODE_NAME`, `SORT_ORDER`, `USE_YN`, `INSERT_USER`)
VALUES
('GENDER', 'MALE', '남성', 1, 'Y', 'SYSTEM'),
('GENDER', 'FEMALE', '여성', 2, 'Y', 'SYSTEM'),
('USER_STATUS', 'PENDING', '가입 대기', 1, 'Y', 'SYSTEM'),
('USER_STATUS', 'ACTIVE', '정상', 2, 'Y', 'SYSTEM'),
('USER_STATUS', 'SUSPENDED', '정지', 3, 'Y', 'SYSTEM'),
('USER_STATUS', 'WITHDRAWN', '탈퇴', 4, 'Y', 'SYSTEM'),
('ACTION_TYPE', 'LIKE', '좋아요', 1, 'Y', 'SYSTEM'),
('ACTION_TYPE', 'SUPER_LIKE', '슈퍼 좋아요', 2, 'Y', 'SYSTEM'),
('ACTION_TYPE', 'PASS', '패스', 3, 'Y', 'SYSTEM'),
('MATCH_STATUS', 'ACTIVE', '진행 중', 1, 'Y', 'SYSTEM'),
('MATCH_STATUS', 'UNMATCHED', '매칭 해제', 2, 'Y', 'SYSTEM'),
('MATCH_STATUS', 'BLOCKED', '차단 종료', 3, 'Y', 'SYSTEM'),
('REPORT_TYPE', 'ABUSE', '욕설 및 괴롭힘', 1, 'Y', 'SYSTEM'),
('REPORT_TYPE', 'FAKE', '허위 프로필', 2, 'Y', 'SYSTEM'),
('REPORT_TYPE', 'FRAUD', '사기 및 금전 요구', 3, 'Y', 'SYSTEM'),
('REPORT_TYPE', 'SEXUAL', '부적절한 성적 콘텐츠', 4, 'Y', 'SYSTEM'),
('REPORT_TYPE', 'ETC', '기타', 99, 'Y', 'SYSTEM')
ON DUPLICATE KEY UPDATE `UPDATE_USER`='SYSTEM', `UPDATE_DATE`=CURRENT_TIMESTAMP(6);

-- 3단계 Reveal 정책 기본값
INSERT INTO `RV_REVEAL_POLICY`
(`REVEAL_LEVEL`, `REVEAL_NAME`, `MIN_MESSAGE_COUNT`, `MIN_CHAT_MINUTES`, `MUTUAL_CONSENT_YN`, `DISCLOSE_SCOPE_JSON`, `USE_YN`, `INSERT_USER`)
VALUES
(0, '미공개', 0, 0, 'N', JSON_OBJECT('image','hidden','name','hidden'), 'Y', 'SYSTEM'),
(1, '실루엣 및 부분 공개', 20, 10, 'N', JSON_OBJECT('image','silhouette','profile','partial'), 'Y', 'SYSTEM'),
(2, '전체 공개', 50, 30, 'Y', JSON_OBJECT('image','full','profile','full'), 'Y', 'SYSTEM')
ON DUPLICATE KEY UPDATE `UPDATE_USER`='SYSTEM', `UPDATE_DATE`=CURRENT_TIMESTAMP(6);

