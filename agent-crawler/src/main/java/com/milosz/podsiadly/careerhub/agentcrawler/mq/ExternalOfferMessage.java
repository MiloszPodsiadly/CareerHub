package com.milosz.podsiadly.careerhub.agentcrawler.mq;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ExternalOfferMessage(
        String source,
        String externalId,
        String url,
        String title,
        String description,
        String companyName,
        String cityName,
        Boolean remote,
        String level,
        String mainContract,
        Set<String> contracts,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        String salaryPeriod,
        String applyUrl,
        List<String> techTags,
        Instant publishedAt,
        Boolean active
) {}
