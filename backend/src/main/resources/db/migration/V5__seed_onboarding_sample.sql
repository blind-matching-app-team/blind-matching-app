-- =====================================================================
-- V5: 온보딩 예시 질문 (BMA-38)
--
-- 실제 문항 콘텐츠는 아직 미확정이다(허익 단독 판단으로 추후 확정).
-- 프론트(BMA-37)가 화면을 붙여볼 수 있도록 S2 사양서 v1.4 의 구조를
-- 그대로 재현한 예시만 넣는다. 콘텐츠가 확정되면 이 데이터를 지우고
-- 새 마이그레이션으로 실제 문항을 넣는다.
--
-- 재현하는 구조
--   - 1구간: 관심사(MULTI) — 대분류 7개 + 대분류별 세부 항목, 우선순위 정렬 대상
--   - 2~4구간: 2~3지선다 단일 선택
--   두 유형이 다 있어야 프론트가 관심사 통합화면과 일반 문항을 모두 검증할 수 있다.
--
-- 사양서의 총 7구간 22문항을 다 넣지 않는 이유: 실제 콘텐츠가 아니라
-- 어차피 교체될 값이고, 구조 검증에는 유형별 대표 문항이면 충분하다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1구간: 관심사 (다중 선택 + 세부 + 우선순위)
-- ---------------------------------------------------------------------
INSERT INTO `ON_QUESTION`
(`QUESTION_TYPE`, `CATEGORY_CODE`, `QUESTION_TEXT`, `REQUIRED_YN`, `MATCH_WEIGHT`,
 `SORT_ORDER`, `STEP_NO`, `USE_YN`, `INSERT_USER`)
VALUES
('MULTI', 'INTEREST', '평소 관심 있는 분야를 골라주세요 (중복 가능)', 'Y', 1.5000, 10, 1, 'Y', 'SYSTEM');

SET @q_interest = LAST_INSERT_ID();

-- 대분류 7개. 사양서 S2-10 의 칩 목록과 같다.
INSERT INTO `ON_QUESTION_OPTION`
(`QUESTION_ID`, `PARENT_OPTION_ID`, `OPTION_CODE`, `OPTION_TEXT`, `SORT_ORDER`, `INSERT_USER`)
VALUES
(@q_interest, NULL, 'CULTURE',  '문화생활',    1, 'SYSTEM'),
(@q_interest, NULL, 'FITNESS',  '운동/건강',   2, 'SYSTEM'),
(@q_interest, NULL, 'SPORTS',   '스포츠',      3, 'SYSTEM'),
(@q_interest, NULL, 'SELFDEV',  '자기계발',    4, 'SYSTEM'),
(@q_interest, NULL, 'FOOD',     '푸드/카페',   5, 'SYSTEM'),
(@q_interest, NULL, 'TRAVEL',   '여행',        6, 'SYSTEM'),
(@q_interest, NULL, 'GAME',     '게임/디지털', 7, 'SYSTEM');

-- 세부 항목. 대분류를 부모로 갖는다.
-- OPTION_CODE 로 부모를 찾아 넣어야 ID 하드코딩을 피할 수 있다.
INSERT INTO `ON_QUESTION_OPTION`
(`QUESTION_ID`, `PARENT_OPTION_ID`, `OPTION_CODE`, `OPTION_TEXT`, `SORT_ORDER`, `INSERT_USER`)
SELECT @q_interest, parent.`OPTION_ID`, child.`code`, child.`text`, child.`sort`, 'SYSTEM'
FROM (
    SELECT 'CULTURE' AS parent_code, 'CULTURE_MOVIE'   AS `code`, '영화'        AS `text`, 1 AS `sort`
    UNION ALL SELECT 'CULTURE', 'CULTURE_EXHIBIT', '전시/미술관', 2
    UNION ALL SELECT 'CULTURE', 'CULTURE_MUSICAL', '공연/뮤지컬', 3
    UNION ALL SELECT 'CULTURE', 'CULTURE_CONCERT', '콘서트',      4
    UNION ALL SELECT 'CULTURE', 'CULTURE_READING', '독서',        5
    UNION ALL SELECT 'TRAVEL',  'TRAVEL_DOMESTIC', '국내여행',    1
    UNION ALL SELECT 'TRAVEL',  'TRAVEL_ABROAD',   '해외여행',    2
    UNION ALL SELECT 'TRAVEL',  'TRAVEL_CAMPING',  '캠핑/차박',   3
    UNION ALL SELECT 'FITNESS', 'FITNESS_GYM',     '헬스',        1
    UNION ALL SELECT 'FITNESS', 'FITNESS_YOGA',    '요가/필라테스', 2
    UNION ALL SELECT 'FOOD',    'FOOD_CAFE',       '카페 투어',   1
    UNION ALL SELECT 'FOOD',    'FOOD_COOKING',    '요리',        2
) AS child
JOIN `ON_QUESTION_OPTION` AS parent
  ON parent.`QUESTION_ID` = @q_interest
 AND parent.`OPTION_CODE` = child.parent_code
 AND parent.`PARENT_OPTION_ID` IS NULL;

