package com.bma.payment.service;

import lombok.Getter;

/**
 * 게이트웨이 호출 실패. PG 가 준 오류 코드를 그대로 담아 결제 원장의 실패 사유로 남긴다.
 */
@Getter
public class GatewayException extends RuntimeException {

    /** PG 오류 코드(예: INVALID_CARD_NUMBER). 통신 자체가 실패하면 {@code GATEWAY_ERROR}. */
    private final String code;

    public GatewayException(String code, String message) {
        super(message);
        this.code = code;
    }

    public GatewayException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
