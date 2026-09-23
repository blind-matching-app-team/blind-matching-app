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

import java.time.LocalDateTime;

/**
 * 사용자 단위 자동결제 빌링키({@code PY_BILLING_KEY}).
 *
 * <p>빌링키는 결제 1건이 아니라 사용자 1명에 귀속되어 매 청구 주기마다 재사용한다.
 * 토스 자동결제 승인에는 빌링키와 짝이 되는 {@code customerKey} 가 반드시 함께 필요하므로
 * 둘을 한 행에 보관한다. 빌링키 원문은 AES-GCM 으로 암호화해 저장한다
 * ({@link com.bma.payment.service.BillingKeyCipher}).</p>
 *
 * <p>사용자당 1행이다({@code UK_PY_BILLING_KEY_USER}). 카드를 바꾸면 같은 행을 새 값으로 덮어쓴다.</p>
 */
@Entity
@Table(name = "PY_BILLING_KEY")
@Getter
@Setter
@NoArgsConstructor
public class BillingKey extends BaseAuditEntity {

    /** 사용 가능. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 폐기됨(구독 해지 후 카드 삭제, 탈퇴 등). */
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BILLING_KEY_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 토스 customerKey. 빌링키와 쌍으로만 결제가 가능하다. */
    @Column(name = "CUSTOMER_KEY", nullable = false, length = 100)
    private String customerKey;

    /** 빌링키 암호문(AES-GCM, nonce 포함). */
    @Column(name = "BILLING_KEY_ENC", nullable = false, columnDefinition = "VARBINARY(1000)")
    private byte[] billingKeyEnc;

    @Column(name = "CARD_COMPANY", length = 50)
    private String cardCompany;

    @Column(name = "CARD_NUMBER_MASKED", length = 30)
    private String cardNumberMasked;

    @Column(name = "KEY_STATUS", nullable = false, length = 20)
    private String keyStatus = STATUS_ACTIVE;

    @Column(name = "ISSUED_DATE", nullable = false)
    private LocalDateTime issuedDate = LocalDateTime.now();

    @Column(name = "REVOKED_DATE")
    private LocalDateTime revokedDate;

    /**
     * 새 빌링키 행을 만든다.
     *
     * @param userId           사용자
     * @param customerKey      토스 customerKey
     * @param billingKeyEnc    암호화된 빌링키
     * @param cardCompany      카드사
     * @param cardNumberMasked 마스킹 카드번호
     * @return 엔티티
     */
    public static BillingKey issue(Long userId, String customerKey, byte[] billingKeyEnc,
                                   String cardCompany, String cardNumberMasked) {
        BillingKey key = new BillingKey();
        key.userId = userId;
        key.replace(customerKey, billingKeyEnc, cardCompany, cardNumberMasked);
        return key;
    }

    /**
     * 카드를 바꿔 등록할 때 같은 행을 새 값으로 덮어쓴다.
     *
     * @param customerKey      토스 customerKey
     * @param billingKeyEnc    암호화된 빌링키
     * @param cardCompany      카드사
     * @param cardNumberMasked 마스킹 카드번호
     */
    public void replace(String customerKey, byte[] billingKeyEnc, String cardCompany, String cardNumberMasked) {
        this.customerKey = customerKey;
        this.billingKeyEnc = billingKeyEnc;
        this.cardCompany = cardCompany;
        this.cardNumberMasked = cardNumberMasked;
        this.keyStatus = STATUS_ACTIVE;
        this.issuedDate = LocalDateTime.now();
        this.revokedDate = null;
    }

    /** 폐기한다. 이후 자동결제에 쓸 수 없다. */
    public void revoke() {
        this.keyStatus = STATUS_REVOKED;
        this.revokedDate = LocalDateTime.now();
    }

    /**
     * 자동결제에 쓸 수 있는지 확인한다.
     *
     * @return 사용 가능하면 {@code true}
     */
    public boolean isUsable() {
        return STATUS_ACTIVE.equals(keyStatus) && !isDeleted();
    }
}
