package com.bma.verification.service;

/**
 * 얼굴 1:1 대조 추상화 (BMA-82).
 *
 * <p>자체 모델 vs 외부 API(AWS Rekognition 등) 결정과 무관하게 서비스 로직을 고정한다. 구현체는 두 이미지의
 * 바이트만 받아 유사도(0~100)를 돌려주고, 어떤 이미지도 저장하지 않아야 한다.</p>
 */
public interface FaceComparisonGateway {

    /** 설정 키워드(stub/rekognition ...). */
    String provider();

    /**
     * 셀피와 프로필 사진을 대조한다.
     *
     * @param selfie               촬영 이미지(메모리에만 존재)
     * @param selfieContentType    셀피 MIME
     * @param reference            프로필 사진
     * @param referenceContentType 프로필 사진 MIME
     * @return 대조 결과
     * @throws FaceComparisonException 제공자 호출 실패
     */
    FaceMatch compare(byte[] selfie, String selfieContentType, byte[] reference, String referenceContentType);

    /**
     * 대조 결과.
     *
     * @param faceDetected 양쪽 모두에서 얼굴을 찾았는지
     * @param similarity   유사도 0~100. 얼굴을 못 찾았으면 0
     */
    record FaceMatch(boolean faceDetected, double similarity) {
    }

    /** 제공자 호출 실패(네트워크·인증·한도). */
    class FaceComparisonException extends RuntimeException {
        public FaceComparisonException(String message, Throwable cause) {
            super(message, cause);
        }

        public FaceComparisonException(String message) {
            super(message);
        }
    }
}
