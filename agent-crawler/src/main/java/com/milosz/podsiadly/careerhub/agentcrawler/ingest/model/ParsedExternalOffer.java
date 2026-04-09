package com.milosz.podsiadly.careerhub.agentcrawler.ingest.model;

import com.milosz.podsiadly.careerhub.agentcrawler.job.domain.JobSource;

public record ParsedExternalOffer(
        JobSource source,
        String externalId,
        ExternalJobOfferData data
) {
    public ParsedExternalOffer withExternalId(String newExternalId) {
        return new ParsedExternalOffer(
                source,
                newExternalId != null && !newExternalId.isBlank() ? newExternalId : externalId,
                data
        );
    }
}
