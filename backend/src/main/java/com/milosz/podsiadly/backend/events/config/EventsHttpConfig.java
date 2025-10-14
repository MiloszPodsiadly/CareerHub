package com.milosz.podsiadly.backend.events.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Configuration
public class EventsHttpConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(30));
        rf.setReadTimeout(Duration.ofSeconds(45));

        return builder
                .requestFactory(() -> rf)
                .defaultHeader(HttpHeaders.USER_AGENT, "CareerHub/1.0 (+https://example.com)")
                .build();
    }
}
