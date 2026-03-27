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
    Queue justjoinUrlsQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getJustjoinUrls()).build();
    }

    @Bean
    Binding justjoinUrlsBinding(Queue justjoinUrlsQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(justjoinUrlsQueue).to(jobsExchange).with(p.getRouting().getJustjoinUrls());
    }

    @Bean
    Queue justjoinUrlsRetryQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getJustjoinUrlsRetry())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", p.getExchange(),
                        "x-dead-letter-routing-key", p.getRouting().getJustjoinUrls()
                ))
                .build();
    }

    @Bean
    Binding justjoinUrlsRetryBinding(Queue justjoinUrlsRetryQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(justjoinUrlsRetryQueue).to(jobsExchange).with(p.getRouting().getJustjoinUrlsRetry());
    }

    @Bean
    Queue justjoinUrlsDlqQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getJustjoinUrlsDlq()).build();
    }

    @Bean
    Binding justjoinUrlsDlqBinding(Queue justjoinUrlsDlqQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(justjoinUrlsDlqQueue).to(jobsExchange).with(p.getRouting().getJustjoinUrlsDlq());
    }

    @Bean
    Queue nfjUrlsQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getNfjUrls()).build();
    }

    @Bean
    Binding nfjUrlsBinding(Queue nfjUrlsQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(nfjUrlsQueue).to(jobsExchange).with(p.getRouting().getNfjUrls());
    }

    @Bean
    Queue nfjUrlsRetryQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getNfjUrlsRetry())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", p.getExchange(),
                        "x-dead-letter-routing-key", p.getRouting().getNfjUrls()
                ))
                .build();
    }

    @Bean
    Binding nfjUrlsRetryBinding(Queue nfjUrlsRetryQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(nfjUrlsRetryQueue).to(jobsExchange).with(p.getRouting().getNfjUrlsRetry());
    }

    @Bean
    Queue nfjUrlsDlqQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getNfjUrlsDlq()).build();
    }

    @Bean
    Binding nfjUrlsDlqBinding(Queue nfjUrlsDlqQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(nfjUrlsDlqQueue).to(jobsExchange).with(p.getRouting().getNfjUrlsDlq());
    }

    @Bean
    Queue solidUrlsQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getSolidUrls()).build();
    }

    @Bean
    Binding solidUrlsBinding(Queue solidUrlsQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(solidUrlsQueue).to(jobsExchange).with(p.getRouting().getSolidUrls());
    }

    @Bean
    Queue solidUrlsRetryQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getSolidUrlsRetry())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", p.getExchange(),
                        "x-dead-letter-routing-key", p.getRouting().getSolidUrls()
                ))
                .build();
    }

    @Bean
    Binding solidUrlsRetryBinding(Queue solidUrlsRetryQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(solidUrlsRetryQueue).to(jobsExchange).with(p.getRouting().getSolidUrlsRetry());
    }

    @Bean
    Queue solidUrlsDlqQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getSolidUrlsDlq()).build();
    }

    @Bean
    Binding solidUrlsDlqBinding(Queue solidUrlsDlqQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(solidUrlsDlqQueue).to(jobsExchange).with(p.getRouting().getSolidUrlsDlq());
    }

    @Bean
    Queue theProtocolUrlsQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getTheProtocolUrls()).build();
    }

    @Bean
    Binding theProtocolUrlsBinding(Queue theProtocolUrlsQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(theProtocolUrlsQueue).to(jobsExchange).with(p.getRouting().getTheProtocolUrls());
    }

    @Bean
    Queue theProtocolUrlsRetryQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getTheProtocolUrlsRetry())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", p.getExchange(),
                        "x-dead-letter-routing-key", p.getRouting().getTheProtocolUrls()
                ))
                .build();
    }

    @Bean
    Binding theProtocolUrlsRetryBinding(Queue theProtocolUrlsRetryQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(theProtocolUrlsRetryQueue).to(jobsExchange).with(p.getRouting().getTheProtocolUrlsRetry());
    }

    @Bean
    Queue theProtocolUrlsDlqQueue(IngestMessagingProperties p) {
        return QueueBuilder.durable(p.getQueue().getTheProtocolUrlsDlq()).build();
    }

    @Bean
    Binding theProtocolUrlsDlqBinding(Queue theProtocolUrlsDlqQueue, DirectExchange jobsExchange, IngestMessagingProperties p) {
        return BindingBuilder.bind(theProtocolUrlsDlqQueue).to(jobsExchange).with(p.getRouting().getTheProtocolUrlsDlq());
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
