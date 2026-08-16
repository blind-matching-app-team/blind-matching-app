package com.bma.auth.entity;

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
 * 리프레시 토큰 등 서버가 상태를 관리해야 하는 토큰의 저장 엔티티({@code US_USER_TOKEN}).
 *
 * <p>토큰 원문은 저장하지 않고 SHA-256 해시만 보관한다. DB가 유출되어도 해시만으로는
 * 유효한 토큰을 만들 수 없다.</p>
 */
@Entity
@Table(name = "US_USER_TOKEN")
@Getter
@Setter
@NoArgsConstructor
public class UserToken extends BaseAuditEntity {

    /** 토큰 ID(PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TOKEN_ID")
    private Long id;

    /** 토큰 소유자. */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 토큰 종류. {@link com.bma.common.security.TokenType} 값이 들어간다. */
    @Column(name = "TOKEN_TYPE", nullable = false, length = 30)
    private String tokenType;

    /** 토큰 원문의 SHA-256 16진 해시. 유니크 제약이 걸려 있다. */
    @Column(name = "TOKEN_HASH", nullable = false, unique = true)
    private String tokenHash;

    /** 만료 일시. */
    @Column(name = "EXPIRE_DATE", nullable = false)
    private LocalDateTime expireDate;

    /** 사용(회수) 일시. 값이 있으면 이미 소진된 토큰이다. */
    @Column(name = "USED_DATE")
    private LocalDateTime usedDate;

    /**
     * 새 토큰 레코드를 만든다.
     *
     * @param userId     소유자 ID
     * @param tokenType  토큰 종류
     * @param tokenHash  토큰 해시
     * @param expireDate 만료 일시
     * @return 저장 대상 엔티티
     */
    public static UserToken issue(Long userId, String tokenType, String tokenHash, LocalDateTime expireDate) {
        UserToken token = new UserToken();
        token.userId = userId;
        token.tokenType = tokenType;
        token.tokenHash = tokenHash;
        token.expireDate = expireDate;
        return token;
    }

    /**
     * 토큰을 회수 처리한다(로테이션/로그아웃/탈취 대응 공통).
     *
     * <p>논리 삭제까지 함께 수행하므로 이후 조회 조건({@code DELETED='N'})에서 자동으로 제외된다.</p>
     */
    public void revoke() {
        this.usedDate = LocalDateTime.now();
        markDeleted();
    }

    /**
     * 지금 사용할 수 있는 토큰인지 확인한다.
     *
     * @return 미사용 + 미만료 + 미삭제 상태면 {@code true}
     */
    public boolean isUsable() {
        return usedDate == null
                && !isDeleted()
                && expireDate != null
                && expireDate.isAfter(LocalDateTime.now());
    }
}
