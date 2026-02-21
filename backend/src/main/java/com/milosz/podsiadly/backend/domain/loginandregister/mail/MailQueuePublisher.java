package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailQueuePublisher {

    private final RabbitTemplate rabbit;
    private final MailMessagingProperties props;

    public void enqueueVerification(String token) {
        enqueue(new MailSendCommand(MailEventType.VERIFY_EMAIL, token, Instant.now()));
    }

    public void enqueuePasswordReset(String token) {
        enqueue(new MailSendCommand(MailEventType.RESET_PASSWORD, token, Instant.now()));
    }

    private void enqueue(MailSendCommand cmd) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(cmd);
                }
            });
        } else {
            publish(cmd);
        }
    }

    private void publish(MailSendCommand cmd) {
        rabbit.convertAndSend(
                props.getExchange(),
                props.getRouting().getSend(),
                cmd,
                this::withDefaults
        );
    }

    private Message withDefaults(Message message) {
        var props = message.getMessageProperties();
        if (props.getMessageId() == null) {
            props.setMessageId(UUID.randomUUID().toString());
        }
        if (!props.getHeaders().containsKey("x-attempt")) {
            props.setHeader("x-attempt", 0);
        }
        return message;
    }
}
