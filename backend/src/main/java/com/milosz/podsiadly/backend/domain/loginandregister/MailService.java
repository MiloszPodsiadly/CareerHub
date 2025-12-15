package com.milosz.podsiadly.backend.domain.loginandregister;

public interface MailService {
    void sendPasswordResetEmail(String to, String resetLink);
}
