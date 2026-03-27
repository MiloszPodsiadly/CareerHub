package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import com.milosz.podsiadly.backend.ingest.mq.UrlMessage;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestPublisher {

    private final RabbitTemplate rabbit;
    private final IngestMessagingProperties p;

    public void publishUrl(String url, JobSource source) {
        String routingKey = routingKeyFor(source);

        log.debug("[ingest] publish source={} routingKey={} url={}", source, routingKey, url);

        rabbit.convertAndSend(
                p.getExchange(),
                routingKey,
                new UrlMessage(source, url, null)
        );
    }

    private String routingKeyFor(JobSource source) {
        if (source == null) {
            return p.getRouting().getJustjoinUrls();
        }
        return switch (source) {
            case NOFLUFFJOBS -> p.getRouting().getNfjUrls();
            case SOLIDJOBS -> p.getRouting().getSolidUrls();
            case THEPROTOCOL -> p.getRouting().getTheProtocolUrls();
            default -> p.getRouting().getJustjoinUrls();
        };
    }
}
