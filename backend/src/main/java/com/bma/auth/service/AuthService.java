package com.bma.auth.service;

import com.bma.auth.dto.AuthDtos.DuplicateEmailDetail;
import com.bma.auth.dto.AuthDtos.LoginRequest;
import com.bma.auth.dto.AuthDtos.SignupRequest;
import com.bma.auth.dto.AuthDtos.SignupResponse;
import com.bma.auth.dto.AuthDtos.SuspendedAccountDetail;
import com.bma.auth.dto.AuthDtos.TokenResponse;
import com.bma.auth.entity.UserToken;
import com.bma.auth.repository.UserTokenRepository;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.security.JwtTokenProvider;
import com.bma.common.security.TokenType;
import com.bma.safety.entity.UserSanction;
import com.bma.safety.repository.UserSanctionRepository;
import com.bma.user.entity.User;
import com.bma.user.entity.UserProfile;
import com.bma.user.repository.UserProfileRepository;
import com.bma.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 회원가입 / 로그인 / 토큰 재발급 / 로그아웃 처리.
 *
 * <p>고친 점</p>
 * <ul>
 *   <li><b>토큰 재사용 탐지</b>: 이미 회수된 리프레시 토큰이 다시 들어오면 탈취로 간주하고
 *       해당 사용자의 모든 리프레시 토큰을 폐기한다(기존에는 단순히 실패만 반환).</li>
 *   <li>토큰 행의 소유자와 JWT의 subject가 일치하는지 교차 검증한다.</li>
 *   <li>정지/탈퇴 계정의 로그인·재발급을 차단한다(기존에는 상태를 전혀 보지 않았다).</li>
 *   <li>휴대전화 중복을 미리 확인해 유니크 제약 위반으로 인한 500을 막는다.</li>
 *   <li>재발급 시 해당 사용자의 만료 토큰을 정리해 테이블이 무한히 커지지 않게 한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final UserProfileRepository profileRepository;
    private final UserSanctionRepository sanctionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * 회원가입.
     *
     * @param request 가입 요청
     * @return 생성된 사용자 정보
     * @throws BusinessException 이메일 또는 휴대전화가 이미 사용 중인 경우
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        // 소셜로 가입된 이메일인지 프론트가 구분해 안내할 수 있도록 가입 수단을 함께 내려준다.
        // 계정 자동 통합이나 별도 계정 생성은 하지 않고 차단 + 안내로만 처리한다.
        Optional<User> existing = userRepository.findByEmailAndDeleted(email, YesNo.N);
        if (existing.isPresent()) {
            throw BusinessException.withDetails(
                    ErrorCode.EMAIL_DUPLICATED,
                    DuplicateEmailDetail.of(existing.get().getLoginProvider()));
        }
        // US_USER.PHONE_NUMBER 에 유니크 제약이 있으므로 저장 전에 확인한다.
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()
                && userRepository.existsByPhoneNumberAndDeleted(request.phoneNumber(), YesNo.N)) {
            throw new BusinessException(ErrorCode.PHONE_DUPLICATED);
        }

        User user = User.createLocal(email, passwordEncoder.encode(request.password()), request.phoneNumber());
        User saved = userRepository.save(user);

        log.info("회원가입 완료: userId={}", saved.getId());
        return new SignupResponse(saved.getId(), saved.getUserStatus());
    }

    /**
     * 로그인.
     *
     * <p>이메일이 없는 경우와 비밀번호가 틀린 경우 모두 같은 오류를 반환해
     * 가입 여부가 노출되지 않도록 한다.</p>
     *
     * @param request 로그인 요청
     * @return 발급된 토큰
     * @throws BusinessException 자격 증명 불일치 또는 이용할 수 없는 계정 상태
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeleted(normalizeEmail(request.email()), YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("로그인 실패(자격 증명 불일치): userId={}", user.getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 자격 증명을 먼저 검증한 뒤에 계정 상태를 본다. 순서를 반대로 하면
        // 비밀번호를 모르는 사람도 계정 정지 여부를 알아낼 수 있다.
        if (user.isSuspended()) {
            throw BusinessException.withDetails(ErrorCode.ACCOUNT_SUSPENDED, describeSuspension(user));
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        user.touchLastLogin();
        return issueTokens(user);
    }

    /**
     * 리프레시 토큰으로 액세스 토큰을 재발급한다(로테이션 방식).
     *
     * <p>사용된 리프레시 토큰은 즉시 회수되고 새 토큰이 발급된다. 회수된 토큰이 다시 제출되면
     * 탈취로 간주해 해당 사용자의 모든 리프레시 토큰을 폐기하고 재로그인을 요구한다.</p>
     *
     * @param refreshToken 리프레시 토큰 원문
     * @return 새로 발급된 토큰
     * @throws BusinessException 토큰이 유효하지 않거나 재사용이 탐지된 경우
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parseAndRequireType(refreshToken, TokenType.REFRESH);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = Long.valueOf(claims.getSubject());
        String tokenHash = sha256Hex(refreshToken);

        UserToken storedToken = tokenRepository
                .findByTokenHashAndTokenTypeAndDeleted(tokenHash, TokenType.REFRESH.value(), YesNo.N)
                .orElseGet(() -> {
                    // 서명은 유효한데 살아 있는 행이 없다 = 이미 로테이션된 토큰의 재사용.
                    // 정상 클라이언트라면 발생할 수 없으므로 탈취로 간주하고 전 세션을 끊는다.
                    revokeAllRefreshTokens(userId, "리프레시 토큰 재사용 탐지");
                    throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
                });

        // 해시 충돌이나 데이터 오염 대비. 토큰 행의 주인과 JWT subject가 반드시 같아야 한다.
        if (!storedToken.getUserId().equals(userId)) {
            revokeAllRefreshTokens(userId, "토큰 소유자 불일치");
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (!storedToken.isUsable()) {
            storedToken.revoke();
            throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        // 로테이션: 기존 토큰을 회수하고 새 쌍을 발급한다.
        storedToken.revoke();

        User user = userRepository.findByIdAndDeleted(userId, YesNo.N)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            revokeAllRefreshTokens(userId, "비활성 계정의 토큰 재발급 시도");
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        // 만료된 행이 쌓이지 않도록 이 사용자 분량만 정리한다.
        tokenRepository.deleteExpiredTokens(userId, LocalDateTime.now());

        return issueTokens(user);
    }

    /**
     * 로그아웃. 제출한 리프레시 토큰을 회수한다.
     *
     * <p>이 API는 인증이 필요하며, 본인 소유가 아닌 토큰은 회수하지 않는다.
     * (기존에는 인증 없이 호출 가능해 토큰만 알면 타인의 세션을 끊을 수 있었다.)</p>
     *
     * @param userId       요청자 ID
     * @param refreshToken 회수할 리프레시 토큰 원문
     */
    @Transactional
    public void logout(Long userId, String refreshToken) {
        tokenRepository
                .findByTokenHashAndTokenTypeAndDeleted(sha256Hex(refreshToken), TokenType.REFRESH.value(), YesNo.N)
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(UserToken::revoke);
    }

    /**
     * 액세스/리프레시 토큰 쌍을 발급하고 리프레시 토큰 해시를 저장한다.
     *
     * @param user 대상 사용자
     * @return 토큰 응답
     */
    TokenResponse issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getUserRole());
        String refreshToken = tokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getUserRole());

        tokenRepository.save(UserToken.issue(
                user.getId(),
                TokenType.REFRESH.value(),
                sha256Hex(refreshToken),
                LocalDateTime.now().plusSeconds(tokenProvider.refreshTokenSeconds())));

        boolean profileCompleted = profileRepository.findById(user.getId())
                .map(UserProfile::isComplete)
                .orElse(false);

        return new TokenResponse(
                accessToken,
                refreshToken,
                tokenProvider.accessTokenSeconds(),
                user.getId(),
                profileCompleted);
    }

    /**
     * 해당 사용자의 살아 있는 리프레시 토큰을 모두 폐기한다.
     *
     * @param userId 사용자 ID
     * @param reason 로그에 남길 사유
     */
    private void revokeAllRefreshTokens(Long userId, String reason) {
        List<UserToken> tokens = tokenRepository
                .findAllByUserIdAndTokenTypeAndDeleted(userId, TokenType.REFRESH.value(), YesNo.N);
        tokens.forEach(UserToken::revoke);
        log.warn("리프레시 토큰 전체 폐기: userId={}, 폐기 건수={}, 사유={}", userId, tokens.size(), reason);
    }

    /**
     * 정지 계정의 상세를 만든다.
     *
     * <p>제재 이력({@code SF_USER_SANCTION})이 정본이다. 로그인을 막는 제재(SUSPEND/BAN) 중
     * 지금 효력이 있는 것을 찾아 영구 제재를 우선하고, 같은 종류면 해제가 가장 늦은 것을 고른다.
     * 제재 행이 없으면 {@code US_USER.SUSPENDED_UNTIL} 로 대체한다.</p>
     *
     * <p>시각은 UTC ISO-8601 로 내려주고 KST 변환은 프론트가 한다.</p>
     *
     * @param user 정지 상태인 사용자
     * @return 정지 상세
     */
    SuspendedAccountDetail describeSuspension(User user) {
        Optional<UserSanction> sanction = sanctionRepository
                .findByUserIdAndActiveYnAndDeletedOrderByStartDateDesc(user.getId(), YesNo.Y, YesNo.N)
                .stream()
                .filter(UserSanction::blocksLogin)
                .filter(item -> item.isEffectiveAt(LocalDateTime.now()))
                // 영구 제재가 하나라도 있으면 그것이 이긴다. 그다음은 해제가 가장 늦은 것.
                .max(Comparator.<UserSanction, Boolean>comparing(UserSanction::isPermanent)
                        .thenComparing(item -> item.getEndDate() == null
                                ? LocalDateTime.MAX : item.getEndDate()));

        if (sanction.isPresent()) {
            UserSanction found = sanction.get();
            return found.isPermanent()
                    ? SuspendedAccountDetail.permanent(found.getReason())
                    : SuspendedAccountDetail.temporary(found.getReason(), toUtcIso(found.getEndDate()));
        }

        // 제재 이력이 없는데 상태만 SUSPENDED 인 경우. 운영 중 수동 변경 등으로 생길 수 있다.
        log.warn("정지 상태이지만 활성 제재 이력이 없습니다. userId={}", user.getId());
        LocalDateTime until = user.getSuspendedUntil();
        return until == null
                ? SuspendedAccountDetail.permanent(ErrorCode.ACCOUNT_SUSPENDED.getMessage())
                : SuspendedAccountDetail.temporary(ErrorCode.ACCOUNT_SUSPENDED.getMessage(), toUtcIso(until));
    }

    /**
     * 서버 로컬 시각을 UTC ISO-8601 문자열로 바꾼다.
     *
     * <p>DB 와 애플리케이션 시간대가 Asia/Seoul 로 맞춰져 있으므로 KST 로 해석한 뒤 UTC 로 옮긴다.</p>
     *
     * @param value 변환할 시각. {@code null} 이면 {@code null} 을 그대로 반환한다
     * @return UTC ISO-8601 문자열
     */
    private String toUtcIso(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * 이메일을 소문자로 정규화한다. 대소문자만 다른 중복 가입을 막는다.
     *
     * @param email 원본 이메일
     * @return 정규화된 이메일
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * 토큰 원문을 SHA-256 16진 문자열로 변환한다.
     *
     * @param value 원문
     * @return 소문자 16진 해시
     */
    String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 반드시 지원하므로 실제로는 발생하지 않는다.
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
