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

import java.math.BigDecimal;

/**
 * 사진인증 시도 이력({@code US_PHOTO_VERIFICATION}, BMA-82).
 *
 * <p>결과·유사도만 남긴다. 촬영한 셀피는 대조 직후 메모리에서 버리고 어떤 컬럼·저장소에도 쓰지 않는다.</p>
 */
@Entity
@Table(name = "US_PHOTO_VERIFICATION")
@Getter
@Setter
@NoArgsConstructor
public class PhotoVerification extends BaseAuditEntity {

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PHOTO_VERIFICATION_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "PROVIDER", nullable = false, length = 20)
    private String provider;

    @Column(name = "RESULT", nullable = false, length = 20)
    private String result;

    @Column(name = "SIMILARITY", precision = 5, scale = 2)
    private BigDecimal similarity;

    @Column(name = "THRESHOLD", nullable = false, precision = 5, scale = 2)
    private BigDecimal threshold;

    @Column(name = "PROFILE_IMAGE_ID")
    private Long profileImageId;

    @Column(name = "FAIL_REASON", length = 500)
    private String failReason;

    public static PhotoVerification of(Long userId, String provider, String result, Double similarity,
                                       double threshold, Long profileImageId, String failReason) {
        PhotoVerification v = new PhotoVerification();
        v.userId = userId;
        v.provider = provider;
        v.result = result;
        v.similarity = similarity == null ? null : BigDecimal.valueOf(similarity).setScale(2, java.math.RoundingMode.HALF_UP);
        v.threshold = BigDecimal.valueOf(threshold).setScale(2, java.math.RoundingMode.HALF_UP);
        v.profileImageId = profileImageId;
        v.failReason = failReason != null && failReason.length() > 500 ? failReason.substring(0, 500) : failReason;
        return v;
    }
}
