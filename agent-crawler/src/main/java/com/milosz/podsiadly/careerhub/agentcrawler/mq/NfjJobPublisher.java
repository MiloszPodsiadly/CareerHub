package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NfjJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final IngestMessagingProperties props;

    private static final String SOURCE_NOFLUFF = "NOFLUFFJOBS";

    public void publishUrl(String url) {
        publishUrl(url, SOURCE_NOFLUFF, null);
    }

    public void publishUrl(String url, String source) {
        publishUrl(url, source, null);
    }

    public void publishUrl(String url, String source, String externalId) {
        UrlMessage msg = new UrlMessage(url, source, externalId);
        String routingKey = props.getRouting().getNfjUrls();

        log.debug("[mq] nfj publish source={} externalId={} routingKey={} url={}",
                source, externalId, routingKey, url);

        rabbitTemplate.convertAndSend(
                props.getExchange(),
                routingKey,
                msg
        );
    }
}
