package com.bma.notification.dto;

import com.bma.notification.entity.Notification;

import java.time.LocalDateTime;

/**
 * 알림 API의 응답 DTO 모음.
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /**
     * 알림 응답.
     *
     * @param notificationId 알림 ID
     * @param type           알림 유형
     * @param title          제목
     * @param content        내용
     * @param referenceType  참조 유형
     * @param referenceId    참조 ID
     * @param read           읽음 여부
     * @param readDate       읽은 일시
     * @param createdDate    생성 일시
     */
    public record NotificationResponse(Long notificationId,
                                       String type,
                                       String title,
                                       String content,
                                       String referenceType,
                                       Long referenceId,
                                       boolean read,
                                       LocalDateTime readDate,
                                       LocalDateTime createdDate) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param notification 알림 엔티티
         * @return 응답 DTO
         */
        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getNotificationType(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.getReferenceType(),
                    notification.getReferenceId(),
                    notification.isRead(),
                    notification.getReadDate(),
                    notification.getInsertDate());
        }
    }

    /**
     * 안 읽은 알림 수 응답.
     *
     * @param unreadCount 안 읽은 알림 수
     */
    public record UnreadCountResponse(long unreadCount) {
    }
}
