package com.bma.safety.repository;

import com.bma.safety.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * {@link UserReport} 저장소.
 */
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    /**
     * 같은 대상에 대한 최근 중복 신고가 있는지 확인한다(매칭이 없는 경우의 보조 규칙).
     *
     * @param reportUserId 신고자
     * @param targetUserId 피신고자
     * @param since        기준 시각
     * @param deleted      논리 삭제 여부
     * @return 최근 신고가 있으면 {@code true}
     */
    boolean existsByReportUserIdAndTargetUserIdAndInsertDateAfterAndDeleted(
            Long reportUserId, Long targetUserId, LocalDateTime since, String deleted);

    /**
     * 같은 매칭에 대해 이미 신고했는지 확인한다(BMA-30: 매칭 1건당 1회).
     *
     * @param reportUserId 신고자
     * @param targetUserId 피신고자
     * @param matchId      매칭 ID
     * @param deleted      논리 삭제 여부
     * @return 이미 신고했으면 {@code true}
     */
    boolean existsByReportUserIdAndTargetUserIdAndMatchIdAndDeleted(
            Long reportUserId, Long targetUserId, Long matchId, String deleted);

    /**
     * 피신고자의 누적 반영 횟수(자동 반영 + 관리자 유효 판정).
     *
     * @param targetUserId 피신고자
     * @param statuses     반영으로 치는 상태들
     * @param deleted      논리 삭제 여부
     * @return 누적 횟수
     */
    long countByTargetUserIdAndReportStatusInAndDeleted(Long targetUserId, Collection<String> statuses, String deleted);

    /**
     * 즉시검토(중대 유형) 대기 신고가 있는지 확인한다. 있으면 새 매칭 진입이 막힌다.
     *
     * @param targetUserId 피신고자
     * @param severity     심각도
     * @param reportStatus 상태
     * @param deleted      논리 삭제 여부
     * @return 대기 신고가 있으면 {@code true}
     */
    boolean existsByTargetUserIdAndSeverityAndReportStatusAndDeleted(
            Long targetUserId, String severity, String reportStatus, String deleted);

    /**
     * 상태별 신고 목록(최신순, S12 목록 탭).
     */
    Page<UserReport> findByReportStatusInAndDeletedOrderByIdDesc(Collection<String> statuses, String deleted, Pageable pageable);

    /**
     * 피신고자의 신고 이력(최신순, S12 상세).
     */
    List<UserReport> findByTargetUserIdAndDeletedOrderByIdDesc(Long targetUserId, String deleted);
}