-- ---------------------------------------------------------------------
-- 2구간: 가치관 (2지선다)
-- ---------------------------------------------------------------------
INSERT INTO `ON_QUESTION`
(`QUESTION_TYPE`, `CATEGORY_CODE`, `QUESTION_TEXT`, `REQUIRED_YN`, `MATCH_WEIGHT`,
 `SORT_ORDER`, `STEP_NO`, `USE_YN`, `INSERT_USER`)
VALUES
('SINGLE', 'VALUES', '주말을 보내는 방식 중 더 끌리는 쪽은?', 'Y', 1.0000, 20, 2, 'Y', 'SYSTEM');

SET @q_weekend = LAST_INSERT_ID();

INSERT INTO `ON_QUESTION_OPTION`
(`QUESTION_ID`, `PARENT_OPTION_ID`, `OPTION_CODE`, `OPTION_TEXT`, `SCORE_VALUE`, `SORT_ORDER`, `INSERT_USER`)
VALUES
(@q_weekend, NULL, 'HOME', '집에서 조용히 나만의 시간',   -1.0000, 1, 'SYSTEM'),
(@q_weekend, NULL, 'OUT',  '밖에서 사람들과 활동적으로',   1.0000, 2, 'SYSTEM');

-- ---------------------------------------------------------------------
-- 3구간: 가치관 (2지선다)
-- ---------------------------------------------------------------------
INSERT INTO `ON_QUESTION`
(`QUESTION_TYPE`, `CATEGORY_CODE`, `QUESTION_TEXT`, `REQUIRED_YN`, `MATCH_WEIGHT`,
 `SORT_ORDER`, `STEP_NO`, `USE_YN`, `INSERT_USER`)
VALUES
('SINGLE', 'VALUES', '연애에 대해 어떻게 생각하시나요?', 'Y', 1.2000, 30, 3, 'Y', 'SYSTEM');

SET @q_dating = LAST_INSERT_ID();

INSERT INTO `ON_QUESTION_OPTION`
(`QUESTION_ID`, `PARENT_OPTION_ID`, `OPTION_CODE`, `OPTION_TEXT`, `SCORE_VALUE`, `SORT_ORDER`, `INSERT_USER`)
VALUES
(@q_dating, NULL, 'SERIOUS', '결혼을 전제로 만나고 싶다',      1.0000, 1, 'SYSTEM'),
(@q_dating, NULL, 'CASUAL',  '자유롭게 만나며 알아가고 싶다', -1.0000, 2, 'SYSTEM');

-- ---------------------------------------------------------------------
-- 4구간: 연애 성향 (3지선다)
-- ---------------------------------------------------------------------
INSERT INTO `ON_QUESTION`
(`QUESTION_TYPE`, `CATEGORY_CODE`, `QUESTION_TEXT`, `REQUIRED_YN`, `MATCH_WEIGHT`,
 `SORT_ORDER`, `STEP_NO`, `USE_YN`, `INSERT_USER`)
VALUES
('SINGLE', 'ROMANCE', '지금 어떤 만남을 찾고 있나요?', 'Y', 1.0000, 40, 4, 'Y', 'SYSTEM');

SET @q_seeking = LAST_INSERT_ID();

INSERT INTO `ON_QUESTION_OPTION`
(`QUESTION_ID`, `PARENT_OPTION_ID`, `OPTION_CODE`, `OPTION_TEXT`, `SCORE_VALUE`, `SORT_ORDER`, `INSERT_USER`)
VALUES
(@q_seeking, NULL, 'LIGHT',   '가볍게 알아가고 싶어요', -1.0000, 1, 'SYSTEM'),
(@q_seeking, NULL, 'SERIOUS', '진지한 연애를 원해요',    1.0000, 2, 'SYSTEM'),
(@q_seeking, NULL, 'UNSURE',  '아직 잘 모르겠어요',      0.0000, 3, 'SYSTEM');
