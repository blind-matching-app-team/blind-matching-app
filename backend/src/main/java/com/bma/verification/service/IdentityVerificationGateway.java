package com.bma.verification.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 인증사 연동 추상화 (BMA-79).
 *
 * <p>업체(PASS/NICE/토스 등)가 확정되지 않아 연동 방식을 이 인터페이스 뒤로 숨겼다. 프론트 계약(요청 → SDK → 확인)은
 * 업체와 무관하게 고정되고, 업체가 정해지면 구현체 하나를 추가하고 {@code app.verification.identity.provider} 만 바꾸면 된다.</p>
 */
public interface IdentityVerificationGateway {

    /** 설정 키워드(stub/pass/nice/toss ...). */
    String provider();

    /**
     * 인증 요청을 시작한다. 프론트가 SDK 를 띄우는 데 필요한 값(업체별 토큰·URL 등)을 돌려준다.
     *
     * @param transactionId 우리 쪽 거래 ID
     * @param userId        요청 사용자
     * @return SDK 파라미터(업체별로 다름)
     */
    Map<String, Object> begin(String transactionId, Long userId);

    /**
     * 인증 결과를 확인한다. 업체별 결과 토큰/암호문을 검증해 신원 정보를 돌려준다.
     *
     * @param transactionId 거래 ID
     * @param providerPayload 프론트가 SDK 에서 받아 그대로 넘긴 결과
     * @return 검증된 신원
     * @throws IdentityVerificationException 검증 실패
     */
    IdentityResult confirm(String transactionId, Map<String, Object> providerPayload);

    /**
     * 인증사가 검증한 신원 정보.
     *
     * @param name        실명
     * @param birthDate   생년월일(성인 판정의 유일한 근거)
     * @param genderCode  M/F
     * @param phoneNumber 휴대전화 번호
     * @param ci          연계정보(같은 사람이면 어느 서비스에서든 같은 값)
     */
    record IdentityResult(String name, LocalDate birthDate, String genderCode, String phoneNumber, String ci) {
    }

    /** 인증사 검증 실패. */
    class IdentityVerificationException extends RuntimeException {
        public IdentityVerificationException(String message) {
            super(message);
        }
    }
}
