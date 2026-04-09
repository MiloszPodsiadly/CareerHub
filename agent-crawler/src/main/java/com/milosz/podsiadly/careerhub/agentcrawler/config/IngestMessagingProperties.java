package com.milosz.podsiadly.careerhub.agentcrawler.config;

import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jobs.ingest")
public class IngestMessagingProperties {

    private String exchange;
    private Routing routing = new Routing();
    private QueueNames queue = new QueueNames();
    private Retry retry = new Retry();
    private UrlConsumers urlConsumer = new UrlConsumers();
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
    }

    @Getter @Setter
    public static class UrlConsumers {
        private ListenerSettings justjoin = new ListenerSettings(true, 1, 2, 20);
        private ListenerSettings nofluffjobs = new ListenerSettings(true, 1, 2, 5);
        private ListenerSettings solidjobs = new ListenerSettings(true, 1, 2, 5);
        private ListenerSettings theprotocol = new ListenerSettings(true, 1, 2, 10);
        private ListenerSettings pracuj = new ListenerSettings(false, 1, 2, 5);
    }

    @Getter @Setter
    public static class ListenerSettings {
        private boolean enabled;
        private int concurrency;
        private int maxConcurrency;
        private int prefetch;

        public ListenerSettings() {
        }

        public ListenerSettings(boolean enabled, int concurrency, int maxConcurrency, int prefetch) {
            this.enabled = enabled;
            this.concurrency = concurrency;
            this.maxConcurrency = maxConcurrency;
            this.prefetch = prefetch;
        }
    }

    public List<String> externalOfferSources() {
        return Arrays.stream(JobSource.values())
                .map(Enum::name)
                .toList();
    }

    public String[] urlPrimaryQueues() {
        return externalOfferSources().stream()
                .map(this::urlsQueue)
                .toArray(String[]::new);
    }

    public String urlsRouting(String source) {
        return withSourceSuffix(routing.getUrls(), source);
    }

    public String urlsRetry1Routing(String source) {
        return withSourceSuffix(routing.getUrlsRetry1(), source);
    }

    public String urlsRetry5Routing(String source) {
        return withSourceSuffix(routing.getUrlsRetry5(), source);
    }

    public String urlsRetry30Routing(String source) {
        return withSourceSuffix(routing.getUrlsRetry30(), source);
    }

    public String urlsDlqRouting(String source) {
        return withSourceSuffix(routing.getUrlsDlq(), source);
    }

    public String urlsQueue(String source) {
        return withSourceSuffix(queue.getUrls(), source);
    }

    public String urlsRetry1Queue(String source) {
        return withSourceSuffix(queue.getUrlsRetry1(), source);
    }

    public String urlsRetry5Queue(String source) {
        return withSourceSuffix(queue.getUrlsRetry5(), source);
    }

    public String urlsRetry30Queue(String source) {
        return withSourceSuffix(queue.getUrlsRetry30(), source);
    }

    public String urlsDlqQueue(String source) {
        return withSourceSuffix(queue.getUrlsDlq(), source);
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

    public ListenerSettings listenerSettings(String source) {
        String normalized = resolveExternalOfferSource(source);
        return switch (JobSource.valueOf(normalized)) {
            case JUSTJOIN -> urlConsumer.getJustjoin();
            case NOFLUFFJOBS -> urlConsumer.getNofluffjobs();
            case SOLIDJOBS -> urlConsumer.getSolidjobs();
            case THEPROTOCOL -> urlConsumer.getTheprotocol();
            case PRACUJ -> urlConsumer.getPracuj();
        };
    }

    private boolean isKnownExternalSource(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        try {
            JobSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String withSourceSuffix(String base, String source) {
        return base + "." + source.trim().toLowerCase(Locale.ROOT);
    }
}
