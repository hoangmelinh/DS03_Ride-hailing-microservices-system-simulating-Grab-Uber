package com.goride.payment.infrastructure.messaging;

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

    @Value("${goride.rabbitmq.queues.trip-completed:payment.trip-completed.q}")
    private String tripCompletedQueueName;

    @Value("${goride.rabbitmq.queues.dlq:payment.dead-letter.queue}")
    private String dlqName;

    @Value("${goride.rabbitmq.routing-keys.trip-completed:trip.completed}")
    private String tripCompletedRoutingKey;

    @Value("${goride.rabbitmq.routing-keys.dlq:payment.dead-letter}")
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
    public Queue paymentTripCompletedQueue() {
        return QueueBuilder.durable(tripCompletedQueueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding tripCompletedBinding(Queue paymentTripCompletedQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(paymentTripCompletedQueue)
                .to(gorideEventsExchange)
                .with(tripCompletedRoutingKey);
    }

    @Bean
    public Binding dlqBinding(Queue paymentDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentDeadLetterQueue)
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
