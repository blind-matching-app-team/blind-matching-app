-- =====================================================================
-- V9: 알림 사건 코드 (BMA-53)
--
-- S7 사양서 v1.7 은 알림을 사건 단위로 구분한다(S7-11 미인증 상대, S7-12 다음 단계 요청,
-- S7-13 경고 조치, S7-14 매칭 종료 …). 기존 NOTIFICATION_TYPE(MATCH/MESSAGE/REVEAL/
-- REPORT/SYSTEM)은 큰 분류라 "클릭 시 유형별 화면 이동"(S7-09)과 아이콘 구분에 부족하다.
-- 사건 코드를 컬럼으로 두고, 분류·이동 화면은 애플리케이션(NotificationEvent)이 파생한다.
--
-- 적용이 끝난 마이그레이션은 수정하지 않는다. 내용을 바꿔야 하면
-- 새 버전을 만든다(backend/docs/DB_MIGRATION.md 참고).
-- =====================================================================

ALTER TABLE `NT_NOTIFICATION`
    ADD COLUMN `EVENT_CODE` VARCHAR(40) NULL
        COMMENT '알림 사건 코드(NotificationEvent). MATCH_CREATED/MESSAGE_RECEIVED/REVEAL_REQUESTED 등'
        AFTER `NOTIFICATION_TYPE`;

-- 이미 쌓인 알림은 제목으로 사건을 되짚어 채운다(개발 데이터 정리용).
-- 매칭되지 않는 행은 NULL 로 남고, 응답에서는 SYSTEM 으로 취급된다.
UPDATE `NT_NOTIFICATION` SET `EVENT_CODE` = 'MATCH_LIKED'      WHERE `EVENT_CODE` IS NULL AND `TITLE` = '누군가 회원님에게 호감을 보냈어요';
UPDATE `NT_NOTIFICATION` SET `EVENT_CODE` = 'MATCH_CREATED'    WHERE `EVENT_CODE` IS NULL AND `TITLE` = '매칭이 성사되었어요!';
UPDATE `NT_NOTIFICATION` SET `EVENT_CODE` = 'MATCH_ENDED'      WHERE `EVENT_CODE` IS NULL AND `TITLE` IN ('매칭이 종료되었어요', '매칭이 종료됐어요');
UPDATE `NT_NOTIFICATION` SET `EVENT_CODE` = 'REVEAL_LEVEL_UP'  WHERE `EVENT_CODE` IS NULL AND `TITLE` = '프로필 공개 단계가 올라갔어요';
UPDATE `NT_NOTIFICATION` SET `EVENT_CODE` = 'REVEAL_REQUESTED' WHERE `EVENT_CODE` IS NULL AND `TITLE` = '상대가 프로필 공개를 원해요';
UPDATE `NT_NOTIFICATION` SET `EVENT_CODE` = 'MESSAGE_RECEIVED' WHERE `EVENT_CODE` IS NULL AND `TITLE` = '새 메시지가 도착했어요';
