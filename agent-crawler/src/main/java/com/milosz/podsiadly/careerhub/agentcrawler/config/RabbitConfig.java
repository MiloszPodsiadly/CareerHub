package com.milosz.podsiadly.careerhub.agentcrawler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
    public Queue justjoinUrlsQueue(IngestMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getJustjoinUrls()).build();
    }

    @Bean
    public Binding justjoinUrlsBinding(Queue justjoinUrlsQueue,
                                       DirectExchange jobsExchange,
                                       IngestMessagingProperties props) {
        return BindingBuilder
                .bind(justjoinUrlsQueue)
                .to(jobsExchange)
                .with(props.getRouting().getJustjoinUrls());
    }

    @Bean
    public Queue nfjUrlsQueue(IngestMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getNfjUrls()).build();
    }

    @Bean
    public Binding nfjUrlsBinding(Queue nfjUrlsQueue,
                                  DirectExchange jobsExchange,
                                  IngestMessagingProperties props) {
        return BindingBuilder
                .bind(nfjUrlsQueue)
                .to(jobsExchange)
                .with(props.getRouting().getNfjUrls());
    }

    @Bean
    public Queue solidUrlsQueue(IngestMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getSolidUrls()).build();
    }

    @Bean
    public Binding solidUrlsBinding(Queue solidUrlsQueue,
                                    DirectExchange jobsExchange,
                                    IngestMessagingProperties props) {
        return BindingBuilder
                .bind(solidUrlsQueue)
                .to(jobsExchange)
                .with(props.getRouting().getSolidUrls());
    }

    @Bean
    public Queue theProtocolUrlsQueue(IngestMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getTheProtocolUrls()).build();
    }

    @Bean
    public Binding theProtocolUrlsBinding(Queue theProtocolUrlsQueue,
                                          DirectExchange jobsExchange,
                                          IngestMessagingProperties props) {
        return BindingBuilder
                .bind(theProtocolUrlsQueue)
                .to(jobsExchange)
                .with(props.getRouting().getTheProtocolUrls());
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
    public Queue externalOffersQueue(IngestMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getExternalOffers()).build();
    }

    @Bean
    public Binding externalOffersBinding(Queue externalOffersQueue,
                                         DirectExchange jobsExchange,
                                         IngestMessagingProperties props) {
        return BindingBuilder
                .bind(externalOffersQueue)
                .to(jobsExchange)
                .with(props.getRouting().getExternalOffers());
    }

}
