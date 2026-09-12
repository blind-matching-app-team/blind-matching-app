-- =====================================================================
-- V8: S4 매칭 선호조건 — 키 필터 제거 (BMA-44)
--
-- BMA-19 안건2(2026-08-18 확정): 키는 "외모보다 대화" 컨셉과 충돌하는
-- 요소로 판단해 매칭 선호조건 필터에서 영구 제외한다. 기술 미확정이
-- 아니라 의도된 설계이므로 컬럼을 남겨 두지 않고 지운다.
-- (키 자체는 US_USER_PROFILE.HEIGHT_CM 에 선택 입력으로 남아 있고,
--  Reveal 2단계부터 노출된다. 여기서 지우는 것은 "상대에게 요구하는 키"다.)
--
-- 적용이 끝난 마이그레이션은 수정하지 않는다. 내용을 바꿔야 하면
-- 새 버전을 만든다(backend/docs/DB_MIGRATION.md 참고).
-- =====================================================================

-- CHECK 제약이 컬럼을 참조하므로 제약을 먼저 지워야 컬럼을 지울 수 있다.
ALTER TABLE `US_USER_PREFERENCE`
    DROP CONSTRAINT `CK_US_PREF_HEIGHT`;

ALTER TABLE `US_USER_PREFERENCE`
    DROP COLUMN `MIN_HEIGHT_CM`,
    DROP COLUMN `MAX_HEIGHT_CM`;

-- 희망 지역은 S4-04 사양(v1.5)대로 시/군/구 하나 또는 "서울 전체"처럼
-- 시/도 하나를 담는다. 시/도 코드가 들어오면 그 하위 시/군/구 전체를 뜻한다.
ALTER TABLE `US_USER_PREFERENCE`
    MODIFY COLUMN `PREFERRED_REGION_CODE` VARCHAR(30) NULL
        COMMENT '희망 지역(CM_REGION 코드). 시/도 코드면 하위 시/군/구 전체를 뜻한다';
