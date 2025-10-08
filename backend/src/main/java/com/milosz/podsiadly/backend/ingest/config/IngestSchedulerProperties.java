package com.milosz.podsiadly.backend.ingest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Data
@Component("ingestSchedulerProperties")
@ConfigurationProperties(prefix = "ingest.scheduler")
public class IngestSchedulerProperties {

    private boolean enabled = true;
    private Duration initialDelay = Duration.ofMinutes(3);
    private Duration fixedDelay = Duration.ofHours(1);
    private List<Source> sources = List.of();

    @Data
    public static class Source {
        private String source;
        private String sitemap;
    }
}
