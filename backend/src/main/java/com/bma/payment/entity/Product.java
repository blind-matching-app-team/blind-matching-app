package com.bma.payment.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 부가 기능 상품({@code PY_PRODUCT}).
 */
@Entity
@Table(name = "PY_PRODUCT")
@Getter
@Setter
@NoArgsConstructor
public class Product extends BaseAuditEntity {

    /** 상품 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID")
    private Long id;

    /** 상품 코드. 유니크 제약이 걸려 있다. */
    @Column(name = "PRODUCT_CODE", nullable = false, length = 50)
    private String productCode;

    /** 상품명. */
    @Column(name = "PRODUCT_NAME", nullable = false, length = 200)
    private String productName;

    /** 상품 유형(SUBSCRIPTION/ITEM). */
    @Column(name = "PRODUCT_TYPE", nullable = false, length = 30)
    private String productType;

    /**
     * 판매 가격.
     *
     * <p>결제 승인 금액은 반드시 이 값을 기준으로 검증한다.
     * 클라이언트가 보낸 금액을 신뢰하면 금액 위변조가 가능해진다.</p>
     */
    @Column(name = "PRICE", nullable = false)
    private BigDecimal price;

    /** 통화 코드. */
    @Column(name = "CURRENCY_CODE", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode = "KRW";

    /** 혜택 구성(JSON 문자열). */
    @Column(name = "BENEFIT_JSON", columnDefinition = "json")
    private String benefitJson;

    /** 판매 여부. */
    @Column(name = "USE_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String useYn = YesNo.Y;

    /**
     * 현재 판매 중인 상품인지 확인한다.
     *
     * @return 판매 중이면 {@code true}
     */
    public boolean isOnSale() {
        return YesNo.isY(useYn) && !isDeleted();
    }
}
