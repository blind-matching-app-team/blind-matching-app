package com.bma.auth.mail;

/**
 * 비밀번호 재설정 메일 발송 추상화. 구현체는 {@code app.mail.mode} 로 고른다(log/smtp).
 */
public interface PasswordResetMailer {

    /**
     * 재설정 링크 메일을 보낸다.
     *
     * @param toEmail       수신 이메일
     * @param resetLink     토큰이 포함된 링크
     * @param expireMinutes 링크 유효 시간(분)
     */
    void sendResetLink(String toEmail, String resetLink, int expireMinutes);
}
