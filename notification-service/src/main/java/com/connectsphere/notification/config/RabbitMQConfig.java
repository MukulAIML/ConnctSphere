package com.connectsphere.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue.notification-dispatch}")
    private String notificationDispatchQueue;

    @Value("${app.rabbitmq.routing-key.notification-dispatch}")
    private String notificationDispatchRoutingKey;

    @Bean
    public TopicExchange connectSphereExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue notificationDispatchQueue() {
        return QueueBuilder.durable(notificationDispatchQueue).build();
    }

    @Bean
    public Binding notificationDispatchBinding() {
        return BindingBuilder.bind(notificationDispatchQueue())
                .to(connectSphereExchange())
                .with(notificationDispatchRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
