package com.milosz.podsiadly.backend.ingest.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobUrlConsumer {

    private final JobUrlConsumeService consumeService;

    @RabbitListener(
            id = "jobUrlConsumer",
            queues = "#{ingestMessagingProperties.queue.urls}",
            containerFactory = "rabbitListenerContainerFactory",
            concurrency = "4-8",
            autoStartup = "false"
    )
    public void onMessage(UrlMessage msg) throws Exception {
        consumeService.consume(msg);
    }
}
