package com.bma.safety.repository;

import com.bma.safety.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link AdminAuditLog} 저장소.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * 신고 1건에 대한 감사 로그(시간순).
     */
    List<AdminAuditLog> findByReportIdAndDeletedOrderByIdAsc(Long reportId, String deleted);

    /**
     * 감사 로그 목록(최신순). 대상 사용자·신고로 좁힐 수 있다.
     */
    @Query("""
            select a from AdminAuditLog a
            where a.deleted = 'N'
              and (:targetUserId is null or a.targetUserId = :targetUserId)
              and (:reportId is null or a.reportId = :reportId)
            order by a.id desc
            """)
    Page<AdminAuditLog> search(@Param("targetUserId") Long targetUserId,
                               @Param("reportId") Long reportId,
                               Pageable pageable);
}
