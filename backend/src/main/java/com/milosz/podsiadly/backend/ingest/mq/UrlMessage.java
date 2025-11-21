package com.milosz.podsiadly.backend.ingest.mq;

import com.milosz.podsiadly.backend.job.domain.JobSource;

public record UrlMessage(
        JobSource source,
        String url
) {}
