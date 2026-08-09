package com.bma.payment.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결제 승인·취소·웹훅 이벤트 원문({@code PY_PAYMENT_EVENT}).
 *
 * <p>이 테이블은 스키마에 있었지만 기존 코드에서 전혀 사용되지 않았다.
 * 결제 분쟁이 생기면 "언제 무엇을 주고받았는지"를 증명해야 하므로,
 * 요청/응답 원문을 남기는 것은 선택이 아니라 필수다.</p>
 *
 * <p>{@code UK_PY_PAYMENT_EVENT_IDEMPOTENCY(IDEMPOTENCY_KEY)} 제약을 이용해
 * 같은 웹훅이 중복 처리되는 것도 막는다.</p>
 */
@Entity
@Table(name = "PY_PAYMENT_EVENT")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEvent extends BaseAuditEntity {

    /** 이벤트: 결제 요청. */
    public static final String TYPE_REQUEST = "REQUEST";

    /** 이벤트: 승인. */
    public static final String TYPE_APPROVE = "APPROVE";

    /** 이벤트: 취소. */
    public static final String TYPE_CANCEL = "CANCEL";

    /** 이벤트: 웹훅 수신. */
    public static final String TYPE_WEBHOOK = "WEBHOOK";

    /** 처리 성공. */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 처리 실패. */
    public static final String STATUS_FAIL = "FAIL";

    /** 이벤트 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVENT_ID")
    private Long id;

    /** 연관된 내부 결제 ID. 웹훅이 먼저 도착한 경우 {@code null}일 수 있다. */
    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    /** 이벤트 유형. */
    @Column(name = "EVENT_TYPE", nullable = false, length = 50)
    private String eventType;

    /** 처리 결과. */
    @Column(name = "EVENT_STATUS", nullable = false, length = 20)
    private String eventStatus;

    /** 멱등성 키. 같은 키의 이벤트는 한 번만 저장된다. */
    @Column(name = "IDEMPOTENCY_KEY", length = 100)
    private String idempotencyKey;

    // 스키마 컬럼이 MySQL JSON 타입이라 String 기본 매핑(VARCHAR)과 어긋난다.
    // ddl-auto=validate 를 통과하도록 타입을 명시한다.

    /** 요청 본문 원문(JSON). */
    @Column(name = "REQUEST_JSON", columnDefinition = "json")
    private String requestJson;

    /** 응답/웹훅 본문 원문(JSON). */
    @Column(name = "RESPONSE_JSON", columnDefinition = "json")
    private String responseJson;

    /** 오류 메시지. */
    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    /**
     * 이벤트 로그를 만든다.
     *
     * @param paymentId      결제 ID(선택)
     * @param eventType      이벤트 유형
     * @param eventStatus    처리 결과
     * @param idempotencyKey 멱등성 키(선택)
     * @param requestJson    요청 원문
     * @param responseJson   응답 원문
     * @param errorMessage   오류 메시지
     * @return 저장 대상 엔티티
     */
    public static PaymentEvent of(Long paymentId, String eventType, String eventStatus,
                                  String idempotencyKey, String requestJson,
                                  String responseJson, String errorMessage) {
        PaymentEvent event = new PaymentEvent();
        event.paymentId = paymentId;
        event.eventType = eventType;
        event.eventStatus = eventStatus;
        event.idempotencyKey = idempotencyKey;
        event.requestJson = requestJson;
        event.responseJson = responseJson;
        event.errorMessage = errorMessage == null || errorMessage.length() <= 2000
                ? errorMessage
                : errorMessage.substring(0, 2000);
        return event;
    }
}
