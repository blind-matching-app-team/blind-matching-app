package com.bma.common.security;

/**
 * JWT의 {@code typ} 클레임에 담기는 토큰 종류.
 *
 * <p>액세스 토큰과 리프레시 토큰이 같은 키로 서명되기 때문에, 이 클레임이 없으면
 * 리프레시 토큰을 그대로 Authorization 헤더에 넣어 API를 호출할 수 있다.
 * 문자열 리터럴 대신 enum으로 고정해 오타로 인한 검증 누락을 막는다.</p>
 */
public enum TokenType {

    /** 리소스 접근용 단기 토큰. */
    ACCESS,

    /** 액세스 토큰 재발급 전용 장기 토큰. */
    REFRESH;

    /** DB 및 클레임에 저장되는 문자열 값. */
    public String value() {
        return name();
    }
}
