package com.milosz.podsiadly.backend.ingest.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean DirectExchange jobsExchange(IngestMessagingProperties p) {
        return new DirectExchange(p.getExchange(), true, false);
    }

    @Bean Queue urlsQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getUrls()).build();
    }

    @Bean Binding urlsBinding(Queue urlsQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(urlsQueue).to(jobsExchange).with(p.getRouting().getUrls());
    }
    @Bean
    MessageConverter rabbitJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            org.springframework.amqp.rabbit.connection.ConnectionFactory cf,
            MessageConverter rabbitJsonConverter) {
        var f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(rabbitJsonConverter);
        f.setDefaultRequeueRejected(false);
        f.setAutoStartup(false);
        return f;
    }

}
