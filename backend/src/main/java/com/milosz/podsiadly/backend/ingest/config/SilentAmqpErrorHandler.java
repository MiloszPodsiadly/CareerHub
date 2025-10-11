package com.milosz.podsiadly.backend.ingest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.util.ErrorHandler;


final class SilentAmqpErrorHandler implements ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(SilentAmqpErrorHandler.class);

    @Override
    public void handleError(Throwable t) {
        Throwable root = unwrap(t);

        if (root instanceof ImmediateRequeueAmqpException) {
            log.debug("[amqp] requeue: {}", root.getMessage());
            return;
        }
        if (root instanceof AmqpRejectAndDontRequeueException) {
            log.debug("[amqp] drop: {}", root.getMessage());
            return;
        }

        log.warn("[amqp] listener error: {}", root.toString());
    }

    private static Throwable unwrap(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x;
    }
}
