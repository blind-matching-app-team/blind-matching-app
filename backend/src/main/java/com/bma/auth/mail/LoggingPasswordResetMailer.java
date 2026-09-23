package com.bma.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 발송 없이 링크를 로그에 남기는 개발용 메일러. {@code app.mail.mode=log}(기본값)일 때 등록된다.
 *
 * <p>로컬·검증 환경에서는 SMTP 계정 없이 흐름을 확인할 수 있어야 한다. 링크에는 토큰이 들어 있으므로
 * 운영에서는 반드시 {@code smtp} 로 바꾼다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    @Override
    public void sendResetLink(String toEmail, String resetLink, int expireMinutes) {
        log.warn("[메일 미발송 - log 모드] 비밀번호 재설정 링크: to={}, link={}, 유효={}분. "
                + "운영에서는 app.mail.mode=smtp 로 설정하세요.", toEmail, resetLink, expireMinutes);
    }
}
