package com.milosz.podsiadly.backend.ingest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
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

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    DirectExchange jobsExchange(IngestMessagingProperties p) {
        return new DirectExchange(p.getExchange(), true, false);
    }

    @Bean
    MessageConverter rabbitJsonConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setCreateMessageIds(true);
        return converter;
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
        return f;
    }

    @Bean
    SimpleRabbitListenerContainerFactory externalOffersRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitJsonConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties props
    ) {
        var f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, connectionFactory);
        f.setMessageConverter(rabbitJsonConverter);
        f.setDefaultRequeueRejected(false);
        f.setAutoStartup(true);
        f.setErrorHandler(amqpErrorHandler);
        f.setConcurrentConsumers(props.getExternalOffersConsumer().getConcurrency());
        f.setMaxConcurrentConsumers(props.getExternalOffersConsumer().getMaxConcurrency());
        f.setPrefetchCount(props.getExternalOffersConsumer().getPrefetch());
        return f;
    }

    @Bean
    String[] externalOfferPrimaryQueues(IngestMessagingProperties p) {
        return p.externalOfferPrimaryQueues();
    }

    @Bean
    Declarables externalOfferTopology(DirectExchange jobsExchange, IngestMessagingProperties p) {
        List<Declarable> declarables = new ArrayList<>();

        for (String source : p.externalOfferSources()) {
            Queue primaryQueue = QueueBuilder.durable(p.externalOffersQueue(source)).build();
            Queue retry1Queue = QueueBuilder.durable(p.externalOffersRetry1Queue(source))
                    .withArgument("x-dead-letter-exchange", p.getExchange())
                    .withArgument("x-dead-letter-routing-key", p.externalOffersRouting(source))
                    .build();
            Queue retry5Queue = QueueBuilder.durable(p.externalOffersRetry5Queue(source))
                    .withArgument("x-dead-letter-exchange", p.getExchange())
                    .withArgument("x-dead-letter-routing-key", p.externalOffersRouting(source))
                    .build();
            Queue retry30Queue = QueueBuilder.durable(p.externalOffersRetry30Queue(source))
                    .withArgument("x-dead-letter-exchange", p.getExchange())
                    .withArgument("x-dead-letter-routing-key", p.externalOffersRouting(source))
                    .build();
            Queue dlqQueue = QueueBuilder.durable(p.externalOffersDlqQueue(source)).build();

            declarables.add(primaryQueue);
            declarables.add(retry1Queue);
            declarables.add(retry5Queue);
            declarables.add(retry30Queue);
            declarables.add(dlqQueue);
            declarables.add(BindingBuilder.bind(primaryQueue).to(jobsExchange).with(p.externalOffersRouting(source)));
            declarables.add(BindingBuilder.bind(retry1Queue).to(jobsExchange).with(p.externalOffersRetry1Routing(source)));
            declarables.add(BindingBuilder.bind(retry5Queue).to(jobsExchange).with(p.externalOffersRetry5Routing(source)));
            declarables.add(BindingBuilder.bind(retry30Queue).to(jobsExchange).with(p.externalOffersRetry30Routing(source)));
            declarables.add(BindingBuilder.bind(dlqQueue).to(jobsExchange).with(p.externalOffersDlqRouting(source)));
        }

        return new Declarables(declarables);
    }
}
