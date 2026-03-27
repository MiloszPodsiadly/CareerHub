package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolidJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    private static final String SOURCE_SOLID = "SOLIDJOBS";

    public void publishUrl(String url) {
        UrlMessage msg = new UrlMessage(url, SOURCE_SOLID, null);
        String routingKey = props.getRouting().getSolidUrls();

        log.debug("[mq] solid publish source={} routingKey={} url={}", SOURCE_SOLID, routingKey, url);

        rabbitTemplate.convertAndSend(
                props.getExchange(),
                routingKey,
                msg
        );
    }
}
