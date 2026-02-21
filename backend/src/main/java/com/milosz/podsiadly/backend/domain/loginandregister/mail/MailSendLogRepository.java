package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailSendLogRepository extends JpaRepository<MailSendLog, Long> {
    Optional<MailSendLog> findByMessageId(String messageId);
}
