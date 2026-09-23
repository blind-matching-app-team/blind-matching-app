package com.bma.payment.entity;

/**
 * 소모형 이용권 종류 (BMA-17 확정, 빠른매칭권은 폐기).
 */
public enum ItemType {

    /** 매칭 기회 추가: 하루 무료 대기열 진입 횟수를 넘겨 진입할 때 1개 소모. */
    MATCH_CHANCE,

    /** 재매칭권: 현재 매칭을 끝내고 대기 없이 즉시 대기열에 다시 들어갈 때 1개 소모 (S5-12, S10-19). */
    REMATCH_TICKET;

    /**
     * 문자열 코드를 열거형으로 바꾼다.
     *
     * @param code 코드
     * @return 열거형. 모르는 코드면 {@code null}
     */
    public static ItemType of(String code) {
        for (ItemType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
