package com.bma.notification.repository;

import com.bma.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * {@link Notification} 저장소.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 사용자의 알림을 최신순으로 페이지 조회한다.
     *
     * @param userId   수신자
     * @param deleted  논리 삭제 여부
     * @param pageable 페이지 정보
     * @return 알림 페이지
     */
    Page<Notification> findByUserIdAndDeletedOrderByIdDesc(Long userId, String deleted, Pageable pageable);

    /**
     * 소유자까지 지정해 알림을 조회한다. 타인의 알림을 읽음 처리하는 것을 막는다.
     *
     * @param id      알림 ID
     * @param userId  수신자
     * @param deleted 논리 삭제 여부
     * @return 알림
     */
    Optional<Notification> findByIdAndUserIdAndDeleted(Long id, Long userId, String deleted);

    /**
     * 읽지 않은 알림 수를 센다.
     *
     * @param userId  수신자
     * @param readYn  읽음 여부
     * @param deleted 논리 삭제 여부
     * @return 개수
     */
    long countByUserIdAndReadYnAndDeleted(Long userId, String readYn, String deleted);

    /**
     * 사용자의 안 읽은 알림을 한 번에 읽음 처리한다.
     *
     * <p>기존 구현은 전체를 엔티티로 로딩한 뒤 하나씩 갱신해, 알림이 많으면
     * 그만큼 UPDATE가 발생했다. 벌크 업데이트 한 방으로 바꾼다.</p>
     *
     * @param userId 수신자
     * @param readAt 읽음 처리 시각
     * @return 갱신된 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.readYn = 'Y', n.readDate = :readAt
             where n.userId = :userId and n.readYn = 'N' and n.deleted = 'N'
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
