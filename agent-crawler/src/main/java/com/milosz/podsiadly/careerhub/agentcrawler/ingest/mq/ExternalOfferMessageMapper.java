package com.milosz.podsiadly.careerhub.agentcrawler.ingest.mq;

import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ExternalJobOfferData;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.model.ParsedExternalOffer;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.parser.JustJoinParser;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.ContractType;
import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;
import com.milosz.podsiadly.careerhub.agentcrawler.mq.ExternalOfferMessage;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class ExternalOfferMessageMapper {

    public static ExternalOfferMessage fromJustJoin(JustJoinParser.ParsedOffer offer) {
        return new ExternalOfferMessage(
                JobSource.JUSTJOIN.name(),
                offer.externalId(),
                offer.url(),
                safe(offer.title()),
                safe(offer.description()),
                safe(offer.companyName()),
                safe(offer.cityName()),
                offer.remote(),
                nameOf(offer.level()),
                safe(offer.contract()),
                offer.contracts() != null ? offer.contracts() : Set.of(),
                offer.min(),
                offer.max(),
                safe(offer.currency()),
                nameOf(offer.salaryPeriod(), "MONTH"),
                offer.url(),
                offer.techTags() != null ? offer.techTags() : List.of(),
                offer.publishedAt() != null ? offer.publishedAt() : Instant.now(),
                Boolean.TRUE
        );
    }

    public static ExternalOfferMessage fromParsedOffer(ParsedExternalOffer parsedOffer) {
        return fromData(parsedOffer.source(), parsedOffer.externalId(), parsedOffer.data());
    }

    public static ExternalOfferMessage fromData(JobSource source, String externalId, ExternalJobOfferData data) {
        return new ExternalOfferMessage(
                source.name(),
                externalId,
                data.detailsUrl(),
                safe(data.title()),
                safe(data.description()),
                safe(data.companyName()),
                safe(data.cityName()),
                data.remote(),
                nameOf(data.level()),
                nameOf(data.mainContract()),
                nameSet(data.contracts()),
                data.salaryMin(),
                data.salaryMax(),
                safe(data.currency()),
                nameOf(data.salaryPeriod(), "MONTH"),
                data.applyUrl(),
                data.techTags() != null ? data.techTags() : List.of(),
                data.publishedAt() != null ? data.publishedAt() : Instant.now(),
                data.active() != null ? data.active() : Boolean.TRUE
        );
    }

    public static ExternalOfferMessage inactive(JobSource source, String externalId, String url) {
        return new ExternalOfferMessage(
                source.name(),
                externalId,
                url,
                "",
                "",
                "",
                "",
                null,
                "",
                "",
                Set.of(),
                null,
                null,
                "",
                "MONTH",
                url,
                List.of(),
                Instant.now(),
                Boolean.FALSE
        );
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String nameOf(Enum<?> value) {
        return value != null ? value.name() : "";
    }

    private static String nameOf(Enum<?> value, String fallback) {
        return value != null ? value.name() : fallback;
    }

    private static Set<String> nameSet(Set<ContractType> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return Set.of();
        }
        return contracts.stream().map(Enum::name).collect(Collectors.toSet());
    }
}
