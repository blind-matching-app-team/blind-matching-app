package com.bma.matching.repository;

import com.bma.common.entity.QRegion;
import com.bma.common.entity.YesNo;
import com.bma.user.entity.QUserPreference;
import com.bma.user.entity.QUserProfile;
import com.bma.user.entity.UserPreference;
import com.bma.user.entity.UserProfile;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 추천 후보 조회 전용 QueryDSL 저장소.
 *
 * <p>고친 점 — 기존 구현은 아래 한 줄이 전부였다.</p>
 * <pre>
 * selectFrom(userProfile)
 *   .where(id.ne(userId), deleted.eq("N"), profileStatus.eq("COMPLETE"))
 *   .orderBy(id.desc()).limit(size)
 * </pre>
 * <p>그 결과</p>
 * <ul>
 *   <li>선호 조건(성별/나이/지역)이 전혀 반영되지 않았다.</li>
 *   <li>이미 좋아요/패스한 상대, 차단한 상대, 이미 매칭된 상대가 계속 다시 나왔다.</li>
 *   <li>매칭 참여를 끈 사용자도 추천됐다.</li>
 *   <li>{@code id desc} 고정이라 매번 같은 목록만 반환했다.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class MatchingQueryRepository {

    private static final QUserProfile PROFILE = QUserProfile.userProfile;
    private static final QUserPreference PREFERENCE = QUserPreference.userPreference;
    private static final QRegion REGION = QRegion.region;

    private final JPAQueryFactory queryFactory;

    /**
     * 추천 후보를 조회한다.
     *
     * @param userId       요청자 ID
     * @param preference   요청자의 선호 조건({@code null}이면 조건 없이 조회)
     * @param excludeUsers 제외할 사용자 ID 집합(이미 액션한 상대, 차단, 기존 매칭)
     * @param cursorScore  커서: 직전 페이지 마지막 항목의 완성도 점수({@code null}이면 처음부터)
     * @param cursorUserId 커서: 직전 페이지 마지막 항목의 사용자 ID
     * @param size         조회 건수
     * @return 추천 후보 프로필 목록
     */
    public List<UserProfile> findRecommendations(Long userId,
                                                 UserPreference preference,
                                                 Collection<Long> excludeUsers,
                                                 Integer cursorScore,
                                                 Long cursorUserId,
                                                 int size) {

        BooleanBuilder condition = new BooleanBuilder()
                .and(PROFILE.id.ne(userId))
                .and(PROFILE.deleted.eq(YesNo.N))
                .and(PROFILE.profileStatus.eq(UserProfile.STATUS_COMPLETE));

        // 이미 판단이 끝난 상대(좋아요/패스/차단/매칭)는 다시 보여주지 않는다.
        if (excludeUsers != null && !excludeUsers.isEmpty()) {
            condition.and(PROFILE.id.notIn(excludeUsers));
        }

        // 매칭 참여를 끈 사용자는 후보에서 제외한다.
        // 선호 조건 행이 아직 없는 사용자는 참여 중으로 간주하므로 notExists를 사용한다.
        condition.and(JPAExpressions.selectOne()
                .from(PREFERENCE)
                .where(PREFERENCE.id.eq(PROFILE.id),
                        PREFERENCE.matchingEnabledYn.eq(YesNo.N),
                        PREFERENCE.deleted.eq(YesNo.N))
                .notExists());

        applyPreferenceFilters(condition, preference);
        applyCursor(condition, cursorScore, cursorUserId);

        return queryFactory
                .selectFrom(PROFILE)
                .where(condition)
                // 완성도가 높은 프로필을 먼저 보여주고, 동점이면 ID 내림차순으로 안정 정렬한다.
                .orderBy(PROFILE.profileScore.desc(), PROFILE.id.desc())
                .limit(size)
                .fetch();
    }

    /**
     * 요청자의 선호 조건을 검색 조건으로 변환한다.
     *
     * @param condition  누적 조건
     * @param preference 선호 조건
     */
    private void applyPreferenceFilters(BooleanBuilder condition, UserPreference preference) {
        if (preference == null) {
            return;
        }

        if (hasText(preference.getPreferredGenderCode())) {
            condition.and(PROFILE.genderCode.eq(preference.getPreferredGenderCode()));
        }

        // 나이는 생년월일에서 파생되므로, SQL 날짜 함수 대신 자바에서 생년월일 범위로 환산한다.
        // (DB 함수 사용을 피하면 인덱스도 그대로 활용할 수 있다.)
        LocalDate today = LocalDate.now();
        if (preference.getMinAge() != null) {
            // 만 minAge세 이상 → 생년월일이 (오늘 - minAge년) 이전
            condition.and(PROFILE.birthDate.loe(today.minusYears(preference.getMinAge())));
        }
        if (preference.getMaxAge() != null) {
            // 만 maxAge세 이하 → 생년월일이 (오늘 - (maxAge+1)년 + 1일) 이후
            condition.and(PROFILE.birthDate.goe(
                    today.minusYears(preference.getMaxAge() + 1L).plusDays(1)));
        }

        // 키 조건은 없다. BMA-19 안건2 에서 매칭 필터에서 영구 제외했다(V8 에서 컬럼 삭제).

        if (hasText(preference.getPreferredRegionCode())) {
            condition.and(inPreferredRegion(preference.getPreferredRegionCode()));
        }
    }

    /**
     * 희망 지역 조건.
     *
     * <p>희망 지역은 시/군/구 하나이거나 "서울 전체"처럼 시/도 하나다(S4-04).
     * 시/도면 그 하위 시/군/구에 사는 후보를 모두 포함해야 하므로 {@code CM_REGION} 의
     * 상위 관계로 푼다. 코드 접두 일치({@code startsWith})는 쓰지 않는다 —
     * {@code GWANGJU}(광주광역시)가 {@code GYEONGGI_GWANGJU}(경기 광주시)와 섞이는 식의
     * 우연한 문자열 일치를 데이터 관계로 막기 위해서다.</p>
     *
     * @param regionCode 희망 지역 코드(시/도 또는 시/군/구)
     * @return 후보의 활동 지역이 희망 지역 자체이거나 그 하위인 조건
     */
    private BooleanExpression inPreferredRegion(String regionCode) {
        return PROFILE.regionCode.eq(regionCode)
                .or(PROFILE.regionCode.in(JPAExpressions
                        .select(REGION.code)
                        .from(REGION)
                        .where(REGION.parentCode.eq(regionCode),
                                REGION.useYn.eq(YesNo.Y),
                                REGION.deleted.eq(YesNo.N))));
    }

    /**
     * 커서 기반 페이지 조건을 적용한다.
     *
     * <p>offset 방식은 목록이 길어질수록 느려지고, 그 사이 데이터가 바뀌면 항목이
     * 중복되거나 누락된다. (점수, ID) 복합 커서로 안정적으로 이어서 조회한다.</p>
     *
     * @param condition    누적 조건
     * @param cursorScore  직전 마지막 항목의 완성도 점수
     * @param cursorUserId 직전 마지막 항목의 사용자 ID
     */
    private void applyCursor(BooleanBuilder condition, Integer cursorScore, Long cursorUserId) {
        if (cursorScore == null || cursorUserId == null) {
            return;
        }
        // (score, id) < (cursorScore, cursorId) 를 표현한다.
        condition.and(PROFILE.profileScore.lt(cursorScore)
                .or(PROFILE.profileScore.eq(cursorScore).and(PROFILE.id.lt(cursorUserId))));
    }

    /**
     * 문자열이 비어 있지 않은지 확인한다.
     *
     * @param value 확인할 값
     * @return 값이 있으면 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
