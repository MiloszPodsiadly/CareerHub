package com.milosz.podsiadly.backend.ingest.config;

import com.milosz.podsiadly.backend.ingest.mq.DelayedRetryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.util.ErrorHandler;


final class SilentAmqpErrorHandler implements ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(SilentAmqpErrorHandler.class);

    @Override
    public void handleError(Throwable t) {
        Throwable delayed = findCause(t, DelayedRetryException.class);
        if (delayed instanceof DelayedRetryException retry) {
            log.debug("[amqp] delayed retry: {}", retry.getMessage());
            return;
        }

        Throwable drop = findCause(t, AmqpRejectAndDontRequeueException.class);
        if (drop instanceof AmqpRejectAndDontRequeueException reject) {
            log.debug("[amqp] drop: {}", reject.getMessage());
            return;
        }

        Throwable root = unwrap(t);
        log.warn("[amqp] listener error: {}", root.toString());
    }

    private static Throwable unwrap(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x;
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Throwable x = t;
        while (x != null) {
            if (type.isInstance(x)) return type.cast(x);
            if (x.getCause() == x) break;
            x = x.getCause();
        }
        return null;
    }
}
