package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlRetryPublisher {

    public static final String RETRY_COUNT_HEADER = "x-retry-count";
    private static final int MAX_RETRIES = 5;

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties properties;

    public void publishDelayedRetry(UrlMessage msg, long delayMs, int currentRetryCount) {
        int nextRetryCount = currentRetryCount + 1;

        if (nextRetryCount > MAX_RETRIES) {
            log.error("[ingest] max retries exceeded for source={} url={} -> DLQ",
                    msg.source(), msg.url());

            rabbitTemplate.convertAndSend(
                    properties.getExchange(),
                    dlqRoutingFor(msg),
                    msg,
                    message -> {
                        message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextRetryCount);
                        return message;
                    }
            );
            return;
        }

        log.warn("[ingest] delayed retry #{} for source={} url={} in {} ms",
                nextRetryCount, msg.source(), msg.url(), delayMs);

        MessagePostProcessor mpp = message -> {
            message.getMessageProperties().setExpiration(String.valueOf(delayMs));
            message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextRetryCount);
            return message;
        };

        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                retryRoutingFor(msg),
                msg,
                mpp
        );
    }

    private String retryRoutingFor(UrlMessage msg) {
        if (msg == null || msg.source() == null) {
            return properties.getRouting().getJustjoinUrlsRetry();
        }
        return switch (msg.source()) {
            case NOFLUFFJOBS -> properties.getRouting().getNfjUrlsRetry();
            case SOLIDJOBS -> properties.getRouting().getSolidUrlsRetry();
            case THEPROTOCOL -> properties.getRouting().getTheProtocolUrlsRetry();
            default -> properties.getRouting().getJustjoinUrlsRetry();
        };
    }

    private String dlqRoutingFor(UrlMessage msg) {
        if (msg == null || msg.source() == null) {
            return properties.getRouting().getJustjoinUrlsDlq();
        }
        return switch (msg.source()) {
            case NOFLUFFJOBS -> properties.getRouting().getNfjUrlsDlq();
            case SOLIDJOBS -> properties.getRouting().getSolidUrlsDlq();
            case THEPROTOCOL -> properties.getRouting().getTheProtocolUrlsDlq();
            default -> properties.getRouting().getJustjoinUrlsDlq();
        };
    }
}
