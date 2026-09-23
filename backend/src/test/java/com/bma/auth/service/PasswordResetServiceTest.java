package com.bma.auth.service;

import com.bma.auth.dto.AuthDtos.PasswordResetConfirmRequest;
import com.bma.auth.dto.AuthDtos.PasswordResetRequested;
import com.bma.auth.entity.UserToken;
import com.bma.auth.mail.PasswordResetMailer;
import com.bma.auth.repository.UserTokenRepository;
import com.bma.common.config.AppProperties;
import com.bma.common.entity.YesNo;
import com.bma.common.exception.BusinessException;
import com.bma.common.exception.ErrorCode;
import com.bma.common.security.TokenType;
import com.bma.support.TestProperties;
import com.bma.user.entity.User;
import com.bma.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비밀번호 재설정 흐름의 보안 규칙을 고정한다: 존재 여부 비노출, 토큰 해시 저장, 일회성, 만료, 세션 폐기.
 */
class PasswordResetServiceTest {

    private UserRepository userRepository;
    private UserTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordResetMailer mailer;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(UserTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        mailer = mock(PasswordResetMailer.class);
        AppProperties properties = TestProperties.defaults();
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, mailer, properties);
        when(tokenRepository.findAllByUserIdAndTokenTypeAndDeleted(any(), anyString(), anyString()))
                .thenReturn(List.of());
    }

    private User activeUser(long id) {
        User user = User.createLocal("me@example.com", "$2a$old", null);
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("미가입 이메일도 같은 응답(sent=true)이고 토큰·메일은 만들지 않는다")
    void unknownEmailIsSilent() {
        when(userRepository.findByEmailAndDeleted("nobody@example.com", YesNo.N)).thenReturn(Optional.empty());

        PasswordResetRequested result = service.requestReset("Nobody@Example.com ");

        assertThat(result.sent()).isTrue();
        assertThat(result.expiresInMinutes()).isEqualTo(30);
        assertThat(result.debugResetLink()).isNull();
        verify(tokenRepository, never()).save(any());
        verify(mailer, never()).sendResetLink(anyString(), anyString(), Mockito.anyInt());
    }

    @Test
    @DisplayName("소셜 전용 계정(비밀번호 없음)도 조용히 같은 응답을 준다")
    void socialOnlyAccountIsSilent() {
        User social = User.createSocial("me@example.com", "KAKAO", "key");
        social.setId(7L);
        when(userRepository.findByEmailAndDeleted("me@example.com", YesNo.N)).thenReturn(Optional.of(social));

        assertThat(service.requestReset("me@example.com").sent()).isTrue();
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 계정이면 토큰은 해시로만 저장되고, 이전 토큰은 폐기되며, 메일 링크에 원문 토큰이 실린다")
    void issuesHashedTokenAndMailsLink() {
        User user = activeUser(7L);
        when(userRepository.findByEmailAndDeleted("me@example.com", YesNo.N)).thenReturn(Optional.of(user));
        UserToken previous = UserToken.issue(7L, PasswordResetService.TOKEN_TYPE, "old-hash",
                LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findAllByUserIdAndTokenTypeAndDeleted(7L, PasswordResetService.TOKEN_TYPE, YesNo.N))
                .thenReturn(List.of(previous));

        PasswordResetRequested result = service.requestReset("me@example.com");

        assertThat(previous.isDeleted()).isTrue();
        ArgumentCaptor<UserToken> saved = ArgumentCaptor.forClass(UserToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq("me@example.com"), link.capture(), eq(30));

        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(link.getValue()).startsWith("http://localhost:5173/reset-password?token=");
        assertThat(saved.getValue().getTokenType()).isEqualTo("PASSWORD_RESET");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetService.sha256Hex(rawToken));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpireDate()).isAfter(LocalDateTime.now().plusMinutes(29));
        // 테스트 설정은 expose-debug-link=true 라 링크가 응답에도 실린다.
        assertThat(result.debugResetLink()).isEqualTo(link.getValue());
    }

    @Test
    @DisplayName("확정: 비밀번호를 새 해시로 바꾸고 토큰을 소진시키며 리프레시 토큰을 모두 폐기한다")
    void confirmChangesPasswordAndEndsSessions() {
        User user = activeUser(7L);
        UserToken token = UserToken.issue(7L, PasswordResetService.TOKEN_TYPE,
                PasswordResetService.sha256Hex("raw-token"), LocalDateTime.now().plusMinutes(5));
        UserToken refresh = UserToken.issue(7L, TokenType.REFRESH.value(), "r1", LocalDateTime.now().plusDays(1));
        when(tokenRepository.findByTokenHashAndTokenTypeAndDeleted(
                PasswordResetService.sha256Hex("raw-token"), PasswordResetService.TOKEN_TYPE, YesNo.N))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdAndDeleted(7L, YesNo.N)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$new");
        when(tokenRepository.findAllByUserIdAndTokenTypeAndDeleted(7L, TokenType.REFRESH.value(), YesNo.N))
                .thenReturn(List.of(refresh));

        var result = service.confirm(new PasswordResetConfirmRequest("raw-token", "NewPass123"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$new");
        assertThat(token.isUsable()).isFalse();
        assertThat(refresh.isDeleted()).isTrue();
        assertThat(result.sessionsEnded()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 토큰, 이미 쓴 토큰, 모르는 토큰은 모두 AUTH_017")
    void invalidTokensRejected() {
        UserToken expired = UserToken.issue(7L, PasswordResetService.TOKEN_TYPE,
                PasswordResetService.sha256Hex("expired"), LocalDateTime.now().minusMinutes(1));
        UserToken used = UserToken.issue(7L, PasswordResetService.TOKEN_TYPE,
                PasswordResetService.sha256Hex("used"), LocalDateTime.now().plusMinutes(5));
        used.revoke();
        when(tokenRepository.findByTokenHashAndTokenTypeAndDeleted(
                PasswordResetService.sha256Hex("expired"), PasswordResetService.TOKEN_TYPE, YesNo.N))
                .thenReturn(Optional.of(expired));
        when(tokenRepository.findByTokenHashAndTokenTypeAndDeleted(
                PasswordResetService.sha256Hex("used"), PasswordResetService.TOKEN_TYPE, YesNo.N))
                .thenReturn(Optional.of(used));

        for (String raw : List.of("expired", "used", "unknown", " ")) {
            assertThatThrownBy(() -> service.validate(raw))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        verify(passwordEncoder, never()).encode(anyString());
    }
}
