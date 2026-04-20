package com.milosz.podsiadly.careerhub.agentcrawler.ingest.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.UrlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlRetryPublisher {

    private final RabbitTemplate rabbit;
    private final IngestMessagingProperties props;

    public void dlq(UrlMessage msg, Exception ex, String messageId, int attempt) {
        String source = props.resolveExternalOfferSource(msg.source());
        publish(msg, props.urlsDlqRouting(source), attempt, ex, messageId);
    }

    private void publish(UrlMessage msg, String routing, int nextAttempt, Exception ex, String messageId) {
        String effectiveMessageId = messageId != null && !messageId.isBlank()
                ? messageId
                : UUID.randomUUID().toString();
        Long delayMs = delayForRouting(routing);

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
            if (delayMs != null) {
                p.setExpiration(Long.toString(delayMs));
                p.setHeader("x-delay-ms", delayMs);
            }
            return message;
        });

        log.warn("[job-url] routed messageId={} source={} url={} routing={} attempt={} delayMs={}",
                effectiveMessageId, msg.source(), msg.url(), routing, nextAttempt, delayMs);
    }

    private String routingForAttempt(String source, int attempt) {
        if (attempt < 0) {
            return props.urlsDlqRouting(source);
        }
        if (attempt == 0) return props.urlsRetry1Routing(source);
        if (attempt == 1) return props.urlsRetry5Routing(source);
        if (attempt == 2) return props.urlsRetry30Routing(source);
        return props.urlsDlqRouting(source);
    }

    public void retryWithPolicy(UrlMessage msg, int attempt, Exception ex, String messageId) {
        String source = props.resolveExternalOfferSource(msg.source());
        String routing = routingForException(source, attempt, ex);
        int nextAttempt = nextAttemptForException(attempt, ex);
        publish(msg, routing, nextAttempt, ex, messageId);
    }

    private String routingForException(String source, int attempt, Exception ex) {
        if (ex instanceof NfjRateLimitException) {
            if (attempt <= 0) return props.urlsRetry5Routing(source);
            if (attempt == 1) return props.urlsRetry30Routing(source);
            return props.urlsDlqRouting(source);
        }

        return routingForAttempt(source, attempt);
    }

    private int nextAttemptForException(int attempt, Exception ex) {
        if (ex instanceof NfjRateLimitException) {
            if (attempt <= 0) return 2;
            if (attempt == 1) return 3;
            return attempt;
        }

        return attempt + 1;
    }

    private Long delayForRouting(String routing) {
        Duration baseDelay = baseDelayForRouting(routing);
        if (baseDelay == null) {
            return null;
        }
        return jitteredDelayMs(baseDelay);
    }

    private Duration baseDelayForRouting(String routing) {
        if (routing == null) {
            return null;
        }
        if (routing.startsWith(props.getRouting().getUrlsRetry1())) {
            return props.getRetry().getAfter1();
        }
        if (routing.startsWith(props.getRouting().getUrlsRetry5())) {
            return props.getRetry().getAfter5();
        }
        if (routing.startsWith(props.getRouting().getUrlsRetry30())) {
            return props.getRetry().getAfter30();
        }
        return null;
    }

    private long jitteredDelayMs(Duration baseDelay) {
        long baseMs = Math.max(1_000L, baseDelay.toMillis());
        double jitterFactor = Math.max(0.0d, Math.min(props.getRetry().getJitterFactor(), 0.95d));
        if (jitterFactor == 0.0d) {
            return baseMs;
        }

        long minMs = Math.max(1_000L, (long) Math.floor(baseMs * (1.0d - jitterFactor)));
        long maxMs = Math.max(minMs, (long) Math.ceil(baseMs * (1.0d + jitterFactor)));
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
    }
}
