package com.milosz.podsiadly.backend.ingest.config;

import com.milosz.podsiadly.backend.job.domain.JobSource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Getter @Setter
@Component("ingestMessagingProperties")
@ConfigurationProperties(prefix = "jobs.ingest")
public class IngestMessagingProperties {

    private String exchange;
    private Routing routing = new Routing();
    private QueueNames queue = new QueueNames();
    private Retry retry = new Retry();
    private ListenerSettings externalOffersConsumer = new ListenerSettings(4, 6, 50);
    private String sourceDefault;

    @Getter @Setter
    public static class Routing {
        private String urls;
        private String urlsRetry1;
        private String urlsRetry5;
        private String urlsRetry30;
        private String urlsDlq;
        private String externalOffers;
        private String externalOffersRetry1;
        private String externalOffersRetry5;
        private String externalOffersRetry30;
        private String externalOffersDlq;
    }

    @Getter @Setter
    public static class QueueNames {
        private String urls;
        private String urlsRetry1;
        private String urlsRetry5;
        private String urlsRetry30;
        private String urlsDlq;
        private String externalOffers;
        private String externalOffersRetry1;
        private String externalOffersRetry5;
        private String externalOffersRetry30;
        private String externalOffersDlq;
    }

    @Getter @Setter
    public static class Retry {
        private java.time.Duration after1 = java.time.Duration.ofMinutes(1);
        private java.time.Duration after5 = java.time.Duration.ofMinutes(5);
        private java.time.Duration after30 = java.time.Duration.ofMinutes(30);
        private double jitterFactor = 0.20d;
    }

    @Getter @Setter
    public static class ListenerSettings {
        private int concurrency;
        private int maxConcurrency;
        private int prefetch;

        public ListenerSettings() {
        }

        public ListenerSettings(int concurrency, int maxConcurrency, int prefetch) {
            this.concurrency = concurrency;
            this.maxConcurrency = maxConcurrency;
            this.prefetch = prefetch;
        }
    }

    public String[] externalOfferPrimaryQueues() {
        return externalOfferSources().stream()
                .map(this::externalOffersQueue)
                .toArray(String[]::new);
    }

    public List<String> externalOfferSources() {
        return Arrays.stream(JobSource.values())
                .filter(source -> source != JobSource.PLATFORM)
                .map(Enum::name)
                .toList();
    }

    public String externalOffersRouting(String source) {
        return withSourceSuffix(routing.getExternalOffers(), source);
    }

    public String externalOffersRetry1Routing(String source) {
        return withSourceSuffix(routing.getExternalOffersRetry1(), source);
    }

    public String externalOffersRetry5Routing(String source) {
        return withSourceSuffix(routing.getExternalOffersRetry5(), source);
    }

    public String externalOffersRetry30Routing(String source) {
        return withSourceSuffix(routing.getExternalOffersRetry30(), source);
    }

    public String externalOffersDlqRouting(String source) {
        return withSourceSuffix(routing.getExternalOffersDlq(), source);
    }

    public String externalOffersQueue(String source) {
        return withSourceSuffix(queue.getExternalOffers(), source);
    }

    public String externalOffersRetry1Queue(String source) {
        return withSourceSuffix(queue.getExternalOffersRetry1(), source);
    }

    public String externalOffersRetry5Queue(String source) {
        return withSourceSuffix(queue.getExternalOffersRetry5(), source);
    }

    public String externalOffersRetry30Queue(String source) {
        return withSourceSuffix(queue.getExternalOffersRetry30(), source);
    }

    public String externalOffersDlqQueue(String source) {
        return withSourceSuffix(queue.getExternalOffersDlq(), source);
    }

    public String resolveExternalOfferSource(String source) {
        if (isKnownExternalSource(source)) {
            return source.trim().toUpperCase(Locale.ROOT);
        }
        if (isKnownExternalSource(sourceDefault)) {
            return sourceDefault.trim().toUpperCase(Locale.ROOT);
        }
        return JobSource.JUSTJOIN.name();
    }

    private boolean isKnownExternalSource(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        try {
            JobSource jobSource = JobSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
            return jobSource != JobSource.PLATFORM;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String withSourceSuffix(String base, String source) {
        return base + "." + source.trim().toLowerCase(Locale.ROOT);
    }
}
