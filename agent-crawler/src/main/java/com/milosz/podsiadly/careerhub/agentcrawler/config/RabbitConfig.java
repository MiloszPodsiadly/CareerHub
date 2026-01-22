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
    public Queue urlsQueue(IngestMessagingProperties props) {
        return QueueBuilder.durable(props.getQueue().getUrls()).build();
    }

    @Bean
    public Binding urlsBinding(Queue urlsQueue,
                               DirectExchange jobsExchange,
                               IngestMessagingProperties props) {
        return BindingBuilder
                .bind(urlsQueue)
                .to(jobsExchange)
                .with(props.getRouting().getUrls());
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
