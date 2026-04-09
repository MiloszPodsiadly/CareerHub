package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import com.milosz.podsiadly.backend.ingest.dto.ExternalOfferMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalOffersRetryPublisher {

    private final RabbitTemplate rabbit;
    private final IngestMessagingProperties props;

    public void retry(ExternalOfferMessage msg, int attempt, Exception ex, String messageId) {
        String source = props.resolveExternalOfferSource(msg.source());
        String routing = routingForAttempt(source, attempt);
        publish(msg, routing, attempt + 1, ex, messageId);
    }

    public void dlq(ExternalOfferMessage msg, Exception ex, String messageId, int attempt) {
        String source = props.resolveExternalOfferSource(msg.source());
        publish(msg, props.externalOffersDlqRouting(source), attempt, ex, messageId);
    }

    private void publish(ExternalOfferMessage msg, String routing, int nextAttempt, Exception ex, String messageId) {
        String effectiveMessageId = messageId != null && !messageId.isBlank()
                ? messageId
                : UUID.randomUUID().toString();

        rabbit.convertAndSend(props.getExchange(), routing, msg, message -> {
            var p = message.getMessageProperties();
            p.setMessageId(effectiveMessageId);
            p.setHeader("x-message-id", effectiveMessageId);
            p.setHeader("x-attempt", nextAttempt);
            p.setHeader("x-error", ex.getClass().getSimpleName());
            String msgText = ex.getMessage();
            if (msgText != null && msgText.length() > 200) {
                msgText = msgText.substring(0, 200);
            }
            if (msgText != null) {
                p.setHeader("x-error-message", msgText);
            }
            return message;
        });

        log.warn("[external-offers] routed messageId={} source={} externalId={} routing={} attempt={}",
                effectiveMessageId, msg.source(), msg.externalId(), routing, nextAttempt);
    }

    private String routingForAttempt(String source, int attempt) {
        if (attempt == 0) return props.externalOffersRetry1Routing(source);
        if (attempt == 1) return props.externalOffersRetry5Routing(source);
        if (attempt == 2) return props.externalOffersRetry30Routing(source);
        return props.externalOffersDlqRouting(source);
    }
}
