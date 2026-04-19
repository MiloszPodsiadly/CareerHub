package com.milosz.podsiadly.careerhub.agentcrawler.ingest.mq;

import org.springframework.amqp.ImmediateRequeueAmqpException;

public class NfjRateLimitException extends ImmediateRequeueAmqpException {

    public NfjRateLimitException(String message) {
        super(message);
    }
}
