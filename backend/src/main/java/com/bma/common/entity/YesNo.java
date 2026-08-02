package com.bma.common.entity;

/**
 * BMA 스키마 전반에서 쓰이는 {@code CHAR(1)} Y/N 플래그 상수.
 *
 * <p>기존 코드는 {@code "Y"} / {@code "N"} 문자열 리터럴을 각 서비스에 흩뿌려 두어
 * 오타 하나가 조회 조건 누락으로 이어졌다. 상수로 모아 두면 컴파일 시점에 잡힌다.</p>
 */
public final class YesNo {

    /** 참(사용/삭제됨/읽음 등)을 의미하는 값. */
    public static final String Y = "Y";

    /** 거짓(미사용/미삭제/미읽음 등)을 의미하는 값. */
    public static final String N = "N";

    private YesNo() {
    }

    /**
     * boolean 값을 Y/N 문자열로 변환한다.
     *
     * @param value 변환할 값. {@code null}은 거짓으로 취급한다.
     * @return {@code true}면 {@link #Y}, 아니면 {@link #N}
     */
    public static String of(Boolean value) {
        return Boolean.TRUE.equals(value) ? Y : N;
    }

    /**
     * Y/N 문자열을 boolean으로 변환한다.
     *
     * @param value 변환할 값
     * @return {@code "Y"}인 경우에만 {@code true}
     */
    public static boolean isY(String value) {
        return Y.equals(value);
    }
}
