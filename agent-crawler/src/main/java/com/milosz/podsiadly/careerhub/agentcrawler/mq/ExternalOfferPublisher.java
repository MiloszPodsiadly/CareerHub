package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalOfferPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    public static final String SOURCE_PRACUJ = "PRACUJ"; // leaved to an future idea when enter EO publisher

    public void publish(ExternalOfferMessage msg) {
        String exchange = props.getExchange();
        String routingKey = props.getRouting().getExternalOffers();

        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalStateException("jobs.ingest.routing.externalOffers is empty");
        }
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalStateException("jobs.ingest.exchange is empty");
        }

        String correlationId = UUID.randomUUID().toString();

        MessagePostProcessor mpp = (Message message) -> {
            message.getMessageProperties().setCorrelationId(correlationId);
            message.getMessageProperties().setHeader("x-source", msg.source());
            message.getMessageProperties().setHeader("x-external-id", msg.externalId());
            message.getMessageProperties().setHeader("x-details-url", msg.applyUrl());
            return message;
        };

        rabbitTemplate.convertAndSend(exchange, routingKey, msg, mpp);

        log.info("[mq] externalOffer published source={} externalId={} routingKey={} corrId={}",
                msg.source(), msg.externalId(), routingKey, correlationId);
    }
}
