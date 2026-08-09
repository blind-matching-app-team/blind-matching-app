package com.bma.payment.dto;

import com.bma.payment.entity.Payment;
import com.bma.payment.entity.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 API의 요청/응답 DTO 모음.
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * 결제 승인 요청.
     *
     * <p>금액은 받지 않는다. 서버가 상품 가격으로 결정하므로 클라이언트가 금액을
     * 조작할 여지를 아예 없앤다.</p>
     *
     * @param productId  상품 ID
     * @param orderId    주문 ID(멱등성 키)
     * @param paymentKey PG 결제 키
     */
    public record PaymentRequest(
            @NotNull(message = "상품 ID는 필수입니다.") Long productId,

            @NotBlank(message = "주문 ID는 필수입니다.")
            @Size(max = 100, message = "주문 ID는 100자를 넘을 수 없습니다.")
            String orderId,

            @NotBlank(message = "결제 키는 필수입니다.")
            @Size(max = 200, message = "결제 키는 200자를 넘을 수 없습니다.")
            String paymentKey
    ) {
    }

    /**
     * 상품 응답.
     *
     * @param productId   상품 ID
     * @param productCode 상품 코드
     * @param productName 상품명
     * @param productType 상품 유형
     * @param price       가격
     * @param currency    통화
     */
    public record ProductResponse(Long productId,
                                  String productCode,
                                  String productName,
                                  String productType,
                                  BigDecimal price,
                                  String currency) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param product 상품 엔티티
         * @return 응답 DTO
         */
        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getProductCode(),
                    product.getProductName(),
                    product.getProductType(),
                    product.getPrice(),
                    product.getCurrencyCode());
        }
    }

    /**
     * 결제 결과 응답.
     *
     * @param paymentId    내부 결제 ID
     * @param orderId      주문 ID
     * @param productId    상품 ID
     * @param status       결제 상태
     * @param amount       결제 금액
     * @param currency     통화
     * @param approvedDate 승인 일시
     */
    public record PaymentResponse(Long paymentId,
                                  String orderId,
                                  Long productId,
                                  String status,
                                  BigDecimal amount,
                                  String currency,
                                  LocalDateTime approvedDate) {

        /**
         * 엔티티를 응답 DTO로 변환한다.
         *
         * @param payment 결제 엔티티
         * @return 응답 DTO
         */
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getProductId(),
                    payment.getPaymentStatus(),
                    payment.getAmount(),
                    payment.getCurrencyCode(),
                    payment.getApprovedDate());
        }
    }
}
