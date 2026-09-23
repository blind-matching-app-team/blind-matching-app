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
 * 소모형 이용권 잔여 개수({@code PY_USER_ITEM}). 사용자 × 이용권 종류당 1행.
 *
 * <p>구매분({@code purchasedQty})과 구독 지급분({@code grantedQty})을 나눠 두는 이유는
 * 이월 정책이 지급분에만 적용되기 때문이다(BMA-19 안건4: "다음 달로 이월되나 최대 2개월치까지만
 * 누적, 초과분 소멸"). 구매한 이용권은 소멸하지 않는다. 사용할 때는 소멸 대상인 지급분을 먼저 쓴다.</p>
 */
@Entity
@Table(name = "PY_USER_ITEM")
@Getter
@Setter
@NoArgsConstructor
public class UserItem extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ITEM_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "ITEM_TYPE", nullable = false, length = 30)
    private String itemType;

    @Column(name = "PURCHASED_QTY", nullable = false)
    private int purchasedQty;

    @Column(name = "GRANTED_QTY", nullable = false)
    private int grantedQty;

    /**
     * 빈 지갑 행을 만든다.
     *
     * @param userId   사용자
     * @param itemType 이용권 종류
     * @return 엔티티
     */
    public static UserItem emptyFor(Long userId, ItemType itemType) {
        UserItem item = new UserItem();
        item.userId = userId;
        item.itemType = itemType.name();
        return item;
    }

    /**
     * 총 잔여 개수.
     *
     * @return 구매분 + 지급분
     */
    public int total() {
        return purchasedQty + grantedQty;
    }

    /**
     * 구매로 얻은 개수를 더한다.
     *
     * @param qty 수량(양수)
     */
    public void addPurchased(int qty) {
        this.purchasedQty += qty;
    }

    /**
     * 구독 월 지급분을 더하되 상한을 넘는 만큼은 소멸시킨다.
     *
     * @param qty 이번 달 지급 수량
     * @param cap 지급분 누적 상한(= 월 지급량 × 이월 개월 수)
     * @return 소멸한 수량(상한 초과분)
     */
    public int addGrantedWithCap(int qty, int cap) {
        int before = this.grantedQty;
        int after = Math.min(before + qty, Math.max(cap, before));
        this.grantedQty = after;
        return before + qty - after;
    }

    /**
     * 1개를 사용한다. 지급분(소멸 대상)을 먼저 쓴다.
     *
     * @return 사용했으면 {@code true}, 잔여가 없으면 {@code false}
     */
    public boolean consumeOne() {
        if (grantedQty > 0) {
            grantedQty--;
            return true;
        }
        if (purchasedQty > 0) {
            purchasedQty--;
            return true;
        }
        return false;
    }
}
