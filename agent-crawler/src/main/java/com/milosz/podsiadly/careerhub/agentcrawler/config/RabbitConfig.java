package com.milosz.podsiadly.careerhub.agentcrawler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milosz.podsiadly.careerhub.agentcrawler.ingest.config.SilentAmqpErrorHandler;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter conv = new Jackson2JsonMessageConverter(objectMapper);
        conv.setCreateMessageIds(true);
        return conv;
    }

    @Bean
    public DirectExchange jobsExchange(IngestMessagingProperties props) {
        return new DirectExchange(props.getExchange(), true, false);
    }

    @Bean
    public Declarables urlTopology(DirectExchange jobsExchange, IngestMessagingProperties props) {
        List<Declarable> declarables = new ArrayList<>();

        for (String source : props.externalOfferSources()) {
            Queue primaryQueue = QueueBuilder.durable(props.urlsQueue(source)).build();
            Queue retry1Queue = QueueBuilder.durable(props.urlsRetry1Queue(source))
                    .withArgument("x-dead-letter-exchange", props.getExchange())
                    .withArgument("x-dead-letter-routing-key", props.urlsRouting(source))
                    .build();
            Queue retry5Queue = QueueBuilder.durable(props.urlsRetry5Queue(source))
                    .withArgument("x-dead-letter-exchange", props.getExchange())
                    .withArgument("x-dead-letter-routing-key", props.urlsRouting(source))
                    .build();
            Queue retry30Queue = QueueBuilder.durable(props.urlsRetry30Queue(source))
                    .withArgument("x-dead-letter-exchange", props.getExchange())
                    .withArgument("x-dead-letter-routing-key", props.urlsRouting(source))
                    .build();
            Queue dlqQueue = QueueBuilder.durable(props.urlsDlqQueue(source)).build();

            declarables.add(primaryQueue);
            declarables.add(retry1Queue);
            declarables.add(retry5Queue);
            declarables.add(retry30Queue);
            declarables.add(dlqQueue);
            declarables.add(BindingBuilder.bind(primaryQueue).to(jobsExchange).with(props.urlsRouting(source)));
            declarables.add(BindingBuilder.bind(retry1Queue).to(jobsExchange).with(props.urlsRetry1Routing(source)));
            declarables.add(BindingBuilder.bind(retry5Queue).to(jobsExchange).with(props.urlsRetry5Routing(source)));
            declarables.add(BindingBuilder.bind(retry30Queue).to(jobsExchange).with(props.urlsRetry30Routing(source)));
            declarables.add(BindingBuilder.bind(dlqQueue).to(jobsExchange).with(props.urlsDlqRouting(source)));
        }

        return new Declarables(declarables);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf,
                                         MessageConverter converter,
                                         DirectExchange jobsExchange) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(converter);
        tpl.setExchange(jobsExchange.getName());
        return tpl;
    }

    @Bean
    public ErrorHandler amqpErrorHandler() {
        return new SilentAmqpErrorHandler();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler
    ) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setErrorHandler(amqpErrorHandler);
        return factory;
    }

    @Bean
    SimpleRabbitListenerContainerFactory justJoinJobUrlListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties props
    ) {
        return jobUrlListenerFactory(connectionFactory, jacksonMessageConverter, configurer, amqpErrorHandler,
                props.listenerSettings("JUSTJOIN"));
    }

    @Bean
    SimpleRabbitListenerContainerFactory nofluffJobUrlListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties props
    ) {
        return jobUrlListenerFactory(connectionFactory, jacksonMessageConverter, configurer, amqpErrorHandler,
                props.listenerSettings("NOFLUFFJOBS"));
    }

    @Bean
    SimpleRabbitListenerContainerFactory solidJobUrlListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties props
    ) {
        return jobUrlListenerFactory(connectionFactory, jacksonMessageConverter, configurer, amqpErrorHandler,
                props.listenerSettings("SOLIDJOBS"));
    }

    @Bean
    SimpleRabbitListenerContainerFactory theProtocolJobUrlListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties props
    ) {
        return jobUrlListenerFactory(connectionFactory, jacksonMessageConverter, configurer, amqpErrorHandler,
                props.listenerSettings("THEPROTOCOL"));
    }

    @Bean
    SimpleRabbitListenerContainerFactory pracujJobUrlListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties props
    ) {
        return jobUrlListenerFactory(connectionFactory, jacksonMessageConverter, configurer, amqpErrorHandler,
                props.listenerSettings("PRACUJ"));
    }

    @Bean(name = "justJoinJobUrlQueue")
    public String justJoinJobUrlQueue(IngestMessagingProperties props) {
        return props.urlsQueue("JUSTJOIN");
    }

    @Bean(name = "nofluffJobUrlQueue")
    public String nofluffJobUrlQueue(IngestMessagingProperties props) {
        return props.urlsQueue("NOFLUFFJOBS");
    }

    @Bean(name = "solidJobUrlQueue")
    public String solidJobUrlQueue(IngestMessagingProperties props) {
        return props.urlsQueue("SOLIDJOBS");
    }

    @Bean(name = "theProtocolJobUrlQueue")
    public String theProtocolJobUrlQueue(IngestMessagingProperties props) {
        return props.urlsQueue("THEPROTOCOL");
    }

    @Bean(name = "pracujJobUrlQueue")
    public String pracujJobUrlQueue(IngestMessagingProperties props) {
        return props.urlsQueue("PRACUJ");
    }

    @Bean(name = "pracujJobUrlConsumerEnabled")
    public boolean pracujJobUrlConsumerEnabled(IngestMessagingProperties props) {
        return props.listenerSettings("PRACUJ").isEnabled();
    }

    @Bean
    public Declarables externalOfferTopology(DirectExchange jobsExchange, IngestMessagingProperties props) {
        List<Declarable> declarables = new ArrayList<>();

        for (String source : props.externalOfferSources()) {
            Queue primaryQueue = QueueBuilder.durable(props.externalOffersQueue(source)).build();
            Queue retry1Queue = QueueBuilder.durable(props.externalOffersRetry1Queue(source))
                    .withArgument("x-dead-letter-exchange", props.getExchange())
                    .withArgument("x-dead-letter-routing-key", props.externalOffersRouting(source))
                    .build();
            Queue retry5Queue = QueueBuilder.durable(props.externalOffersRetry5Queue(source))
                    .withArgument("x-dead-letter-exchange", props.getExchange())
                    .withArgument("x-dead-letter-routing-key", props.externalOffersRouting(source))
                    .build();
            Queue retry30Queue = QueueBuilder.durable(props.externalOffersRetry30Queue(source))
                    .withArgument("x-dead-letter-exchange", props.getExchange())
                    .withArgument("x-dead-letter-routing-key", props.externalOffersRouting(source))
                    .build();
            Queue dlqQueue = QueueBuilder.durable(props.externalOffersDlqQueue(source)).build();

            declarables.add(primaryQueue);
            declarables.add(retry1Queue);
            declarables.add(retry5Queue);
            declarables.add(retry30Queue);
            declarables.add(dlqQueue);
            declarables.add(BindingBuilder.bind(primaryQueue).to(jobsExchange).with(props.externalOffersRouting(source)));
            declarables.add(BindingBuilder.bind(retry1Queue).to(jobsExchange).with(props.externalOffersRetry1Routing(source)));
            declarables.add(BindingBuilder.bind(retry5Queue).to(jobsExchange).with(props.externalOffersRetry5Routing(source)));
            declarables.add(BindingBuilder.bind(retry30Queue).to(jobsExchange).with(props.externalOffersRetry30Routing(source)));
            declarables.add(BindingBuilder.bind(dlqQueue).to(jobsExchange).with(props.externalOffersDlqRouting(source)));
        }

        return new Declarables(declarables);
    }

    private SimpleRabbitListenerContainerFactory jobUrlListenerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jacksonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ErrorHandler amqpErrorHandler,
            IngestMessagingProperties.ListenerSettings settings
    ) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setErrorHandler(amqpErrorHandler);
        factory.setConcurrentConsumers(settings.getConcurrency());
        factory.setMaxConcurrentConsumers(settings.getMaxConcurrency());
        factory.setPrefetchCount(settings.getPrefetch());
        return factory;
    }

}
