package com.bma.user.repository;

import com.bma.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link User} 저장소.
 *
 * <p>모든 조회 메서드는 논리 삭제 조건({@code deleted})을 함께 받는다.
 * 이 조건을 빠뜨리면 탈퇴한 계정으로 로그인이 되는 등의 문제가 생긴다.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자를 찾는다.
     *
     * @param email   로그인 이메일
     * @param deleted 논리 삭제 여부
     * @return 사용자
     */
    Optional<User> findByEmailAndDeleted(String email, String deleted);

    /**
     * ID로 살아 있는 사용자를 찾는다.
     *
     * @param id      사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 사용자
     */
    Optional<User> findByIdAndDeleted(Long id, String deleted);

    /**
     * 이메일 중복 여부를 확인한다.
     *
     * @param email   로그인 이메일
     * @param deleted 논리 삭제 여부
     * @return 존재하면 {@code true}
     */
    boolean existsByEmailAndDeleted(String email, String deleted);

    /**
     * 소셜 제공자와 제공자 키로 연결된 계정을 조회한다.
     *
     * <p>{@code UK_US_USER_PROVIDER(LOGIN_PROVIDER, PROVIDER_USER_KEY)} 유니크 제약과 짝을 이룬다.</p>
     *
     * @param loginProvider   로그인 제공자(KAKAO/NAVER/GOOGLE)
     * @param providerUserKey 제공자 내 사용자 고유 키
     * @param deleted         논리 삭제 여부
     * @return 연결된 사용자
     */
    Optional<User> findByLoginProviderAndProviderUserKeyAndDeleted(String loginProvider,
                                                                   String providerUserKey,
                                                                   String deleted);

    /**
     * 휴대전화 번호 중복 여부를 확인한다.
     *
     * <p>{@code US_USER}에 유니크 제약이 있어 사전 확인 없이 저장하면
     * 500(DataIntegrityViolation)으로 터진다.</p>
     *
     * @param phoneNumber 휴대전화 번호
     * @param deleted     논리 삭제 여부
     * @return 존재하면 {@code true}
     */
    boolean existsByPhoneNumberAndDeleted(String phoneNumber, String deleted);

    /**
     * 살아 있는 사용자인지 확인한다.
     *
     * @param id      사용자 ID
     * @param deleted 논리 삭제 여부
     * @return 존재하면 {@code true}
     */
    boolean existsByIdAndDeleted(Long id, String deleted);
}
