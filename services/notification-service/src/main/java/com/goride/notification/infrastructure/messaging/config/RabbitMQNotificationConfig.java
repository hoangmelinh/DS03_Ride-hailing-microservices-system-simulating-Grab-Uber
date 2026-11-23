package com.goride.notification.infrastructure.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQNotificationConfig {

    @Value("${goride.rabbitmq.exchange:goride.events}")
    private String exchangeName;

    @Value("${goride.rabbitmq.dlx:goride.dead-letter.exchange}")
    private String dlxName;

    @Value("${goride.rabbitmq.queues.notification-events:notification.events.q}")
    private String notificationEventsQueueName;

    @Value("${goride.rabbitmq.queues.dlq:notification.dead-letter.queue}")
    private String dlqName;

    @Value("${goride.rabbitmq.routing-keys.payment-succeeded:payment.succeeded}")
    private String paymentSucceededRoutingKey;

    @Value("${goride.rabbitmq.routing-keys.payment-failed:payment.failed}")
    private String paymentFailedRoutingKey;

    @Value("${goride.rabbitmq.routing-keys.trip-completed:trip.completed}")
    private String tripCompletedRoutingKey;

    @Value("${goride.rabbitmq.routing-keys.trip-started:trip.started}")
    private String tripStartedRoutingKey;

    @Value("${goride.rabbitmq.routing-keys.driver-assigned:trip.driver-assigned}")
    private String driverAssignedRoutingKey;

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
        return QueueBuilder.durable(notificationEventsQueueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding paymentSucceededBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with(paymentSucceededRoutingKey);
    }

    @Bean
    public Binding paymentFailedBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with(paymentFailedRoutingKey);
    }

    @Bean
    public Binding tripCompletedBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with(tripCompletedRoutingKey);
    }

    @Bean
    public Binding tripStartedBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with(tripStartedRoutingKey);
    }

    @Bean
    public Binding driverAssignedBinding(Queue notificationEventsQueue, TopicExchange gorideEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(gorideEventsExchange)
                .with(driverAssignedRoutingKey);
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
