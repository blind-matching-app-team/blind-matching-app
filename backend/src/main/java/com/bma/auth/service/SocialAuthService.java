package com.bma.auth.service;

import com.bma.auth.dto.AuthDtos.DuplicateEmailDetail;
import com.bma.auth.dto.AuthDtos.TokenResponse;
import com.bma.auth.entity.UserToken;
import com.bma.auth.repository.UserTokenRepository;
import com.bma.auth.social.SocialOAuthClient;
import com.bma.auth.social.SocialProvider;
import com.bma.auth.social.SocialUserProfile;
import com.bma.common.config.AppProperties;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.user.entity.User;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * 소셜 로그인(백엔드 콜백 방식) 처리.
 *
 * <p>흐름</p>
 * <ol>
 *   <li>프론트가 {@code /authorize} 로 이동 → state 를 만들어 쿠키에 심고 3사로 302</li>
 *   <li>3사가 {@code /callback} 호출 → state 대조 → 토큰 교환 → 가입/로그인
 *       → <b>일회용 티켓</b>을 붙여 프론트로 302</li>
 *   <li>프론트가 {@code /exchange} 로 티켓을 제출 → 실제 토큰 발급</li>
 * </ol>
 *
 * <p>2단계에서 JWT 를 그대로 URL 에 실으면 브라우저 히스토리와 리퍼러에 남는다.
 * 그래서 짧은 수명의 일회용 티켓만 넘기고 실제 토큰은 3단계 POST 로 건넨다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAuthService {

    /** 일회용 티켓의 {@code US_USER_TOKEN.TOKEN_TYPE} 값. */
    private static final String TICKET_TOKEN_TYPE = "SOCIAL_TICKET";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties properties;
    private final SocialOAuthClient oauthClient;
    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final AuthService authService;

    /**
     * CSRF 방지용 state 값을 만든다.
     *
     * @return URL 에 안전한 난수 문자열
     */
    public String createState() {
        return randomToken();
    }

    /**
     * 사용자를 보낼 3사 인가 URL 을 만든다.
     *
     * @param provider 제공자
     * @param state    {@link #createState()} 로 만든 값
     * @return 인가 URL
     */
    public String buildAuthorizeUrl(SocialProvider provider, String state) {
        return oauthClient.buildAuthorizeUrl(provider, state);
    }

    /**
     * 콜백을 처리하고 프론트에 넘길 일회용 티켓을 만든다.
     *
     * @param provider    제공자
     * @param code        인가 코드
     * @param state       콜백으로 돌아온 state
     * @param cookieState 쿠키에 심어 두었던 state
     * @return 일회용 티켓 원문
     * @throws BusinessException state 불일치, 토큰 교환 실패, 이메일 미제공, 계정 사용 불가
     */
    @Transactional
    public String handleCallback(SocialProvider provider, String code, String state, String cookieState) {
        if (cookieState == null || state == null || !cookieState.equals(state)) {
            log.warn("소셜 state 불일치: provider={}", provider);
            throw new BusinessException(ErrorCode.SOCIAL_STATE_MISMATCH);
        }

        SocialUserProfile profile = oauthClient.fetchProfile(provider, code, state);
        if (profile.providerKey() == null) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }

        User user = resolveUser(profile);
        assertUsable(user);
        user.touchLastLogin();

        return issueTicket(user);
    }

    /**
     * 일회용 티켓을 실제 토큰으로 교환한다.
     *
     * @param ticket 티켓 원문
     * @return 토큰 응답. 이메일 로그인과 동일한 형태다
     * @throws BusinessException 티켓이 없거나 이미 사용된 경우
     */
    @Transactional
    public TokenResponse exchangeTicket(String ticket) {
        UserToken stored = tokenRepository
                .findByTokenHashAndTokenTypeAndDeleted(authService.sha256Hex(ticket), TICKET_TOKEN_TYPE, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_TICKET_INVALID));

        if (!stored.isUsable()) {
            throw new BusinessException(ErrorCode.SOCIAL_TICKET_INVALID);
        }
        // 티켓은 1회용이다. 교환 즉시 소진시킨다.
        stored.revoke();

        User user = userRepository.findByIdAndDeleted(stored.getUserId(), YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        assertUsable(user);

        return authService.issueTokens(user);
    }

    /**
     * 프로필로 기존 계정을 찾거나 새로 가입시킨다.
     *
     * <p>같은 제공자 + 같은 제공자 키면 기존 계정이다. 그렇지 않고 이메일이 이미
     * 쓰이고 있으면 <b>차단하고 가입 수단을 안내</b>한다. 계정 자동 통합이나 별도 계정
     * 생성은 하지 않는다(정합성 검토 v1.6).</p>
     *
     * @param profile 제공자에게서 받은 사용자 정보
     * @return 로그인시킬 사용자
     */
    private User resolveUser(SocialUserProfile profile) {
        String provider = profile.provider().name();

        Optional<User> linked = userRepository
                .findByLoginProviderAndProviderUserKeyAndDeleted(provider, profile.providerKey(), YesNo.N);
        if (linked.isPresent()) {
            return linked.get();
        }

        // US_USER.EMAIL 이 NOT NULL 이라 이메일 없이는 가입할 수 없다.
        // 카카오는 이메일 필수 동의에 비즈 앱 전환과 검수가 필요하다.
        if (!profile.hasEmail()) {
            log.warn("소셜 이메일 미제공으로 가입 불가: provider={}", provider);
            throw new BusinessException(ErrorCode.SOCIAL_EMAIL_REQUIRED);
        }

        String email = profile.email().trim().toLowerCase();
        Optional<User> byEmail = userRepository.findByEmailAndDeleted(email, YesNo.N);
        if (byEmail.isPresent()) {
            throw BusinessException.withDetails(
                    ErrorCode.EMAIL_DUPLICATED,
                    DuplicateEmailDetail.of(byEmail.get().getLoginProvider()));
        }

        User created = userRepository.save(User.createSocial(email, provider, profile.providerKey()));
        log.info("소셜 회원가입 완료: userId={}, provider={}", created.getId(), provider);
        return created;
    }

    /**
     * 로그인시킬 수 있는 계정인지 확인한다.
     *
     * <p>정지 계정은 이메일 로그인과 동일하게 상세를 담아 거부한다.</p>
     *
     * @param user 대상 사용자
     */
    private void assertUsable(User user) {
        if (user.isSuspended()) {
            throw BusinessException.withDetails(
                    ErrorCode.ACCOUNT_SUSPENDED, authService.describeSuspension(user));
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    /**
     * 일회용 티켓을 발급하고 해시를 저장한다.
     *
     * @param user 대상 사용자
     * @return 티켓 원문
     */
    private String issueTicket(User user) {
        String ticket = randomToken();
        tokenRepository.save(UserToken.issue(
                user.getId(),
                TICKET_TOKEN_TYPE,
                authService.sha256Hex(ticket),
                LocalDateTime.now().plusSeconds(properties.oauth().ticketSeconds())));
        return ticket;
    }

    /**
     * URL 에 안전한 난수 문자열을 만든다.
     *
     * @return 32바이트를 Base64 URL 인코딩한 값
     */
    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
