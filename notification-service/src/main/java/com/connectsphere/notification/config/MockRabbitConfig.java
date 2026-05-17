package com.connectsphere.notification.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;

@Configuration
public class MockRabbitConfig {
    @Bean
    @Primary
    public ConnectionFactory mockConnectionFactory() {
        return Mockito.mock(ConnectionFactory.class);
    }
}
