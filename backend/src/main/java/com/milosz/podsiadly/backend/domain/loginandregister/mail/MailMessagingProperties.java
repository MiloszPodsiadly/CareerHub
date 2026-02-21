package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component("mailMessagingProperties")
@ConfigurationProperties(prefix = "app.mailer")
public class MailMessagingProperties {

    private String exchange;
    private Routing routing = new Routing();
    private QueueNames queue = new QueueNames();
    private Listener listener = new Listener();

    @Getter
    @Setter
    public static class Routing {
        private String send;
        private String retry1;
        private String retry5;
        private String retry30;
        private String dlq;
    }

    @Getter
    @Setter
    public static class QueueNames {
        private String send;
        private String retry1;
        private String retry5;
        private String retry30;
        private String dlq;
    }

    @Getter
    @Setter
    public static class Listener {
        private int prefetch = 5;
        private int concurrency = 1;
    }
}
