package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mail_send_log",
        indexes = {
                @Index(columnList = "message_id", unique = true),
                @Index(columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 128)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MailEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MailSendStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 256)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
