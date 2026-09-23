-- ---------------------------------------------------------------------
-- BMA-66: S9 매칭대기 — 대기열 항목에 매칭 결과를 남긴다.
--   GET /matching/queue 가 "매칭 성사" 상태를 돌려주려면 어떤 매칭으로 끝났는지 알아야 한다.
--   타임아웃(5분) 항목은 QUEUE_STATUS=EXPIRED 로 남고, 소모한 이용권은 환불된다.
-- ---------------------------------------------------------------------
ALTER TABLE `MT_MATCH_QUEUE`
    ADD COLUMN `MATCH_ID` BIGINT UNSIGNED NULL COMMENT '성사된 매칭 ID (MATCHED 일 때)' AFTER `EXPIRE_DATE`,
    ADD COLUMN `MATCHED_DATE` DATETIME(6) NULL COMMENT '매칭 성사 일시' AFTER `MATCH_ID`,
    ADD INDEX `IX_MT_QUEUE_USER_STATUS` (`USER_ID`, `QUEUE_ID`, `DELETED`);
