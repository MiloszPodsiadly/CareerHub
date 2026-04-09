package com.milosz.podsiadly.careerhub.agentcrawler.integration.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import com.milosz.podsiadly.careerhub.agentcrawler.integration.AgentCrawlerIntegrationTestBase;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.ExternalOfferMessage;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.ExternalOfferPublisher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ExternalOfferPublisherIntegrationTests extends AgentCrawlerIntegrationTestBase {

    @Autowired
    private ExternalOfferPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessageConverter messageConverter;

    @Autowired
    private IngestMessagingProperties properties;

    @Test
    void should_publish_external_offer_message_with_headers_to_broker() {
        ExternalOfferMessage payload = new ExternalOfferMessage(
                "PRACUJ",
                "offer-123",
                "https://it.pracuj.pl/oferta-123",
                "Senior Java Developer",
                "Remote role",
                "CareerHub",
                "Warsaw",
                true,
                "SENIOR",
                "B2B",
                Set.of("B2B", "UOP"),
                25_000,
                32_000,
                "PLN",
                "MONTH",
                "https://it.pracuj.pl/oferta-123/apply",
                List.of("Java", "Spring"),
                Instant.parse("2026-04-01T10:00:00Z"),
                true
        );

        publisher.publish(payload);

        Message message = rabbitTemplate.receive(properties.externalOffersQueue("PRACUJ"), 5_000);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getHeaders().get("x-source")).isEqualTo("PRACUJ");
        assertThat(message.getMessageProperties().getHeaders().get("x-external-id")).isEqualTo("offer-123");
        assertThat(message.getMessageProperties().getHeaders().get("x-details-url"))
                .isEqualTo("https://it.pracuj.pl/oferta-123/apply");

        Object decoded = messageConverter.fromMessage(message);
        assertThat(decoded).isInstanceOf(ExternalOfferMessage.class);
        assertThat(decoded).isEqualTo(payload);
    }
}
