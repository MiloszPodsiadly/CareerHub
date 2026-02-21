package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import java.time.Instant;

public record MailSendCommand(
        MailEventType type,
        String token,
        Instant createdAt
) {
}
