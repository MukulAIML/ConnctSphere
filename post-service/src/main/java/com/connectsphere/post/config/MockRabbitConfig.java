package com.connectsphere.post.config;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MockRabbitConfig {
    @Bean
    @Primary
    public ConnectionFactory mockConnectionFactory() {
        return new ConnectionFactory() {
            @Override
            public Connection createConnection() throws AmqpException {
                return null;
            }

            @Override
            public String getHost() {
                return "localhost";
            }

            @Override
            public int getPort() {
                return 5672;
            }

            @Override
            public String getVirtualHost() {
                return "/";
            }

            @Override
            public String getUsername() {
                return "guest";
            }

            @Override
            public void addConnectionListener(ConnectionListener listener) {}

            @Override
            public boolean removeConnectionListener(ConnectionListener listener) {
                return false;
            }

            @Override
            public void clearConnectionListeners() {}
        };
    }
}
