package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TheProtocolJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    private static final String SOURCE_THEPROTOCOL = "THEPROTOCOL";

    public void publishUrl(String url) {
        UrlMessage msg = new UrlMessage(url, SOURCE_THEPROTOCOL);

        rabbitTemplate.convertAndSend(
                props.getExchange(),
                props.getRouting().getUrls(),
                msg
        );
    }
}
