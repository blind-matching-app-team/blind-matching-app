-- =====================================================================
-- V6: S3 프로필 설정에 필요한 스키마 변경 (BMA-40 계열)
--
-- 1) CM_REGION  — 지역을 시/도 → 시/군/구 2단계로 다룬다.
-- 2) US_USER_PROFILE.GENDER_CODE 를 NULL 허용으로 바꾼다.
--
-- 적용이 끝난 마이그레이션은 수정하지 않는다. 내용을 바꿔야 하면
-- 새 버전을 만든다(backend/docs/DB_MIGRATION.md 참고).
-- =====================================================================

-- ---------------------------------------------------------------------
-- CM_REGION: 행정구역 (시/도 → 시/군/구)
--
-- CM_CODE 에 넣지 않은 이유는 상위-하위 관계를 표현할 컬럼이 없어서다.
-- CODE_VALUE 에 부모 코드를 욱여넣을 수도 있지만, 행정구역은 개편이
-- 잦아(2023 강원특별자치도, 2024 전북특별자치도, 군위군 대구 편입)
-- 자체 수명주기를 갖는 참조 데이터라 별도 테이블로 둔다.
--
-- REGION_CODE 는 내부 식별자(로마자)다. 행정안전부 표준 행정구역 코드는
-- 데이터 소스가 확정되면 LEGAL_CODE 에 채운다. 티켓 기준으로 아직 미확정이다.
-- ---------------------------------------------------------------------
CREATE TABLE `CM_REGION` (
    `REGION_CODE`        VARCHAR(30)      NOT NULL COMMENT '지역 코드(내부 식별자)',
    `PARENT_REGION_CODE` VARCHAR(30)      NULL COMMENT '상위 지역 코드. 시/도 자신은 NULL',
    `REGION_NAME`        VARCHAR(100)     NOT NULL COMMENT '지역명',
    `REGION_LEVEL`       TINYINT UNSIGNED NOT NULL COMMENT '1=시/도, 2=시/군/구',
    `LEGAL_CODE`         VARCHAR(10)      NULL COMMENT '행정안전부 표준 행정구역 코드. 데이터 소스 확정 후 채운다',
    `SORT_ORDER`         INT              NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    `USE_YN`             CHAR(1)          NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
    `DELETED`            CHAR(1)          NOT NULL DEFAULT 'N' COMMENT '논리 삭제 여부(Y/N)',
    `INSERT_USER`        VARCHAR(50)      NOT NULL COMMENT '최초 등록 사용자',
    `INSERT_DATE`        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '최초 등록 일시',
    `UPDATE_USER`        VARCHAR(50)      NULL COMMENT '최종 수정 사용자',
    `UPDATE_DATE`        DATETIME(6)      NULL COMMENT '최종 수정 일시',
    `SYSTEM_REMARK`      VARCHAR(1000)    NULL COMMENT '시스템 처리 및 운영 메모',
    PRIMARY KEY (`REGION_CODE`),
    INDEX `IX_CM_REGION_PARENT` (`PARENT_REGION_CODE`, `SORT_ORDER`),
    INDEX `IX_CM_REGION_LEVEL` (`REGION_LEVEL`, `SORT_ORDER`),
    CONSTRAINT `FK_CM_REGION_PARENT` FOREIGN KEY (`PARENT_REGION_CODE`)
        REFERENCES `CM_REGION` (`REGION_CODE`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='행정구역 (시/도 → 시/군/구)';

-- ---------------------------------------------------------------------
-- US_USER_PROFILE.GENDER_CODE 를 NULL 허용으로
--
-- S3 프로필 설정 화면에 성별 입력란이 없고 S1 회원가입에서도 받지 않는다.
-- NOT NULL 로 두면 화면을 다 채워도 프로필을 저장할 수 없어, 프론트가
-- profileCompleted=false 를 보고 S3 로 되돌리는 무한 루프에 빠진다.
--
-- 성별은 매칭(S4)에는 여전히 필요하므로 프로필 완성 판정에서만 빼고
-- 컬럼 자체는 남긴다. 수집 시점(S15 본인인증 등)은 별도 확정 사항이다.
-- ---------------------------------------------------------------------
ALTER TABLE `US_USER_PROFILE`
    MODIFY COLUMN `GENDER_CODE` VARCHAR(20) NULL COMMENT '성별 코드. 매칭에 필요하나 수집 시점 미확정';

-- ---------------------------------------------------------------------
-- 지역 코드를 담는 컬럼들을 VARCHAR(30) 으로 넓힌다
--
-- 기존 VARCHAR(20) 은 시드 데이터를 담기에 모자란다
-- (GYEONGNAM_CHANGNYEONG 가 21자). 코드를 줄여 맞출 수도 있지만,
-- 행정구역 개편으로 이름이 길어지면 같은 문제가 다시 난다.
-- ---------------------------------------------------------------------
ALTER TABLE `US_USER_PROFILE`
    MODIFY COLUMN `REGION_CODE` VARCHAR(30) NULL COMMENT '활동 지역 코드(CM_REGION 의 시/군/구)';

ALTER TABLE `US_USER_PREFERENCE`
    MODIFY COLUMN `PREFERRED_REGION_CODE` VARCHAR(30) NULL COMMENT '선호 지역(CM_REGION 의 시/군/구)';
