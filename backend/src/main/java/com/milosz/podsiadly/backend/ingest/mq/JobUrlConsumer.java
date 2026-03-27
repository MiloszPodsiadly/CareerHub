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
            id = "justjoinJobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.justjoinUrls}",
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false"
    )
    public void onJustjoinMessage(UrlMessage msg, Message message) throws Exception {
        handle("justjoin-url", msg, message);
    }

    @RabbitListener(
            id = "nfjJobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.nfjUrls}",
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false"
    )
    public void onNfjMessage(UrlMessage msg, Message message) throws Exception {
        handle("nfj-url", msg, message);
    }

    @RabbitListener(
            id = "solidJobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.solidUrls}",
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false"
    )
    public void onSolidMessage(UrlMessage msg, Message message) throws Exception {
        handle("solid-url", msg, message);
    }

    @RabbitListener(
            id = "theProtocolJobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.theProtocolUrls}",
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false"
    )
    public void onTheProtocolMessage(UrlMessage msg, Message message) throws Exception {
        handle("theprotocol-url", msg, message);
    }

    private void handle(String listenerName, UrlMessage msg, Message message) throws Exception {
        log.debug("[ingest] listener={} source={} url={} thread={}",
                listenerName, msg.source(), msg.url(), Thread.currentThread().getName());
        try {
            consumeService.consume(msg);
        } catch (DelayedRetryException e) {
            log.debug("[ingest] listener={} delayed-retry source={} url={} delayMs={}",
                    listenerName, msg.source(), msg.url(), e.getDelayMs());
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
