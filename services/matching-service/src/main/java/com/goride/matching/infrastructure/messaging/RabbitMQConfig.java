package com.goride.matching.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${goride.rabbitmq.exchange:goride.events}")
    private String exchangeName;

    @Value("${goride.rabbitmq.dlx:goride.dead-letter.exchange}")
    private String dlxName;

    @Value("${goride.rabbitmq.queues.trip-created:matching.trip-created.q}")
    private String tripCreatedQueueName;

    @Value("${goride.rabbitmq.queues.dlq:matching.dead-letter.queue}")
    private String dlqName;

    @Value("${goride.rabbitmq.routing-keys.trip-created:trip.created}")
    private String tripCreatedRoutingKey;

    @Value("${goride.rabbitmq.routing-keys.dlq:matching.dead-letter}")
    private String dlqRoutingKey;

    @Bean
    public TopicExchange gorideEventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    public Queue matchingTripCreatedQueue() {
        return QueueBuilder.durable(tripCreatedQueueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue matchingDeadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding tripCreatedBinding(Queue matchingTripCreatedQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(matchingTripCreatedQueue)
                .to(gorideEventsExchange)
                .with(tripCreatedRoutingKey);
    }

    @Bean
    public Binding dlqBinding(Queue matchingDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(matchingDeadLetterQueue)
                .to(deadLetterExchange)
                .with(dlqRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
