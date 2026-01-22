package com.milosz.podsiadly.careerhub.agentcrawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jobs.ingest")
public class IngestMessagingProperties {

    private String exchange;
    private Routing routing = new Routing();
    private QueueNames queue = new QueueNames();
    private String sourceDefault;

    @Getter @Setter
    public static class Routing {
        private String urls;
        private String externalOffers;
    }

    @Getter @Setter
    public static class QueueNames {
        private String urls;
        private String externalOffers;
    }
}
