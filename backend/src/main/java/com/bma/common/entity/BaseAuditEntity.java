package com.bma.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * BMA 스키마의 모든 업무 테이블이 공통으로 갖는 논리삭제 + 감사(audit) 컬럼을 담당하는 상위 클래스.
 *
 * <p>기존 구현은 {@code INSERT_USER}가 항상 "SYSTEM"으로 고정되고 {@code UPDATE_USER}는 영원히
 * null이라 감사 컬럼이 사실상 무의미했다. {@link CreatedBy}/{@link LastModifiedBy}를 붙이고
 * {@code AuditorAware} 구현({@link com.bma.common.config.JpaAuditConfig})을 등록해
 * 실제 요청 사용자 ID가 기록되도록 고쳤다.</p>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    /**
     * 논리 삭제 여부. 조회 시 항상 {@code 'N'} 조건을 함께 걸어야 한다.
     *
     * <p>{@code columnDefinition}을 명시한 이유: 스키마가 {@code CHAR(1)}인데 Hibernate는
     * String 필드를 기본적으로 {@code VARCHAR}로 기대한다. {@code ddl-auto=validate}는
     * 컬럼 타입까지 비교하므로, 명시하지 않으면 타입 불일치로 기동이 실패한다.
     * DDL 생성을 쓰지 않는 환경이라 이 값은 검증에만 영향을 준다.</p>
     */
    @Column(name = "DELETED", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String deleted = YesNo.N;

    /** 최초 등록자. 인증 컨텍스트가 없으면 "SYSTEM"이 기록된다. */
    @CreatedBy
    @Column(name = "INSERT_USER", nullable = false, length = 50, updatable = false)
    private String insertUser;

    /** 최초 등록 일시. 등록 이후에는 변경되지 않는다. */
    @CreatedDate
    @Column(name = "INSERT_DATE", nullable = false, updatable = false)
    private LocalDateTime insertDate;

    /** 최종 수정자. */
    @LastModifiedBy
    @Column(name = "UPDATE_USER", length = 50)
    private String updateUser;

    /** 최종 수정 일시. */
    @LastModifiedDate
    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    /** 시스템 처리 및 운영 메모. 장애 분석용 흔적을 남길 때 사용한다. */
    @Column(name = "SYSTEM_REMARK", length = 1000)
    private String systemRemark;

    /** 이 행을 논리 삭제 상태로 만든다. */
    public void markDeleted() {
        this.deleted = YesNo.Y;
    }

    /** 논리 삭제를 해제한다. 복합키 테이블에서 같은 행을 재사용할 때 쓴다. */
    public void restore() {
        this.deleted = YesNo.N;
    }

    /**
     * 논리 삭제 여부를 반환한다.
     *
     * @return 삭제된 행이면 {@code true}
     */
    public boolean isDeleted() {
        return YesNo.isY(this.deleted);
    }
}
