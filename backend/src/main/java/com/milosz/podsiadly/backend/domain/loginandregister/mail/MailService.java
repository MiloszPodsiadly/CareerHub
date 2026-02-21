package com.milosz.podsiadly.backend.domain.loginandregister.mail;

public interface MailService {
    void sendPasswordResetEmail(String to, String resetLink);
    void sendEmailVerification(String to, String verifyLink);
}
