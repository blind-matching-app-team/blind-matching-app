package com.bma.notification.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 알림 사건 종류 (S7 사양서 v1.7).
 *
 * <p>{@code NOTIFICATION_TYPE}(MATCH/MESSAGE/REVEAL/REPORT/SYSTEM)은 큰 분류라 S7-09 "클릭 시
 * 유형별 화면 이동"과 S7-11~14 의 아이콘·문구 구분에 부족하다. 사건 단위 코드를 따로 두고
 * 분류와 이동 대상 화면은 여기서 파생한다. 코드는 프론트 계약이므로 이름을 바꾸지 말 것.</p>
 */
public enum NotificationEvent {

    /** 한쪽만 호감을 표시함 → 메인 허브. */
    MATCH_LIKED(Notification.TYPE_MATCH, "S5"),

    /** 상호 매칭 성사 → S10 매칭 상세. */
    MATCH_CREATED(Notification.TYPE_MATCH, "S10"),

    /** S7-11 매칭 성사 직후 상대가 사진인증 미완료 → S10. 발행은 사진인증(BMA-82) 이후. */
    MATCH_UNVERIFIED_PARTNER(Notification.TYPE_MATCH, "S10"),

    /** S7-14 매칭 그만두기(S5-16)로 종료됨 → 메인 허브. 사유·주체는 담지 않는다. */
    MATCH_ENDED(Notification.TYPE_MATCH, "S5"),

    /** S7-12 상대가 다음 공개 단계를 요청함 → S10, 동의 모달 자동 오픈. */
    REVEAL_REQUESTED(Notification.TYPE_REVEAL, "S10"),

    /** 공개 단계가 올라감 → S10. */
    REVEAL_LEVEL_UP(Notification.TYPE_REVEAL, "S10"),

    /** 새 채팅 메시지 → S11 해당 채팅방. */
    MESSAGE_RECEIVED(Notification.TYPE_MESSAGE, "S11"),

    /** S7-13 관리자 경고 조치 → 상세 사유 화면(추후)/고객센터. 발행은 신고검토(BMA-76) 이후. */
    WARNING_ISSUED(Notification.TYPE_REPORT, null),

    /** 시스템 공지. 이동 없음. */
    SYSTEM(Notification.TYPE_SYSTEM, null);

    private final String category;
    private final String targetScreen;

    NotificationEvent(String category, String targetScreen) {
        this.category = category;
        this.targetScreen = targetScreen;
    }

    /** {@code NOTIFICATION_TYPE} 에 들어가는 큰 분류. */
    public String category() {
        return category;
    }

    /** 클릭 시 이동할 화면 번호. 이동 대상이 없으면 {@code null}. */
    public String targetScreen() {
        return targetScreen;
    }

    /**
     * 저장된 코드로 사건을 찾는다.
     *
     * @param code {@code EVENT_CODE} 값
     * @return 사건. 모르는 코드나 {@code null} 이면 비어 있음
     */
    public static Optional<NotificationEvent> fromCode(String code) {
        return Arrays.stream(values()).filter(event -> event.name().equals(code)).findFirst();
    }
}
