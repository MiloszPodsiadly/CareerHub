package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailQueueConfig {

    @Bean
    DirectExchange mailExchange(MailMessagingProperties props) {
        return new DirectExchange(props.getExchange(), true, false);
    }

    @Bean
    Queue mailSendQueue(MailMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getSend()).build();
    }

    @Bean
    Queue mailRetry1Queue(MailMessagingProperties props, MailRetryProperties retry) {
        return QueueBuilder.durable(props.getQueue().getRetry1())
                .withArgument("x-message-ttl", retry.getAfter1().toMillis())
                .withArgument("x-dead-letter-exchange", props.getExchange())
                .withArgument("x-dead-letter-routing-key", props.getRouting().getSend())
                .build();
    }

    @Bean
    Queue mailRetry5Queue(MailMessagingProperties props, MailRetryProperties retry) {
        return QueueBuilder.durable(props.getQueue().getRetry5())
                .withArgument("x-message-ttl", retry.getAfter5().toMillis())
                .withArgument("x-dead-letter-exchange", props.getExchange())
                .withArgument("x-dead-letter-routing-key", props.getRouting().getSend())
                .build();
    }

    @Bean
    Queue mailRetry30Queue(MailMessagingProperties props, MailRetryProperties retry) {
        return QueueBuilder.durable(props.getQueue().getRetry30())
                .withArgument("x-message-ttl", retry.getAfter30().toMillis())
                .withArgument("x-dead-letter-exchange", props.getExchange())
                .withArgument("x-dead-letter-routing-key", props.getRouting().getSend())
                .build();
    }

    @Bean
    Queue mailDlqQueue(MailMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getDlq()).build();
    }

    @Bean
    Binding mailSendBinding(Queue mailSendQueue, DirectExchange mailExchange, MailMessagingProperties props) {
        return BindingBuilder.bind(mailSendQueue).to(mailExchange).with(props.getRouting().getSend());
    }

    @Bean
    Binding mailRetry1Binding(Queue mailRetry1Queue, DirectExchange mailExchange, MailMessagingProperties props) {
        return BindingBuilder.bind(mailRetry1Queue).to(mailExchange).with(props.getRouting().getRetry1());
    }

    @Bean
    Binding mailRetry5Binding(Queue mailRetry5Queue, DirectExchange mailExchange, MailMessagingProperties props) {
        return BindingBuilder.bind(mailRetry5Queue).to(mailExchange).with(props.getRouting().getRetry5());
    }

    @Bean
    Binding mailRetry30Binding(Queue mailRetry30Queue, DirectExchange mailExchange, MailMessagingProperties props) {
        return BindingBuilder.bind(mailRetry30Queue).to(mailExchange).with(props.getRouting().getRetry30());
    }

    @Bean
    Binding mailDlqBinding(Queue mailDlqQueue, DirectExchange mailExchange, MailMessagingProperties props) {
        return BindingBuilder.bind(mailDlqQueue).to(mailExchange).with(props.getRouting().getDlq());
    }

    @Bean
    SimpleRabbitListenerContainerFactory mailRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitJsonConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MailMessagingProperties props
    ) {
        var f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, connectionFactory);
        f.setMessageConverter(rabbitJsonConverter);
        f.setDefaultRequeueRejected(false);
        f.setAutoStartup(true);
        f.setPrefetchCount(props.getListener().getPrefetch());
        f.setConcurrentConsumers(props.getListener().getConcurrency());
        return f;
    }
}
