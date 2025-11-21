package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NfjJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    private static final String SOURCE_NOFLUFF = "NOFLUFFJOBS";

    public void publishUrl(String url) {
        publishUrl(url, SOURCE_NOFLUFF);
    }

    public void publishUrl(String url, String source) {
        UrlMessage msg = new UrlMessage(url, source);

        rabbitTemplate.convertAndSend(
                props.getExchange(),
                props.getRouting().getUrls(),
                msg
        );
    }
}
