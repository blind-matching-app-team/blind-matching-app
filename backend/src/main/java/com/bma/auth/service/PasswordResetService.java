package com.bma.auth.service;

import com.bma.auth.dto.AuthDtos.PasswordResetConfirmRequest;
import com.bma.auth.dto.AuthDtos.PasswordResetRequested;
import com.bma.auth.dto.AuthDtos.PasswordResetResult;
import com.bma.auth.dto.AuthDtos.PasswordResetTokenStatus;
import com.bma.auth.entity.UserToken;
import com.bma.auth.mail.PasswordResetMailer;
import com.bma.auth.repository.UserTokenRepository;
import com.bma.common.config.AppProperties;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.security.TokenType;
import com.bma.user.entity.User;
import com.bma.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 비밀번호 재설정 (S13, BMA-61).
 *
 * <ol>
 *   <li>요청: 이메일로 사용자를 찾아 일회용 토큰(30분)을 발급하고 링크 메일을 보낸다.
 *       미가입·탈퇴·소셜 전용 계정이어도 같은 응답을 준다(이메일 존재 여부 비노출, S13-04).</li>
 *   <li>확인: 토큰을 검증해 비밀번호를 바꾸고 토큰을 소진시킨 뒤, 모든 기기의 리프레시 토큰을 폐기한다.</li>
 * </ol>
 *
 * <p>토큰 원문은 저장하지 않고 SHA-256 해시만 {@code US_USER_TOKEN}(종류 {@code PASSWORD_RESET})에 둔다.
 * 새 토큰을 발급하면 같은 사용자의 이전 미사용 토큰은 폐기해 항상 최신 링크 하나만 유효하다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    /** {@code US_USER_TOKEN.TOKEN_TYPE} 값. */
    public static final String TOKEN_TYPE = "PASSWORD_RESET";

    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();

    /**
     * 재설정 링크를 요청한다.
     *
     * @param email 가입 이메일
     * @return 항상 sent=true. 개발 설정이 켜져 있고 실제 발송 대상이면 링크를 함께 준다
     */
    @Transactional
    public PasswordResetRequested requestReset(String email) {
        int minutes = properties.passwordReset().tokenMinutes();
        String normalized = email == null ? "" : email.trim().toLowerCase();

        User user = userRepository.findByEmailAndDeleted(normalized, YesNo.N).orElse(null);
        // 비밀번호가 없는 계정(소셜 전용)이나 이용 불가 계정은 조용히 넘긴다. 응답은 같다.
        if (user == null || !user.isActive() || user.getPasswordHash() == null) {
            log.info("비밀번호 재설정 요청 - 발송 대상 아님(응답은 동일): email={}", mask(normalized));
            return new PasswordResetRequested(true, minutes, null);
        }

        // 이전 링크는 무효화해 항상 마지막 링크만 살아 있게 한다.
        tokenRepository.findAllByUserIdAndTokenTypeAndDeleted(user.getId(), TOKEN_TYPE, YesNo.N)
                .forEach(UserToken::revoke);

        String rawToken = newToken();
        tokenRepository.save(UserToken.issue(user.getId(), TOKEN_TYPE, sha256Hex(rawToken),
                LocalDateTime.now().plusMinutes(minutes)));

        String link = properties.passwordReset().linkBaseUrl()
                + (properties.passwordReset().linkBaseUrl().contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        mailer.sendResetLink(user.getEmail(), link, minutes);
        log.info("비밀번호 재설정 토큰 발급: userId={}, 유효={}분", user.getId(), minutes);

        String debugLink = properties.passwordReset().exposeDebugLink() ? link : null;
        return new PasswordResetRequested(true, minutes, debugLink);
    }

    /**
     * 토큰이 아직 쓸 수 있는지 확인한다 (S13-06 진입 시).
     *
     * @param rawToken 링크의 토큰
     * @return 유효성과 만료 일시
     * @throws BusinessException 없거나 만료·사용된 토큰
     */
    public PasswordResetTokenStatus validate(String rawToken) {
        UserToken token = findUsable(rawToken);
        return new PasswordResetTokenStatus(true, token.getExpireDate());
    }

    /**
     * 새 비밀번호를 설정한다 (S13-09).
     *
     * @param request 토큰과 새 비밀번호
     * @return 변경 결과
     * @throws BusinessException 없거나 만료·사용된 토큰
     */
    @Transactional
    public PasswordResetResult confirm(PasswordResetConfirmRequest request) {
        UserToken token = findUsable(request.token());
        User user = userRepository.findByIdAndDeleted(token.getUserId(), YesNo.N)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        token.revoke();

        // 비밀번호를 바꿨으니 다른 기기에 남은 로그인을 모두 끊는다(탈취 대응).
        List<UserToken> refreshTokens = tokenRepository
                .findAllByUserIdAndTokenTypeAndDeleted(user.getId(), TokenType.REFRESH.value(), YesNo.N);
        refreshTokens.forEach(UserToken::revoke);

        log.info("비밀번호 재설정 완료: userId={}, 폐기한 세션={}", user.getId(), refreshTokens.size());
        return new PasswordResetResult(LocalDateTime.now(), refreshTokens.size());
    }

    private UserToken findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        return tokenRepository.findByTokenHashAndTokenTypeAndDeleted(sha256Hex(rawToken.trim()), TOKEN_TYPE, YesNo.N)
                .filter(UserToken::isUsable)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
