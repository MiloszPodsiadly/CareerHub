package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import com.milosz.podsiadly.backend.domain.loginandregister.EmailVerificationTokenRepository;
import com.milosz.podsiadly.backend.domain.loginandregister.mail.MailService;
import com.milosz.podsiadly.backend.domain.loginandregister.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailConsumer {

    private final MailService mailService;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final RabbitTemplate rabbit;
    private final MailMessagingProperties props;
    private final MailSendLogService mailSendLogService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @RabbitListener(
            queues = "${app.mailer.queue.send}",
            containerFactory = "mailRabbitListenerContainerFactory"
    )
    public void onMessage(MailSendCommand cmd, Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            Object header = message.getMessageProperties().getHeaders().get("x-message-id");
            if (header instanceof String h && !h.isBlank()) {
                messageId = h;
            }
        }
        if (messageId == null || messageId.isBlank()) {
            messageId = UUID.randomUUID().toString();
        }

        int attempt = getAttempt(message);

        try {
            if (!mailSendLogService.startOrSkip(messageId, cmd.type())) {
                return;
            }
            switch (cmd.type()) {
                case VERIFY_EMAIL -> handleVerify(cmd);
                case RESET_PASSWORD -> handleReset(cmd);
            }
            mailSendLogService.markSent(messageId, cmd.type());
        } catch (Exception ex) {
            mailSendLogService.markFailed(messageId, cmd.type(), ex.getMessage());
            log.warn("[mail] send failed type={} attempt={} err={}",
                    cmd.type(), attempt, ex.getMessage());
            requeue(cmd, attempt, ex, messageId);
        }
    }

    private void handleVerify(MailSendCommand cmd) {
        var tokenOpt = verificationTokens.findByToken(cmd.token());
        if (tokenOpt.isEmpty()) return;

        var token = tokenOpt.get();
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) return;
        if (token.getUser().isEmailVerified()) return;

        String link = frontendUrl + "/auth/verify?token=" + token.getToken();
        mailService.sendEmailVerification(token.getUser().getEmail(), link);
    }

    private void handleReset(MailSendCommand cmd) {
        var tokenOpt = resetTokens.findByToken(cmd.token());
        if (tokenOpt.isEmpty()) return;

        var token = tokenOpt.get();
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) return;

        String link = frontendUrl + "/auth/reset-password?token=" + token.getToken();
        mailService.sendPasswordResetEmail(token.getUser().getEmail(), link);
    }

    private int getAttempt(Message message) {
        var header = message.getMessageProperties().getHeaders().get("x-attempt");
        if (header instanceof Number n) return n.intValue();
        return 0;
    }

    private void requeue(MailSendCommand cmd, int attempt, Exception ex, String messageId) {
        String routing;
        int nextAttempt = attempt + 1;

        if (attempt == 0) routing = props.getRouting().getRetry1();
        else if (attempt == 1) routing = props.getRouting().getRetry5();
        else if (attempt == 2) routing = props.getRouting().getRetry30();
        else routing = props.getRouting().getDlq();

        if (routing.equals(props.getRouting().getDlq())) {
            log.warn("[mail] sending to DLQ type={} messageId={}", cmd.type(), messageId);
        }

        rabbit.convertAndSend(props.getExchange(), routing, cmd, msg -> {
            var p = msg.getMessageProperties();
            p.setMessageId(messageId);
            p.setHeader("x-message-id", messageId);
            p.setHeader("x-attempt", nextAttempt);
            p.setHeader("x-error", ex.getClass().getSimpleName());
            String msgText = ex.getMessage();
            if (msgText != null && msgText.length() > 200) {
                msgText = msgText.substring(0, 200);
            }
            if (msgText != null) {
                p.setHeader("x-error-message", msgText);
            }
            return msg;
        });
    }
}
