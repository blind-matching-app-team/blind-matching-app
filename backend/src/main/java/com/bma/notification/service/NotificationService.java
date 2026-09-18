package com.bma.notification.service;

import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.response.PageResponse;
import com.bma.notification.dto.NotificationDtos.NotificationResponse;
import com.bma.notification.dto.NotificationDtos.ReadAllResult;
import com.bma.notification.dto.NotificationDtos.ReadResult;
import com.bma.notification.entity.Notification;
import com.bma.notification.entity.NotificationEvent;
import com.bma.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인앱 알림 발행 및 조회 (S7).
 *
 * <p>매칭 성사, 공개 단계 상승 같은 사건이 발생하면 각 도메인 서비스가
 * 이 서비스를 통해 알림을 남긴다. 사건 종류는 {@link NotificationEvent} 로 고정한다 —
 * 프론트가 아이콘과 이동 화면을 그 코드로 정하기 때문이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 알림 목록을 조회한다 (최신순).
     *
     * @param userId     수신자
     * @param page       페이지 번호(0부터)
     * @param size       페이지 크기(최대 100)
     * @param unreadOnly {@code true} 면 안 읽은 알림만
     * @return 알림 페이지
     */
    public PageResponse<NotificationResponse> getNotifications(Long userId, int page, int size, boolean unreadOnly) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadYnAndDeletedOrderByIdDesc(userId, YesNo.N, YesNo.N, pageable)
                : notificationRepository.findByUserIdAndDeletedOrderByIdDesc(userId, YesNo.N, pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    /**
     * 안 읽은 알림 수를 조회한다. 사이드바 알림 배지에 쓴다.
     *
     * @param userId 수신자
     * @return 안 읽은 알림 수
     */
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadYnAndDeleted(userId, YesNo.N, YesNo.N);
    }

    /**
     * 알림 1건을 읽음 처리한다. 이미 읽은 알림은 그대로 둔다(멱등).
     *
     * @param userId         요청자
     * @param notificationId 알림 ID
     * @return 처리 결과(읽은 시각, 남은 안 읽은 수)
     * @throws BusinessException 본인의 알림이 아니거나 존재하지 않는 경우
     */
    @Transactional
    public ReadResult markRead(Long userId, Long notificationId) {
        // 소유자 조건을 쿼리에 포함해 타인 알림의 존재 여부가 드러나지 않게 한다.
        Notification notification = notificationRepository
                .findByIdAndUserIdAndDeleted(notificationId, userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        notification.markRead();
        // 같은 트랜잭션 안에서 세어도 되도록 방금 바뀐 행을 먼저 반영한다.
        notificationRepository.flush();
        return new ReadResult(notification.getId(), notification.getReadDate(), countUnread(userId));
    }

    /**
     * 안 읽은 알림을 모두 읽음 처리한다 (S7-08 전체 읽음).
     *
     * @param userId 요청자
     * @return 처리 결과(바뀐 건수, 남은 안 읽은 수 = 0)
     */
    @Transactional
    public ReadAllResult markAllRead(Long userId) {
        int updated = notificationRepository.markAllRead(userId, LocalDateTime.now());
        return new ReadAllResult(updated, countUnread(userId));
    }

    /**
     * 알림 1건을 발행한다.
     *
     * @param userId        수신자
     * @param event         알림 사건
     * @param title         제목(사양서 문구)
     * @param content       내용
     * @param referenceType 참조 유형(MATCH/CHAT_ROOM/USER)
     * @param referenceId   참조 ID
     */
    @Transactional
    public void notify(Long userId, NotificationEvent event, String title, String content,
                       String referenceType, Long referenceId) {
        notificationRepository.save(
                Notification.of(userId, event, title, content, referenceType, referenceId));
        log.debug("알림 발행: userId={}, event={}, ref={}:{}", userId, event, referenceType, referenceId);
    }

    /**
     * 여러 사용자에게 같은 알림을 발행한다.
     *
     * <p>매칭 성사처럼 양쪽 모두에게 알려야 하는 경우에 사용한다.</p>
     *
     * @param userIds       수신자 목록
     * @param event         알림 사건
     * @param title         제목
     * @param content       내용
     * @param referenceType 참조 유형
     * @param referenceId   참조 ID
     */
    @Transactional
    public void notifyAll(List<Long> userIds, NotificationEvent event, String title, String content,
                          String referenceType, Long referenceId) {
        List<Notification> notifications = userIds.stream()
                .map(userId -> Notification.of(userId, event, title, content, referenceType, referenceId))
                .toList();
        notificationRepository.saveAll(notifications);
    }
}
