package com.milosz.podsiadly.backend.ingest.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlConsumer {

    private final JobUrlConsumeService consumeService;
    private final JobUrlRetryPublisher retryPublisher;

    @RabbitListener(
            id = "jobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.urls}",
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false"
    )
    public void onMessage(UrlMessage msg, Message message) throws Exception {
        try {
            consumeService.consume(msg);
        } catch (DelayedRetryException e) {
            retryPublisher.publishDelayedRetry(msg, e.getDelayMs(), currentRetryCount(message));
            throw e;
        }
    }

    private static int currentRetryCount(Message message) {
        if (message == null) {
            return 0;
        }

        Object raw = message.getMessageProperties().getHeaders().get(JobUrlRetryPublisher.RETRY_COUNT_HEADER);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
