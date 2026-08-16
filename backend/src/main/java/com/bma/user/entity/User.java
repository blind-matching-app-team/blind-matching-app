package com.bma.user.entity;

import com.bma.common.entity.BaseAuditEntity;
import com.bma.common.entity.YesNo;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * 로그인 계정 및 서비스 상태({@code US_USER}).
 *
 * <p>주의: 이 엔티티는 절대 API 응답으로 직접 반환하지 않는다.
 * 기존 구현은 {@code GET /api/v1/users/me}에서 엔티티를 그대로 직렬화해
 * <b>비밀번호 해시가 응답 본문에 노출</b>되고 있었다. 지금은
 * {@code UserDtos.MeResponse}로 변환해서 내려준다. 실수로 직렬화되는 경우에 대비해
 * 민감 필드에는 {@link JsonIgnore}를 이중으로 걸어 두었다.</p>
 */
@Entity
@Table(name = "US_USER")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseAuditEntity {

    /** 계정 상태: 정상 이용 가능. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 계정 상태: 가입 대기(이메일 인증 등 미완료). */
    public static final String STATUS_PENDING = "PENDING";

    /** 계정 상태: 제재로 인한 정지. */
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 계정 상태: 탈퇴. */
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    /** 기본 권한 코드. */
    public static final String ROLE_USER = "USER";

    /** 사용자 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long id;

    /** 로그인 이메일. 유니크 제약이 걸려 있다. */
    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    /** BCrypt 비밀번호 해시. 어떤 경우에도 외부로 나가서는 안 된다. */
    @JsonIgnore
    @Column(name = "PASSWORD_HASH")
    private String passwordHash;

    /** 휴대전화 번호. 유니크 제약이 걸려 있다. */
    @JsonIgnore
    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    /** 로그인 제공자(LOCAL/KAKAO/GOOGLE/NAVER/APPLE). */
    @Column(name = "LOGIN_PROVIDER", nullable = false)
    private String loginProvider = "LOCAL";

    /** 소셜 제공자의 사용자 키. */
    @JsonIgnore
    @Column(name = "PROVIDER_USER_KEY")
    private String providerUserKey;

    /** 권한 코드(USER/ADMIN). */
    @Column(name = "USER_ROLE", nullable = false)
    private String userRole = ROLE_USER;

    /** 계정 상태. */
    @Column(name = "USER_STATUS", nullable = false)
    private String userStatus = STATUS_ACTIVE;

    // CHAR(1) 컬럼은 columnDefinition 을 명시해야 ddl-auto=validate 를 통과한다
    // (BaseAuditEntity.deleted 주석 참고).

    /** 이메일 인증 여부. */
    @Column(name = "EMAIL_VERIFIED_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String emailVerifiedYn = YesNo.N;

    /** 휴대전화 인증 여부. */
    @Column(name = "PHONE_VERIFIED_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String phoneVerifiedYn = YesNo.N;

    /** 마지막 로그인 일시. */
    @Column(name = "LAST_LOGIN_DATE")
    private LocalDateTime lastLoginDate;

    /**
     * 로컬 가입 계정을 만든다.
     *
     * @param email        로그인 이메일
     * @param passwordHash BCrypt 해시
     * @param phoneNumber  휴대전화 번호(선택)
     * @return 저장 대상 엔티티
     */
    public static User createLocal(String email, String passwordHash, String phoneNumber) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        // 빈 문자열이 들어오면 유니크 제약에 걸리므로 null로 정규화한다.
        user.phoneNumber = (phoneNumber == null || phoneNumber.isBlank()) ? null : phoneNumber;
        user.userStatus = STATUS_ACTIVE;
        user.userRole = ROLE_USER;
        return user;
    }

    /**
     * 서비스를 이용할 수 있는 상태인지 확인한다.
     *
     * @return 정상 상태이고 논리 삭제되지 않았으면 {@code true}
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(userStatus) && !isDeleted();
    }

    /** 마지막 로그인 시각을 현재로 갱신한다. */
    public void touchLastLogin() {
        this.lastLoginDate = LocalDateTime.now();
    }
}
