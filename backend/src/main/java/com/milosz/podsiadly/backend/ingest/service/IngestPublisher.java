package com.milosz.podsiadly.backend.ingest.service;

import com.milosz.podsiadly.backend.ingest.config.IngestMessagingProperties;
import com.milosz.podsiadly.backend.ingest.mq.UrlMessage;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestPublisher {

    private final RabbitTemplate rabbit;
    private final IngestMessagingProperties p;

    public void publishUrl(String url, JobSource source) {
        rabbit.convertAndSend(
                p.getExchange(),
                p.getRouting().getUrls(),
                new UrlMessage(source, url)
        );
    }
}
