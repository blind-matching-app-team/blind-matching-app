-- ---------------------------------------------------------------------
-- US_PROFILE_IMAGE 를 공개 URL 방식에서 저장소 오브젝트 키 방식으로 변경한다.
--
-- 주의: docker-entrypoint-initdb.d 는 파일마다 별도의 mysql 세션으로 실행되며
--       기본 데이터베이스는 MYSQL_DATABASE 값이다. 이전 버전에는 USE 문이 없어
--       스크립트가 엉뚱한(또는 비어 있는) 데이터베이스에서 실행되며 실패했고,
--       그 결과 애플리케이션이 요구하는 ORIGINAL_OBJECT_KEY 컬럼이 생기지 않아
--       ddl-auto=validate 가 기동을 막았다. 대상 DB 를 명시한다.
-- ---------------------------------------------------------------------
USE `bma`;

ALTER TABLE US_PROFILE_IMAGE
    DROP COLUMN ORIGINAL_URL,
    DROP COLUMN BLURRED_URL,
    DROP COLUMN SILHOUETTE_URL,
    DROP COLUMN IMAGE_TYPE,
    -- 로컬 저장소 구현이 기본이므로 기본값을 LOCAL 로 둔다(S3 도입 시 변경).
    ADD COLUMN STORAGE_TYPE VARCHAR(20) NOT NULL DEFAULT 'LOCAL' AFTER USER_ID,
    ADD COLUMN ORIGINAL_OBJECT_KEY VARCHAR(500) NOT NULL AFTER STORAGE_TYPE,
    -- 블러/실루엣 이미지는 후처리로 생성되므로 처음에는 NULL 이다.
    -- ProfileMaskingService 는 해당 단계의 키가 없으면 그 이미지를 목록에서 제외한다.
    ADD COLUMN BLURRED_OBJECT_KEY VARCHAR(500) NULL AFTER ORIGINAL_OBJECT_KEY,
    ADD COLUMN SILHOUETTE_OBJECT_KEY VARCHAR(500) NULL AFTER BLURRED_OBJECT_KEY,
    ADD COLUMN ORIGINAL_FILE_NAME VARCHAR(255) NULL AFTER SILHOUETTE_OBJECT_KEY,
    ADD COLUMN CONTENT_TYPE VARCHAR(100) NULL AFTER ORIGINAL_FILE_NAME,
    ADD COLUMN FILE_SIZE BIGINT UNSIGNED NULL AFTER CONTENT_TYPE,
    ADD COLUMN FILE_HASH VARCHAR(64) NULL AFTER FILE_SIZE,
    ADD COLUMN IMAGE_WIDTH INT UNSIGNED NULL AFTER FILE_HASH,
    ADD COLUMN IMAGE_HEIGHT INT UNSIGNED NULL AFTER IMAGE_WIDTH;
