package com.bma.verification.entity;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 본인인증 요청·결과 이력({@code US_IDENTITY_VERIFICATION}, BMA-79).
 *
 * <p>요청 1건이 거래 ID 하나를 가진다. 프론트는 이 거래 ID 로 인증사 SDK 를 띄우고, 결과를 {@code confirm} 으로 돌려준다.
 * 실명·전화번호는 마스킹된 값만 남긴다.</p>
 */
@Entity
@Table(name = "US_IDENTITY_VERIFICATION")
@Getter
@Setter
@NoArgsConstructor
public class IdentityVerification extends BaseAuditEntity {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    /** 인증사가 돌려준 생년월일 기준 만 19세 미만(BMA-19 안건3). 계정은 롤백된다. */
    public static final String STATUS_REJECTED_MINOR = "REJECTED_MINOR";
    /** 같은 CI 가 이미 다른 활성 계정에 묶여 있음. */
    public static final String STATUS_REJECTED_DUPLICATE = "REJECTED_DUPLICATE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VERIFICATION_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TRANSACTION_ID", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "PROVIDER", nullable = false, length = 20)
    private String provider;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = STATUS_REQUESTED;

    @Column(name = "EXPIRE_DATE", nullable = false)
    private LocalDateTime expireDate;

    @Column(name = "CONFIRM_DATE")
    private LocalDateTime confirmDate;

    @Column(name = "RESULT_NAME_MASKED", length = 50)
    private String resultNameMasked;

    @Column(name = "RESULT_BIRTH_DATE")
    private LocalDate resultBirthDate;

    @Column(name = "RESULT_GENDER_CODE", columnDefinition = "CHAR(1)")
    private String resultGenderCode;

    @Column(name = "RESULT_PHONE_MASKED", length = 30)
    private String resultPhoneMasked;

    @Column(name = "FAIL_REASON", length = 500)
    private String failReason;

    public static IdentityVerification request(Long userId, String transactionId, String provider, int expireMinutes) {
        IdentityVerification v = new IdentityVerification();
        v.userId = userId;
        v.transactionId = transactionId;
        v.provider = provider;
        v.status = STATUS_REQUESTED;
        v.expireDate = LocalDateTime.now().plusMinutes(expireMinutes);
        return v;
    }

    public boolean isRequested() {
        return STATUS_REQUESTED.equals(status);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expireDate.isAfter(now);
    }

    /** 인증 결과(마스킹)를 기록하고 상태를 정한다. */
    public void complete(String status, String nameMasked, LocalDate birthDate, String genderCode,
                         String phoneMasked, String failReason) {
        this.status = status;
        this.confirmDate = LocalDateTime.now();
        this.resultNameMasked = nameMasked;
        this.resultBirthDate = birthDate;
        this.resultGenderCode = genderCode;
        this.resultPhoneMasked = phoneMasked;
        this.failReason = failReason;
    }

    public void expire() {
        this.status = STATUS_EXPIRED;
    }
}
