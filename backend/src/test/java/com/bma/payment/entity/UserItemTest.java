package com.bma.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이용권 지갑의 이월 상한과 사용 순서를 고정한다 (BMA-19 안건4: 최대 2개월치 누적, 초과분 소멸).
 */
class UserItemTest {

    @Test
    @DisplayName("월 지급은 지급분 상한(월 지급량 × 이월 개월)까지만 쌓이고 초과분은 소멸한다")
    void grantWithCap() {
        UserItem item = UserItem.emptyFor(1L, ItemType.MATCH_CHANCE);

        assertThat(item.addGrantedWithCap(3, 6)).isZero();   // 1개월차: 3
        assertThat(item.addGrantedWithCap(3, 6)).isZero();   // 2개월차: 6 (이월)
        assertThat(item.addGrantedWithCap(3, 6)).isEqualTo(3); // 3개월차: 6 유지, 3 소멸
        assertThat(item.getGrantedQty()).isEqualTo(6);
    }

    @Test
    @DisplayName("상한 미만이면 일부만 소멸한다")
    void grantPartiallyExpires() {
        UserItem item = UserItem.emptyFor(1L, ItemType.REMATCH_TICKET);
        item.addGrantedWithCap(2, 4);
        item.consumeOne();                                   // granted 1
        assertThat(item.addGrantedWithCap(2, 4)).isZero();   // 3
        assertThat(item.addGrantedWithCap(2, 4)).isEqualTo(1); // 4, 1 소멸
        assertThat(item.getGrantedQty()).isEqualTo(4);
    }

    @Test
    @DisplayName("구매분은 상한과 무관하게 쌓이고, 사용은 지급분(소멸 대상)부터 한다")
    void consumeGrantedFirst() {
        UserItem item = UserItem.emptyFor(1L, ItemType.MATCH_CHANCE);
        item.addPurchased(2);
        item.addGrantedWithCap(3, 6);
        item.addPurchased(5);
        assertThat(item.getPurchasedQty()).isEqualTo(7);
        assertThat(item.total()).isEqualTo(10);

        for (int i = 0; i < 3; i++) {
            assertThat(item.consumeOne()).isTrue();
        }
        assertThat(item.getGrantedQty()).isZero();
        assertThat(item.getPurchasedQty()).isEqualTo(7);

        assertThat(item.consumeOne()).isTrue();
        assertThat(item.getPurchasedQty()).isEqualTo(6);
    }

    @Test
    @DisplayName("잔여가 없으면 사용에 실패하고 음수가 되지 않는다")
    void consumeWhenEmpty() {
        UserItem item = UserItem.emptyFor(1L, ItemType.MATCH_CHANCE);
        assertThat(item.consumeOne()).isFalse();
        assertThat(item.total()).isZero();
    }

    @Test
    @DisplayName("이미 상한을 넘게 보유한 상태(정책 변경 등)에서는 지급분이 줄지 않고 전량 소멸 처리된다")
    void capBelowCurrentDoesNotShrink() {
        UserItem item = UserItem.emptyFor(1L, ItemType.MATCH_CHANCE);
        item.addGrantedWithCap(10, 10);
        assertThat(item.addGrantedWithCap(3, 6)).isEqualTo(3);
        assertThat(item.getGrantedQty()).isEqualTo(10);
    }
}
