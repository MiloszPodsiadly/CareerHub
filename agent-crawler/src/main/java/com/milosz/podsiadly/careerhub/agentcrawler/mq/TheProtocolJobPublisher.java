package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TheProtocolJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    private static final String SOURCE_THEPROTOCOL = "THEPROTOCOL";

    public void publishUrl(String url) {
        UrlMessage msg = new UrlMessage(url, SOURCE_THEPROTOCOL, null);
        String routingKey = props.getRouting().getTheProtocolUrls();

        log.debug("[mq] theprotocol publish source={} routingKey={} url={}", SOURCE_THEPROTOCOL, routingKey, url);

        rabbitTemplate.convertAndSend(
                props.getExchange(),
                routingKey,
                msg
        );
    }
}
