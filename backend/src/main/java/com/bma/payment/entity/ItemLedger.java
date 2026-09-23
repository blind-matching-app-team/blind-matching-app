package com.bma.payment.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 이용권 지급/사용 이력({@code PY_ITEM_LEDGER}).
 *
 * <p>월 지급은 {@code GRANT_KEY}({subscriptionId}:{yyyyMM}:{itemType}) 에 유니크 제약이 있어
 * 배치가 두 번 돌아도 같은 달에 두 번 지급되지 않는다.</p>
 */
@Entity
@Table(name = "PY_ITEM_LEDGER")
@Getter
@Setter
@NoArgsConstructor
public class ItemLedger extends BaseAuditEntity {

    public static final String REASON_PURCHASE = "PURCHASE";
    public static final String REASON_GRANT = "GRANT";
    public static final String REASON_USE = "USE";
    public static final String REASON_EXPIRE = "EXPIRE";
    public static final String REASON_REFUND = "REFUND";

    public static final String REF_PAYMENT = "PAYMENT";
    public static final String REF_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String REF_MATCH_QUEUE = "MATCH_QUEUE";
    public static final String REF_MATCH = "MATCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LEDGER_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "ITEM_TYPE", nullable = false, length = 30)
    private String itemType;

    @Column(name = "REASON_CODE", nullable = false, length = 30)
    private String reasonCode;

    @Column(name = "DELTA_QTY", nullable = false)
    private int deltaQty;

    @Column(name = "PURCHASED_AFTER", nullable = false)
    private int purchasedAfter;

    @Column(name = "GRANTED_AFTER", nullable = false)
    private int grantedAfter;

    @Column(name = "REF_TYPE", length = 30)
    private String refType;

    @Column(name = "REF_ID")
    private Long refId;

    @Column(name = "GRANT_KEY", length = 100)
    private String grantKey;

    /**
     * 이력 행을 만든다.
     *
     * @param item     처리 후의 지갑
     * @param reason   사유
     * @param delta    증감 수량
     * @param refType  참조 유형
     * @param refId    참조 ID
     * @param grantKey 월 지급 멱등 키(지급이 아니면 {@code null})
     * @return 엔티티
     */
    public static ItemLedger of(UserItem item, String reason, int delta, String refType, Long refId,
                                String grantKey) {
        ItemLedger ledger = new ItemLedger();
        ledger.userId = item.getUserId();
        ledger.itemType = item.getItemType();
        ledger.reasonCode = reason;
        ledger.deltaQty = delta;
        ledger.purchasedAfter = item.getPurchasedQty();
        ledger.grantedAfter = item.getGrantedQty();
        ledger.refType = refType;
        ledger.refId = refId;
        ledger.grantKey = grantKey;
        return ledger;
    }
}
