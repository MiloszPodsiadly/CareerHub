package com.milosz.podsiadly.backend.domain.loginandregister;

import com.milosz.podsiadly.backend.domain.loginandregister.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("spring.mail.username (MAIL_USER) must be set");
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Reset your CareerHub password");
        msg.setText("Click this link to reset your password:\n" + resetLink
                + "\n\nIf you didn't request it, ignore this email.");
        mailSender.send(msg);
    }
}
