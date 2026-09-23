-- ---------------------------------------------------------------------
-- BMA-72: S11 채팅 REST API — S11-08 차단 상대 메시지 규칙 + BMA-30 신고 정책 (2026-09-23)
--   1) 차단된 사람이 보낸 메시지는 발신자 화면에만 보이고 상대에게는 전달되지 않는다(차단 사실 비노출).
--      메시지 행을 버리지 않고 HIDDEN_YN='Y' 로 저장해 발신자 이력에는 남기고, 상대 이력·안읽음·알림·브로드캐스트에서 뺀다.
--   2) 신고 유형 심각도(일반/중대)를 컬럼으로 남긴다. 중대(FRAUD/SEXUAL)는 1회 신고로 즉시 관리자 검토(PENDING_REVIEW),
--      일반은 누적 1~2회 자동 반영(COUNTED), 3회째부터 관리자 검토(PENDING_REVIEW).
-- ---------------------------------------------------------------------
ALTER TABLE `CH_CHAT_MESSAGE`
    ADD COLUMN `HIDDEN_YN` CHAR(1) NOT NULL DEFAULT 'N' COMMENT '차단된 발신자의 메시지(발신자에게만 보임) 여부(Y/N)' AFTER `SEND_DATE`;

ALTER TABLE `SF_USER_REPORT`
    ADD COLUMN `SEVERITY` VARCHAR(10) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL(누적 기반)/SEVERE(즉시 관리자 검토)' AFTER `REPORT_TYPE`,
    MODIFY COLUMN `REPORT_STATUS` VARCHAR(20) NOT NULL DEFAULT 'COUNTED'
        COMMENT 'COUNTED(자동 반영)/PENDING_REVIEW(관리자 검토 대기)/RESOLVED(유효 판정)/REJECTED(기각) — 구 RECEIVED 는 COUNTED 와 같다';

-- 기존 신고는 전부 일반 유형 누적분으로 본다. 중대 유형만 심각도를 맞춰 둔다.
UPDATE `SF_USER_REPORT`
   SET `SEVERITY` = 'SEVERE', `UPDATE_USER` = 'SYSTEM', `UPDATE_DATE` = CURRENT_TIMESTAMP(6)
 WHERE `REPORT_TYPE` IN ('FRAUD', 'SEXUAL');

UPDATE `SF_USER_REPORT`
   SET `REPORT_STATUS` = 'COUNTED', `UPDATE_USER` = 'SYSTEM', `UPDATE_DATE` = CURRENT_TIMESTAMP(6)
 WHERE `REPORT_STATUS` = 'RECEIVED';
