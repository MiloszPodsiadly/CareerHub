package com.milosz.podsiadly.careerhub.agentcrawler.ingest.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.mq.UrlMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlConsumer {

    private final JobUrlConsumeService consumeService;
    private final JobUrlRetryPublisher retryPublisher;

    @RabbitListener(
            id = "justJoinJobUrlConsumer",
            queues = "#{@justJoinJobUrlQueue}",
            containerFactory = "justJoinJobUrlListenerContainerFactory"
    )
    public void onJustJoinMessage(UrlMessage msg, Message message) throws Exception {
        onMessage(msg, message);
    }

    @RabbitListener(
            id = "nofluffJobUrlConsumer",
            queues = "#{@nofluffJobUrlQueue}",
            containerFactory = "nofluffJobUrlListenerContainerFactory"
    )
    public void onNofluffMessage(UrlMessage msg, Message message) throws Exception {
        onMessage(msg, message);
    }

    @RabbitListener(
            id = "solidJobUrlConsumer",
            queues = "#{@solidJobUrlQueue}",
            containerFactory = "solidJobUrlListenerContainerFactory"
    )
    public void onSolidMessage(UrlMessage msg, Message message) throws Exception {
        onMessage(msg, message);
    }

    @RabbitListener(
            id = "theProtocolJobUrlConsumer",
            queues = "#{@theProtocolJobUrlQueue}",
            containerFactory = "theProtocolJobUrlListenerContainerFactory"
    )
    public void onTheProtocolMessage(UrlMessage msg, Message message) throws Exception {
        onMessage(msg, message);
    }

    @RabbitListener(
            id = "pracujJobUrlConsumer",
            queues = "#{@pracujJobUrlQueue}",
            containerFactory = "pracujJobUrlListenerContainerFactory",
            autoStartup = "#{@pracujJobUrlConsumerEnabled}"
    )
    public void onPracujMessage(UrlMessage msg, Message message) throws Exception {
        onMessage(msg, message);
    }

    private void onMessage(UrlMessage msg, Message message) throws Exception {
        String messageId = resolveMessageId(message);
        int attempt = getAttempt(message);

        try {
            consumeService.consume(msg);
        } catch (ImmediateRequeueAmqpException ex) {
            retryPublisher.retry(msg, attempt, ex, messageId);
        } catch (AmqpRejectAndDontRequeueException ex) {
            retryPublisher.dlq(msg, ex, messageId, attempt);
        } catch (Exception ex) {
            retryPublisher.retry(msg, attempt, ex, messageId);
        }
    }

    private static int getAttempt(Message message) {
        var header = message.getMessageProperties().getHeaders().get("x-attempt");
        if (header instanceof Number n) return n.intValue();
        return 0;
    }

    private static String resolveMessageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            Object header = message.getMessageProperties().getHeaders().get("x-message-id");
            if (header instanceof String h && !h.isBlank()) {
                messageId = h;
            }
        }
        return (messageId == null || messageId.isBlank()) ? UUID.randomUUID().toString() : messageId;
    }
}
