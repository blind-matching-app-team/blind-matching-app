package com.bma.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 빌링키 암복호화 왕복과 키 불일치 시 실패를 고정한다.
 */
class BillingKeyCipherTest {

    @Test
    @DisplayName("암호화한 빌링키는 같은 키로 복호화되고, 같은 평문이라도 nonce 가 달라 암호문이 다르다")
    void roundTrip() {
        BillingKeyCipher cipher = new BillingKeyCipher("unit-test-secret");
        String plain = "test_bk_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        byte[] first = cipher.encrypt(plain);
        byte[] second = cipher.encrypt(plain);

        assertThat(cipher.decrypt(first)).isEqualTo(plain);
        assertThat(cipher.decrypt(second)).isEqualTo(plain);
        assertThat(first).isNotEqualTo(second);
        assertThat(new String(first)).doesNotContain("test_bk_");
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void wrongKeyFails() {
        byte[] encrypted = new BillingKeyCipher("secret-a").encrypt("test_bk_x");
        assertThatThrownBy(() -> new BillingKeyCipher("secret-b").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
