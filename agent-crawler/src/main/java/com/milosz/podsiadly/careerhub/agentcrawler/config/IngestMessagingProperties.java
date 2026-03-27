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
        private String justjoinUrls;
        private String justjoinUrlsRetry;
        private String justjoinUrlsDlq;
        private String nfjUrls;
        private String nfjUrlsRetry;
        private String nfjUrlsDlq;
        private String solidUrls;
        private String solidUrlsRetry;
        private String solidUrlsDlq;
        private String theProtocolUrls;
        private String theProtocolUrlsRetry;
        private String theProtocolUrlsDlq;
        private String externalOffers;
    }

    @Getter @Setter
    public static class QueueNames {
        private String justjoinUrls;
        private String justjoinUrlsRetry;
        private String justjoinUrlsDlq;
        private String nfjUrls;
        private String nfjUrlsRetry;
        private String nfjUrlsDlq;
        private String solidUrls;
        private String solidUrlsRetry;
        private String solidUrlsDlq;
        private String theProtocolUrls;
        private String theProtocolUrlsRetry;
        private String theProtocolUrlsDlq;
        private String externalOffers;
    }
}
