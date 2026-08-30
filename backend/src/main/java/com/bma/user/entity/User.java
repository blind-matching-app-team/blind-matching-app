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

    /** 이메일/비밀번호로 가입한 계정의 제공자 값. */
    public static final String PROVIDER_LOCAL = "LOCAL";

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
    private String loginProvider = PROVIDER_LOCAL;

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
     * 정지 종료 예정 일시. 영구 정지이거나 정지 상태가 아니면 {@code null}.
     *
     * <p>제재 이력({@code SF_USER_SANCTION})이 정본이고 이 컬럼은 로그인 경로에서
     * 빠르게 읽기 위한 값이다. 제재 행이 없을 때의 대비책으로도 쓴다.</p>
     */
    @Column(name = "SUSPENDED_UNTIL")
    private LocalDateTime suspendedUntil;

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
     * 소셜 계정으로 가입한 사용자를 만든다.
     *
     * <p>비밀번호가 없으므로 {@code PASSWORD_HASH} 는 null 이다. 이 계정으로는
     * 이메일 로그인을 할 수 없다({@code AuthService.login} 이 해시 null 을 거부한다).</p>
     *
     * @param email       제공자에게서 받은 이메일. 소문자로 정규화된 값을 넣는다
     * @param provider    로그인 제공자(KAKAO/NAVER/GOOGLE)
     * @param providerKey 제공자 내 사용자 고유 키
     * @return 저장 대상 엔티티
     */
    public static User createSocial(String email, String provider, String providerKey) {
        User user = new User();
        user.email = email;
        user.loginProvider = provider;
        user.providerUserKey = providerKey;
        // 제공자가 인증을 마친 이메일이므로 별도 인증 절차를 요구하지 않는다.
        user.emailVerifiedYn = YesNo.Y;
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

    /**
     * 이용 정지 상태인지 확인한다.
     *
     * <p>탈퇴({@code WITHDRAWN})나 가입 대기({@code PENDING})와 구분한다.
     * 정지일 때만 프론트가 이용정지 화면을 띄우고, 나머지는 일반 오류로 처리한다.</p>
     *
     * @return 정지 상태이면 {@code true}
     */
    public boolean isSuspended() {
        return STATUS_SUSPENDED.equals(userStatus);
    }

    /**
     * 소셜 계정으로 가입했는지 확인한다.
     *
     * @return {@code LOCAL} 이 아니면 {@code true}
     */
    public boolean isSocialAccount() {
        return loginProvider != null && !PROVIDER_LOCAL.equals(loginProvider);
    }

    /** 마지막 로그인 시각을 현재로 갱신한다. */
    public void touchLastLogin() {
        this.lastLoginDate = LocalDateTime.now();
    }
}
