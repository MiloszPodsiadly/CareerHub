package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MailSendLogService {

    private final MailSendLogRepository repo;

    @Transactional
    public boolean startOrSkip(String messageId, MailEventType type) {
        var now = LocalDateTime.now();
        var existing = repo.findByMessageId(messageId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == MailSendStatus.SENT) {
                return false;
            }
            existing.setStatus(MailSendStatus.PROCESSING);
            existing.setAttempts(existing.getAttempts() + 1);
            existing.setUpdatedAt(now);
            repo.save(existing);
            return true;
        }

        var log = MailSendLog.builder()
                .messageId(messageId)
                .eventType(type)
                .status(MailSendStatus.PROCESSING)
                .attempts(1)
                .createdAt(now)
                .updatedAt(now)
                .build();

        repo.save(log);
        return true;
    }

    @Transactional
    public void markSent(String messageId, MailEventType type) {
        var now = LocalDateTime.now();
        repo.findByMessageId(messageId).ifPresentOrElse(log -> {
            log.setStatus(MailSendStatus.SENT);
            log.setUpdatedAt(now);
            repo.save(log);
        }, () -> {
            var log = MailSendLog.builder()
                    .messageId(messageId)
                    .eventType(type)
                    .status(MailSendStatus.SENT)
                    .attempts(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            repo.save(log);
        });
    }

    @Transactional
    public void markFailed(String messageId, MailEventType type, String error) {
        var now = LocalDateTime.now();
        var normalizedError = error;
        if (normalizedError != null && normalizedError.length() > 256) {
            normalizedError = normalizedError.substring(0, 256);
        }
        final var trimmed = normalizedError;

        repo.findByMessageId(messageId).ifPresentOrElse(log -> {
            log.setStatus(MailSendStatus.FAILED);
            log.setLastError(trimmed);
            log.setUpdatedAt(now);
            repo.save(log);
        }, () -> {
            var log = MailSendLog.builder()
                    .messageId(messageId)
                    .eventType(type)
                    .status(MailSendStatus.FAILED)
                    .attempts(1)
                    .lastError(trimmed)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            repo.save(log);
        });
    }
}
