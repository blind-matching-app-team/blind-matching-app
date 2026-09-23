package com.bma.safety.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BMA-30 신고 정책(유형 5종·심각도·자동 반영 vs 관리자 검토·즉시검토 매칭 제한)을 엔티티 수준에서 고정한다.
 */
class UserReportPolicyTest {

    @Test
    @DisplayName("사기·부적절한 콘텐츠만 중대 유형이다")
    void severeTypes() {
        assertThat(UserReport.isSevereType("FRAUD")).isTrue();
        assertThat(UserReport.isSevereType("SEXUAL")).isTrue();
        assertThat(UserReport.isSevereType("ABUSE")).isFalse();
        assertThat(UserReport.isSevereType("FAKE")).isFalse();
        assertThat(UserReport.isSevereType("ETC")).isFalse();
    }

    @Test
    @DisplayName("일반 유형: 누적 1~2회는 자동 반영(COUNTED), 3회째부터 관리자 검토(PENDING_REVIEW)")
    void normalTypeCountsAutomaticallyUpToTwo() {
        assertThat(UserReport.decideStatus("ABUSE", 0)).isEqualTo(UserReport.STATUS_COUNTED);
        assertThat(UserReport.decideStatus("FAKE", 1)).isEqualTo(UserReport.STATUS_COUNTED);
        assertThat(UserReport.decideStatus("ETC", 2)).isEqualTo(UserReport.STATUS_PENDING_REVIEW);
        assertThat(UserReport.decideStatus("ABUSE", 6)).isEqualTo(UserReport.STATUS_PENDING_REVIEW);
    }

    @Test
    @DisplayName("중대 유형은 누적과 무관하게 1회로 즉시 관리자 검토")
    void severeTypeGoesToReviewImmediately() {
        assertThat(UserReport.decideStatus("FRAUD", 0)).isEqualTo(UserReport.STATUS_PENDING_REVIEW);
        assertThat(UserReport.decideStatus("SEXUAL", 0)).isEqualTo(UserReport.STATUS_PENDING_REVIEW);
    }

    @Test
    @DisplayName("즉시검토 대기(중대 + PENDING_REVIEW)만 새 매칭 진입을 막는다")
    void onlySeverePendingHoldsMatching() {
        UserReport severe = UserReport.of(1L, 2L, "FRAUD", null, 10L, null, 0);
        assertThat(severe.getSeverity()).isEqualTo(UserReport.SEVERITY_SEVERE);
        assertThat(severe.holdsMatching()).isTrue();

        UserReport thirdNormal = UserReport.of(1L, 2L, "ABUSE", "욕설", 10L, 55L, 2);
        assertThat(thirdNormal.getSeverity()).isEqualTo(UserReport.SEVERITY_NORMAL);
        assertThat(thirdNormal.isPendingReview()).isTrue();
        assertThat(thirdNormal.holdsMatching()).isFalse();

        UserReport firstNormal = UserReport.of(1L, 2L, "ETC", null, null, null, 0);
        assertThat(firstNormal.getReportStatus()).isEqualTo(UserReport.STATUS_COUNTED);
        assertThat(firstNormal.holdsMatching()).isFalse();

        severe.setReportStatus(UserReport.STATUS_REJECTED);
        assertThat(severe.holdsMatching()).isFalse();
    }

    @Test
    @DisplayName("누적 반영으로 치는 상태에는 구 RECEIVED 와 관리자 유효 판정(RESOLVED)이 포함된다")
    void countedStatuses() {
        assertThat(UserReport.COUNTED_STATUSES)
                .contains(UserReport.STATUS_RECEIVED, UserReport.STATUS_COUNTED, UserReport.STATUS_RESOLVED)
                .doesNotContain(UserReport.STATUS_PENDING_REVIEW, UserReport.STATUS_REJECTED);
    }
}
