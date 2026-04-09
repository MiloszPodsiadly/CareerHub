package com.milosz.podsiadly.careerhub.agentcrawler.ingest.service;

import com.milosz.podsiadly.careerhub.agentcrawler.config.IngestMessagingProperties;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.UrlMessage;
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
                p.urlsRouting(source.name()),
                new UrlMessage(url, source.name(), null)
        );
    }
}
