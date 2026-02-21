package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mailer.enabled", havingValue = "false")
public class NoopMailService implements MailService {
    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        log.info("[mail] noop reset email to={}", to);
    }

    @Override
    public void sendEmailVerification(String to, String verifyLink) {
        log.info("[mail] noop verify email to={}", to);
    }
}
