-- ---------------------------------------------------------------------
-- BMA-69: S10 Reveal — BMA-19 안건1 확정값 반영 (2026-08-18)
--   단계 전환 조건 = 매칭 후 24시간 경과 + 양측 각자 메시지 10개 이상(합산 20) + 상호 동의.
--   기존 시드(20건/10분 → 50건/30분, 1단계 동의 불필요)는 팀 미확정 예시값이었다.
--   대화 시간(MIN_CHAT_MINUTES) 조건은 확정안에 없어 0 으로 내리고, 시간 조건은 "매칭 후 경과 시간"으로 바꾼다.
--   2단계(전체 공개) 임계값은 확정되지 않아 같은 규칙에 각자 25개(합산 50, 기존 시드 유지)를 둔다.
-- ---------------------------------------------------------------------
ALTER TABLE `RV_REVEAL_POLICY`
    ADD COLUMN `MIN_HOURS_SINCE_MATCH` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '매칭 후 최소 경과 시간(시간). 구독자는 스킵' AFTER `MIN_CHAT_MINUTES`,
    ADD COLUMN `MIN_MESSAGES_PER_USER` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '양측 각자 최소 메시지 수' AFTER `MIN_HOURS_SINCE_MATCH`;

UPDATE `RV_REVEAL_POLICY`
   SET `REVEAL_NAME` = '실루엣',
       `UPDATE_USER` = 'SYSTEM', `UPDATE_DATE` = CURRENT_TIMESTAMP(6)
 WHERE `REVEAL_LEVEL` = 0;

UPDATE `RV_REVEAL_POLICY`
   SET `REVEAL_NAME` = '부분 공개',
       `MIN_MESSAGE_COUNT` = 20, `MIN_CHAT_MINUTES` = 0,
       `MIN_HOURS_SINCE_MATCH` = 24, `MIN_MESSAGES_PER_USER` = 10,
       `MUTUAL_CONSENT_YN` = 'Y',
       `DISCLOSE_SCOPE_JSON` = JSON_OBJECT('image','blurred','name','partial','height','visible'),
       `UPDATE_USER` = 'SYSTEM', `UPDATE_DATE` = CURRENT_TIMESTAMP(6)
 WHERE `REVEAL_LEVEL` = 1;

UPDATE `RV_REVEAL_POLICY`
   SET `REVEAL_NAME` = '전체 공개',
       `MIN_MESSAGE_COUNT` = 50, `MIN_CHAT_MINUTES` = 0,
       `MIN_HOURS_SINCE_MATCH` = 24, `MIN_MESSAGES_PER_USER` = 25,
       `MUTUAL_CONSENT_YN` = 'Y',
       `UPDATE_USER` = 'SYSTEM', `UPDATE_DATE` = CURRENT_TIMESTAMP(6)
 WHERE `REVEAL_LEVEL` = 2;
