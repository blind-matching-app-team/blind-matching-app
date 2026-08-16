package com.bma.notification.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.response.PageResponse;
import com.bma.notification.dto.NotificationDtos.NotificationResponse;
import com.bma.notification.entity.Notification;
import com.bma.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인앱 알림 발행 및 조회.
 *
 * <p>매칭 성사, 공개 단계 상승 같은 사건이 발생하면 각 도메인 서비스가
 * 이 서비스를 통해 알림을 남긴다. 기존 구현에는 알림을 "만드는" 코드가 아예 없어
 * 알림 목록이 항상 비어 있었다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 알림 목록을 조회한다.
     *
     * @param userId 수신자
     * @param page   페이지 번호(0부터)
     * @param size   페이지 크기(최대 100)
     * @return 알림 페이지
     */
    public PageResponse<NotificationResponse> getNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.of(
                notificationRepository.findByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N, pageable),
                NotificationResponse::from);
    }

    /**
     * 안 읽은 알림 수를 조회한다. 배지 표시에 사용한다.
     *
     * @param userId 수신자
     * @return 안 읽은 알림 수
     */
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadYnAndDeleted(userId, YesNo.N, YesNo.N);
    }

    /**
     * 알림 1건을 읽음 처리한다.
     *
     * @param userId         요청자
     * @param notificationId 알림 ID
     * @throws BusinessException 본인의 알림이 아니거나 존재하지 않는 경우
     */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        // 소유자 조건을 쿼리에 포함해 타인 알림의 존재 여부가 드러나지 않게 한다.
        Notification notification = notificationRepository
                .findByIdAndUserIdAndDeleted(notificationId, userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        notification.markRead();
    }

    /**
     * 안 읽은 알림을 모두 읽음 처리한다.
     *
     * @param userId 요청자
     * @return 처리된 건수
     */
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    /**
     * 알림 1건을 발행한다.
     *
     * @param userId        수신자
     * @param type          알림 유형
     * @param title         제목
     * @param content       내용
     * @param referenceType 참조 유형
     * @param referenceId   참조 ID
     */
    @Transactional
    public void notify(Long userId, String type, String title, String content,
                       String referenceType, Long referenceId) {
        notificationRepository.save(
                Notification.of(userId, type, title, content, referenceType, referenceId));
        log.debug("알림 발행: userId={}, type={}, ref={}:{}", userId, type, referenceType, referenceId);
    }

    /**
     * 여러 사용자에게 같은 알림을 발행한다.
     *
     * <p>매칭 성사처럼 양쪽 모두에게 알려야 하는 경우에 사용한다.</p>
     *
     * @param userIds       수신자 목록
     * @param type          알림 유형
     * @param title         제목
     * @param content       내용
     * @param referenceType 참조 유형
     * @param referenceId   참조 ID
     */
    @Transactional
    public void notifyAll(List<Long> userIds, String type, String title, String content,
                          String referenceType, Long referenceId) {
        List<Notification> notifications = userIds.stream()
                .map(userId -> Notification.of(userId, type, title, content, referenceType, referenceId))
                .toList();
        notificationRepository.saveAll(notifications);
    }
}
