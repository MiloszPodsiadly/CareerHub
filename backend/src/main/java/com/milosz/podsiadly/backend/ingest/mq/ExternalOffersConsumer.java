package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.ingest.dto.ExternalOfferMessage;
import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalOffersConsumer {

    private final ExternalJobOfferIngestService externalIngest;

    @RabbitListener(
            queues = "${jobs.ingest.queue.externalOffers}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void consume(ExternalOfferMessage msg) {
        if (msg == null) return;

        JobSource source = safeEnum(JobSource.class, msg.source(), JobSource.JUSTJOIN);
        String externalId = nvl(msg.externalId(), "");
        String url = nvl(msg.url(), msg.applyUrl());

        ExternalJobOfferData data = new ExternalJobOfferData(
                msg.title(),
                msg.description(),
                msg.companyName(),
                msg.cityName(),
                msg.remote(),
                safeEnum(JobLevel.class, msg.level(), null),
                safeContract(msg.mainContract()),
                safeContracts(msg.contracts()),
                msg.salaryMin(),
                msg.salaryMax(),
                msg.currency(),
                safeEnum(SalaryPeriod.class, msg.salaryPeriod(), SalaryPeriod.MONTH),
                url,
                msg.applyUrl(),
                msg.techTags() != null ? msg.techTags() : List.of(),
                List.of(),
                msg.publishedAt() != null ? msg.publishedAt() : Instant.now(),
                msg.active() != null ? msg.active() : Boolean.TRUE
        );

        externalIngest.ingest(source, externalId, data);

        log.info("[externalOffers] ingested source={} externalId={} title={}",
                source, externalId, msg.title());
    }

    private static String nvl(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static ContractType safeContract(String s) {
        if (s == null || s.isBlank()) return null;

        String v = s.trim().toUpperCase(Locale.ROOT);

        if (v.equals("UOP") || v.equals("UO P") || v.contains("UMOWA O PRAC")) v = "UOP";
        if (v.equals("B2B") || v.contains("KONTRAKT")) v = "B2B";

        return safeEnum(ContractType.class, v, null);
    }

    private static Set<ContractType> safeContracts(Set<String> in) {
        if (in == null || in.isEmpty()) return Collections.emptySet();
        return in.stream()
                .map(ExternalOffersConsumer::safeContract)
                .filter(x -> x != null)
                .collect(Collectors.toSet());
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, v);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
