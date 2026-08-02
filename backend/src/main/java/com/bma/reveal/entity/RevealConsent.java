package com.bma.reveal.entity;

import com.bma.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 매칭 참여자별 공개 단계 동의({@code RV_REVEAL_CONSENT}).
 *
 * <p>기존 구현의 {@code POST /reveal/consent}는 요청 내용을 그대로 되돌려주기만 하고
 * 아무것도 저장하지 않았다. 이 엔티티로 실제 동의 상태를 영속화한다.</p>
 */
@Entity
@Table(name = "RV_REVEAL_CONSENT")
@IdClass(RevealConsent.RevealConsentId.class)
@Getter
@Setter
@NoArgsConstructor
public class RevealConsent extends BaseAuditEntity {

    /** 동의 상태: 응답 대기. */
    public static final String STATUS_PENDING = "PENDING";

    /** 동의 상태: 수락. */
    public static final String STATUS_ACCEPTED = "ACCEPTED";

    /** 동의 상태: 거절. */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 매칭 ID(PK 일부). */
    @Id
    @Column(name = "MATCH_ID")
    private Long matchId;

    /** 동의 주체 사용자 ID(PK 일부). */
    @Id
    @Column(name = "USER_ID")
    private Long userId;

    /** 동의 대상 단계(PK 일부). 스키마가 {@code TINYINT UNSIGNED}라 타입을 명시한다. */
    @Id
    @Column(name = "REVEAL_LEVEL", columnDefinition = "TINYINT UNSIGNED")
    private Integer revealLevel;

    /** 동의 상태. */
    @Column(name = "CONSENT_STATUS", nullable = false, length = 20)
    private String consentStatus = STATUS_PENDING;

    /** 동의 요청 일시. */
    @Column(name = "REQUEST_DATE", nullable = false)
    private LocalDateTime requestDate = LocalDateTime.now();

    /** 응답 일시. */
    @Column(name = "RESPONSE_DATE")
    private LocalDateTime responseDate;

    /**
     * 새 동의 레코드를 만든다.
     *
     * @param matchId     매칭 ID
     * @param userId      사용자 ID
     * @param revealLevel 대상 단계
     * @return 저장 대상 엔티티
     */
    public static RevealConsent of(Long matchId, Long userId, Integer revealLevel) {
        RevealConsent consent = new RevealConsent();
        consent.matchId = matchId;
        consent.userId = userId;
        consent.revealLevel = revealLevel;
        consent.requestDate = LocalDateTime.now();
        return consent;
    }

    /**
     * 동의/거절 응답을 기록한다.
     *
     * @param accepted 수락 여부
     */
    public void respond(boolean accepted) {
        this.consentStatus = accepted ? STATUS_ACCEPTED : STATUS_REJECTED;
        this.responseDate = LocalDateTime.now();
        restore();
    }

    /**
     * 수락 상태인지 확인한다.
     *
     * @return 수락했으면 {@code true}
     */
    public boolean isAccepted() {
        return STATUS_ACCEPTED.equals(consentStatus) && !isDeleted();
    }

    /**
     * 복합 기본키 클래스.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RevealConsentId implements Serializable {

        /** 매칭 ID. */
        private Long matchId;

        /** 사용자 ID. */
        private Long userId;

        /** 공개 단계. */
        private Integer revealLevel;
    }
}
