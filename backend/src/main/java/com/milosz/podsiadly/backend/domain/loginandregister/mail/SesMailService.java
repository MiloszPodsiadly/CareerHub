package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import com.milosz.podsiadly.backend.domain.loginandregister.mail.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mailer.enabled", havingValue = "true", matchIfMissing = true)
public class SesMailService implements MailService {

    private final SesV2Client ses;
    private final SesMailProperties props;

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        requireFrom();

        String subject = "Reset your CareerHub password";
        String body = "Click this link to reset your password:\n" + resetLink
                + "\n\nIf you didn't request it, ignore this email.";

        send(to, subject, body);
    }

    @Override
    public void sendEmailVerification(String to, String verifyLink) {
        requireFrom();

        String subject = "Verify your CareerHub account";
        String body = "Click this link to verify your account:\n" + verifyLink;

        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        var destination = Destination.builder()
                .toAddresses(to)
                .build();

        var message = Message.builder()
                .subject(Content.builder().data(subject).build())
                .body(software.amazon.awssdk.services.sesv2.model.Body.builder()
                        .text(Content.builder().data(body).build())
                        .build())
                .build();

        var request = SendEmailRequest.builder()
                .fromEmailAddress(props.getFrom())
                .destination(destination)
                .content(EmailContent.builder().simple(message).build())
                .build();

        ses.sendEmail(request);
    }

    private void requireFrom() {
        if (props.getFrom() == null || props.getFrom().isBlank()) {
            throw new IllegalStateException("app.mailer.aws.from must be set");
        }
    }
}
