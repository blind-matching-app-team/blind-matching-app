package com.bma.auth.mail;

import com.bma.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP 로 실제 발송하는 메일러. {@code app.mail.mode=smtp} 일 때 등록되며 {@code spring.mail.*} 설정이 필요하다.
 *
 * <p>발송 실패는 호출자에게 전파하지 않는다. 요청 API 는 이메일 존재 여부를 숨기기 위해 항상 성공으로
 * 응답해야 하므로, 실패는 로그로만 남긴다(운영에서는 이 로그에 알림을 건다).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    @Override
    public void sendResetLink(String toEmail, String resetLink, int expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(toEmail);
        message.setSubject("[BMA] 비밀번호 재설정 안내");
        message.setText("""
                안녕하세요, BMA 입니다.

                아래 링크에서 새 비밀번호를 설정해 주세요. 링크는 %d분 동안만 유효합니다.

                %s

                본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(expireMinutes, resetLink));
        try {
            mailSender.send(message);
            log.info("비밀번호 재설정 메일 발송: to={}", toEmail);
        } catch (MailException e) {
            log.error("비밀번호 재설정 메일 발송 실패: to={}", toEmail, e);
        }
    }
}
