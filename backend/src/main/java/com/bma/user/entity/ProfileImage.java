package com.bma.user.entity;

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

/**
 * 프로필 이미지 및 블라인드 표시 정보({@code US_PROFILE_IMAGE}).
 *
 * <p>공개 URL 대신 저장소 오브젝트 키만 보관한다. 클라이언트에는 공개 단계에 맞는 키만
 * 골라서 내려주며, 실제 파일 접근은 단기 서명 URL 발급을 통해 이뤄져야 한다
 * (로컬 저장소 구현에서는 키를 그대로 노출하지 않는 것으로 대신한다).</p>
 */
@Entity
@Table(name = "US_PROFILE_IMAGE")
@Getter
@Setter
@NoArgsConstructor
public class ProfileImage extends BaseAuditEntity {

    /** 검수 상태: 대기. */
    public static final String REVIEW_PENDING = "PENDING";

    /** 검수 상태: 승인. */
    public static final String REVIEW_APPROVED = "APPROVED";

    /** 이미지 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMAGE_ID")
    private Long id;

    /** 소유자 ID. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 저장소 종류(LOCAL/S3). */
    @Column(name = "STORAGE_TYPE", nullable = false)
    private String storageType = "LOCAL";

    /** 원본 이미지 오브젝트 키. 전체 공개(레벨 2)에서만 사용한다. */
    @Column(name = "ORIGINAL_OBJECT_KEY", nullable = false, length = 500)
    private String originalObjectKey;

    /** 블러 처리 이미지 키. 부분 공개(레벨 1)에서 사용한다. */
    @Column(name = "BLURRED_OBJECT_KEY", length = 500)
    private String blurredObjectKey;

    /** 실루엣 이미지 키. 미공개(레벨 0)에서 사용한다. */
    @Column(name = "SILHOUETTE_OBJECT_KEY", length = 500)
    private String silhouetteObjectKey;

    /** 업로드 당시 원본 파일명. 표시용이며 경로 조립에 사용하지 않는다. */
    @Column(name = "ORIGINAL_FILE_NAME")
    private String originalFileName;

    /** 검증된 MIME 타입. */
    @Column(name = "CONTENT_TYPE")
    private String contentType;

    /** 파일 크기(바이트). */
    @Column(name = "FILE_SIZE")
    private Long fileSize;

    /** 파일 내용의 SHA-256 해시. 중복 업로드 판별에 사용한다. */
    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    /** 표시 순서. */
    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 1;

    /** 대표 이미지 여부. 사용자당 최대 1건만 {@code 'Y'}가 되도록 서비스가 보장한다. */
    @Column(name = "PRIMARY_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String primaryYn = YesNo.N;

    /** 검수 상태. */
    @Column(name = "REVIEW_STATUS", nullable = false)
    private String reviewStatus = REVIEW_PENDING;

    /**
     * 대표 이미지 여부를 설정한다.
     *
     * @param primary 대표로 지정할지 여부
     */
    public void markPrimary(boolean primary) {
        this.primaryYn = YesNo.of(primary);
    }

    /**
     * 대표 이미지인지 확인한다.
     *
     * @return 대표이면 {@code true}
     */
    public boolean isPrimary() {
        return YesNo.isY(primaryYn);
    }
}
