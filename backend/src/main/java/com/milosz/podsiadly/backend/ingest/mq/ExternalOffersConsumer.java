package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.ingest.dto.ExternalOfferMessage;
import com.milosz.podsiadly.backend.job.domain.ContractType;
import com.milosz.podsiadly.backend.job.domain.JobLevel;
import com.milosz.podsiadly.backend.job.domain.JobSource;
import com.milosz.podsiadly.backend.job.domain.SalaryPeriod;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferData;
import com.milosz.podsiadly.backend.job.service.ingest.ExternalJobOfferIngestService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalOffersConsumer {

    private final ExternalJobOfferIngestService externalIngest;
    private final ExternalOffersRetryPublisher retryPublisher;
    private final Validator validator;

    @RabbitListener(
            queues = "#{@externalOfferPrimaryQueues}",
            containerFactory = "externalOffersRabbitListenerContainerFactory"
    )
    public void consume(ExternalOfferMessage msg, Message message) {
        if (msg == null) return;

        String messageId = resolveMessageId(message);
        int attempt = getAttempt(message);

        try {
            validateMessage(msg);

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

            var saved = externalIngest.ingest(source, externalId, data);

            if (saved == null) {
                log.info("[externalOffers] skipped missing inactive source={} externalId={}", source, externalId);
                return;
            }

            log.info("[externalOffers] ingested source={} externalId={} title={}",
                    source, externalId, msg.title());
        } catch (IllegalArgumentException ex) {
            retryPublisher.dlq(msg, ex, messageId, attempt);
        } catch (Exception ex) {
            retryPublisher.retry(msg, attempt, ex, messageId);
        }
    }

    private void validateMessage(ExternalOfferMessage msg) {
        var violations = validator.validate(msg);
        if (violations.isEmpty()) {
            return;
        }

        String joined = violations.stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; "));

        throw new IllegalArgumentException(joined);
    }

    private static int getAttempt(Message message) {
        var header = message.getMessageProperties().getHeaders().get("x-attempt");
        if (header instanceof Number n) return n.intValue();
        return 0;
    }

    private static String resolveMessageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            Object header = message.getMessageProperties().getHeaders().get("x-message-id");
            if (header instanceof String h && !h.isBlank()) {
                messageId = h;
            }
        }
        return (messageId == null || messageId.isBlank()) ? UUID.randomUUID().toString() : messageId;
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
