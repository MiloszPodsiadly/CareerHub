package com.milosz.podsiadly.careerhub.agentcrawler.mq;

public record UrlMessage(
        String url,
        String source
) {}