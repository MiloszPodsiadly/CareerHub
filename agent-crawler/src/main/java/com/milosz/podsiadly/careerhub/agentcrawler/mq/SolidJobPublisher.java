package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SolidJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    private static final String SOURCE_SOLID = "SOLIDJOBS";

    public void publishUrl(String url) {
        UrlMessage msg = new UrlMessage(url, SOURCE_SOLID);

        rabbitTemplate.convertAndSend(
                props.getExchange(),
                props.getRouting().getUrls(),
                msg
        );
    }
}
