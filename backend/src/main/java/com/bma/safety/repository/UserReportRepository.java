package com.bma.safety.repository;

import com.bma.safety.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

/**
 * {@link UserReport} 저장소.
 */
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    /**
     * 같은 대상에 대한 최근 중복 신고가 있는지 확인한다.
     *
     * <p>신고 버튼 연타나 악의적인 반복 신고로 테이블이 오염되는 것을 막는다.</p>
     *
     * @param reportUserId 신고자
     * @param targetUserId 피신고자
     * @param since        기준 시각
     * @param deleted      논리 삭제 여부
     * @return 최근 신고가 있으면 {@code true}
     */
    boolean existsByReportUserIdAndTargetUserIdAndInsertDateAfterAndDeleted(
            Long reportUserId, Long targetUserId, LocalDateTime since, String deleted);
}
