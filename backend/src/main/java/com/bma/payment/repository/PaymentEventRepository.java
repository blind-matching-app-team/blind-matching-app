package com.bma.payment.repository;

import com.bma.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link PaymentEvent} 저장소.
 */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    /**
     * 같은 멱등성 키의 이벤트가 이미 있는지 확인한다.
     *
     * <p>PG는 웹훅을 재전송할 수 있으므로, 같은 이벤트를 두 번 반영하지 않도록 확인한다.</p>
     *
     * @param idempotencyKey 멱등성 키
     * @return 이미 처리했으면 {@code true}
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}
