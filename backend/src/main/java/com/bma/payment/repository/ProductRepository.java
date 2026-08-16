package com.bma.payment.repository;

import com.bma.payment.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link Product} 저장소.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 판매 중인 상품 목록을 조회한다.
     *
     * @param useYn   판매 여부
     * @param deleted 논리 삭제 여부
     * @return 상품 목록
     */
    List<Product> findByUseYnAndDeletedOrderByIdAsc(String useYn, String deleted);

    /**
     * 판매 중인 상품 1건을 조회한다.
     *
     * <p>기존 구현은 {@code findById().orElseThrow()}만 사용해, 판매 중지된 상품도
     * 결제할 수 있었고 없는 상품이면 500으로 터졌다.</p>
     *
     * @param id      상품 ID
     * @param useYn   판매 여부
     * @param deleted 논리 삭제 여부
     * @return 상품
     */
    Optional<Product> findByIdAndUseYnAndDeleted(Long id, String useYn, String deleted);
}
