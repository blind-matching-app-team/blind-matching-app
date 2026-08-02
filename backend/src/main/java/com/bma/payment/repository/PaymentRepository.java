package com.bma.payment.repository;

import com.bma.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link Payment} 저장소.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 주문 ID로 결제를 조회한다. 멱등 처리의 기준이 된다.
     *
     * @param orderId 주문 ID
     * @return 결제
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * PG 결제 키로 조회한다. 웹훅 처리에 사용한다.
     *
     * @param paymentKey PG 결제 키
     * @return 결제
     */
    Optional<Payment> findByPaymentKey(String paymentKey);

    /**
     * 사용자의 결제 내역을 최신순으로 조회한다.
     *
     * @param userId  사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 결제 목록
     */
    List<Payment> findByUserIdAndDeletedOrderByIdDesc(Long userId, String deleted);
}
