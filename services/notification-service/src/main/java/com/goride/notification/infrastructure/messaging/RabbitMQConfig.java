package com.goride.notification.infrastructure.messaging;

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

    @Value("${goride.rabbitmq.queues.events:notification.events.q}")
    private String eventsQueueName;

    @Value("${goride.rabbitmq.queues.dlq:notification.dead-letter.queue}")
    private String dlqName;

    @Value("${goride.rabbitmq.routing-keys.dlq:notification.dead-letter}")
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
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(eventsQueueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding tripEventsBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with("trip.#");
    }

    @Bean
    public Binding paymentEventsBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with("payment.#");
    }

    @Bean
    public Binding driverEventsBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with("driver.#");
    }

    @Bean
    public Binding notificationDlqBinding(Queue notificationDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
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
