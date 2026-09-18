package com.bma.notification.dto;

import com.bma.notification.entity.Notification;
import com.bma.notification.entity.NotificationEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 알림 API의 응답 DTO 모음 (S7).
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /**
     * 알림 응답 (S7-09 리스트 아이템).
     *
     * @param notificationId 알림 ID
     * @param type           큰 분류(MATCH/MESSAGE/REVEAL/REPORT/SYSTEM). 아이콘 색 그룹용
     * @param eventCode      사건 코드({@link NotificationEvent}). 아이콘·문구·이동 화면의 기준
     * @param title          제목(사양서의 알림 문구)
     * @param content        내용(부가 설명, 메시지 미리보기 등). 없으면 {@code null}
     * @param target         클릭 시 이동 정보. 이동 대상이 없는 사건은 {@code screen=null}
     * @param referenceType  참조 유형(MATCH/CHAT_ROOM/USER). {@code target} 의 원천
     * @param referenceId    참조 ID
     * @param read           읽음 여부(S7-09 안읽음 점)
     * @param readDate       읽은 일시
     * @param createdDate    생성 일시(S7-09 시간 표시)
     */
    // 값이 없는 필드도 null 로 남긴다. 프론트가 아이템에 그대로 바인딩한다.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NotificationResponse(Long notificationId,
                                       String type,
                                       String eventCode,
                                       String title,
                                       String content,
                                       NotificationTarget target,
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
            NotificationEvent event = notification.event();
            return new NotificationResponse(
                    notification.getId(),
                    notification.getNotificationType(),
                    event.name(),
                    notification.getTitle(),
                    notification.getContent(),
                    NotificationTarget.of(event, notification.getReferenceType(), notification.getReferenceId()),
                    notification.getReferenceType(),
                    notification.getReferenceId(),
                    notification.isRead(),
                    notification.getReadDate(),
                    notification.getInsertDate());
        }
    }

    /**
     * 알림 클릭 시 이동 정보 (S7-09 "클릭 시 관련 화면으로 이동").
     *
     * <p>사건별 대상 화면과 필요한 ID 를 함께 준다. 프론트는 {@code screen} 으로 라우트를 고르고
     * {@code matchId}/{@code chatRoomId} 를 경로에 넣는다.</p>
     *
     * @param screen     화면 번호(S5/S10/S11). 이동 없음이면 {@code null}
     * @param matchId    S10 으로 갈 때의 매칭 ID
     * @param chatRoomId S11 으로 갈 때의 채팅방 ID
     * @param userId     호감 보낸 상대 등 참조 사용자 ID
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NotificationTarget(String screen, Long matchId, Long chatRoomId, Long userId) {

        /**
         * 사건과 참조로부터 이동 정보를 만든다.
         *
         * @param event         사건
         * @param referenceType 참조 유형
         * @param referenceId   참조 ID
         * @return 이동 정보
         */
        public static NotificationTarget of(NotificationEvent event, String referenceType, Long referenceId) {
            Long matchId = "MATCH".equals(referenceType) ? referenceId : null;
            Long chatRoomId = "CHAT_ROOM".equals(referenceType) ? referenceId : null;
            Long userId = "USER".equals(referenceType) ? referenceId : null;
            return new NotificationTarget(event.targetScreen(), matchId, chatRoomId, userId);
        }
    }

    /**
     * 안 읽은 알림 수 응답 (사이드바 알림 배지).
     *
     * @param unreadCount 안 읽은 알림 수
     */
    public record UnreadCountResponse(long unreadCount) {
    }

    /**
     * 개별 읽음 처리 결과.
     *
     * @param notificationId 알림 ID
     * @param readAt         읽은 일시. 이미 읽은 알림이면 처음 읽은 시각 그대로
     * @param unreadCount    처리 후 남은 안 읽은 수(배지 갱신용)
     */
    public record ReadResult(Long notificationId, LocalDateTime readAt, long unreadCount) {
    }

    /**
     * 전체 읽음 처리 결과 (S7-08).
     *
     * @param updatedCount 이번에 읽음으로 바뀐 건수
     * @param unreadCount  처리 후 남은 안 읽은 수. 항상 0
     */
    public record ReadAllResult(int updatedCount, long unreadCount) {
    }
}
