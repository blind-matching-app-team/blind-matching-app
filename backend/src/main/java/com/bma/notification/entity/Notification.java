package com.bma.notification.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 인앱 알림({@code NT_NOTIFICATION}).
 */
@Entity
@Table(name = "NT_NOTIFICATION")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseAuditEntity {

    /** 알림 유형: 매칭 성사. */
    public static final String TYPE_MATCH = "MATCH";

    /** 알림 유형: 새 메시지. */
    public static final String TYPE_MESSAGE = "MESSAGE";

    /** 알림 유형: 공개 단계 변경. */
    public static final String TYPE_REVEAL = "REVEAL";

    /** 알림 유형: 신고 처리. */
    public static final String TYPE_REPORT = "REPORT";

    /** 알림 유형: 시스템 공지. */
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** 알림 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIFICATION_ID")
    private Long id;

    /** 수신자 ID. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 알림 유형. */
    @Column(name = "NOTIFICATION_TYPE", nullable = false, length = 30)
    private String notificationType;

    /** 알림 제목. */
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    /** 알림 내용. */
    @Column(name = "CONTENT", length = 1000)
    private String content;

    /** 참조 엔티티 유형(MATCH/CHAT_ROOM 등). 클라이언트가 이동할 화면을 결정한다. */
    @Column(name = "REFERENCE_TYPE", length = 30)
    private String referenceType;

    /** 참조 엔티티 ID. */
    @Column(name = "REFERENCE_ID")
    private Long referenceId;

    /** 읽음 여부. */
    @Column(name = "READ_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String readYn = YesNo.N;

    /** 읽은 일시. */
    @Column(name = "READ_DATE")
    private LocalDateTime readDate;

    /**
     * 새 알림을 만든다.
     *
     * @param userId        수신자
     * @param type          알림 유형
     * @param title         제목
     * @param content       내용
     * @param referenceType 참조 유형
     * @param referenceId   참조 ID
     * @return 저장 대상 엔티티
     */
    public static Notification of(Long userId, String type, String title, String content,
                                  String referenceType, Long referenceId) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.notificationType = type;
        notification.title = title;
        notification.content = content;
        notification.referenceType = referenceType;
        notification.referenceId = referenceId;
        return notification;
    }

    /**
     * 읽음 처리한다. 이미 읽은 알림은 시각을 덮어쓰지 않는다.
     */
    public void markRead() {
        if (YesNo.isY(readYn)) {
            return;
        }
        this.readYn = YesNo.Y;
        this.readDate = LocalDateTime.now();
    }

    /**
     * 읽음 여부를 확인한다.
     *
     * @return 읽었으면 {@code true}
     */
    public boolean isRead() {
        return YesNo.isY(readYn);
    }
}
