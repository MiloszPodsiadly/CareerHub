package com.milosz.podsiadly.backend.ingest.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;

import java.util.Map;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    DirectExchange jobsExchange(IngestMessagingProperties p) {
        return new DirectExchange(p.getExchange(), true, false);
    }

    @Bean
    Queue urlsQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getUrls()).build();
    }

    @Bean
    Binding urlsBinding(Queue urlsQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(urlsQueue).to(jobsExchange).with(p.getRouting().getUrls());
    }

    @Bean
    Queue urlsRetryQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getUrlsRetry())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", p.getExchange(),
                        "x-dead-letter-routing-key", p.getRouting().getUrls()
                ))
                .build();
    }

    @Bean
    Binding urlsRetryBinding(Queue urlsRetryQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(urlsRetryQueue).to(jobsExchange).with(p.getRouting().getUrlsRetry());
    }

    @Bean
    Queue urlsDlqQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getUrlsDlq()).build();
    }

    @Bean
    Binding urlsDlqBinding(Queue urlsDlqQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(urlsDlqQueue).to(jobsExchange).with(p.getRouting().getUrlsDlq());
    }

    @Bean
    MessageConverter rabbitJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public ErrorHandler amqpErrorHandler() {
        return new SilentAmqpErrorHandler();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitJsonConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler
    ) {
        var f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, connectionFactory);
        f.setMessageConverter(rabbitJsonConverter);
        f.setDefaultRequeueRejected(false);
        f.setAutoStartup(true);
        f.setErrorHandler(amqpErrorHandler);
        f.setPrefetchCount(1);
        f.setConcurrentConsumers(1);
        f.setMaxConcurrentConsumers(1);
        return f;
    }

    @Bean
    Queue externalOffersQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getExternalOffers()).build();
    }

    @Bean
    Binding externalOffersBinding(Queue externalOffersQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder
                .bind(externalOffersQueue)
                .to(jobsExchange)
                .with(p.getRouting().getExternalOffers());
    }

}
