package com.connectsphere.search.config;

import com.connectsphere.search.repository.HashtagElasticsearchRepository;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;

@Configuration
public class MockDependenciesConfig {
    
    @Bean
    @Primary
    public ConnectionFactory mockConnectionFactory() {
        return Mockito.mock(ConnectionFactory.class);
    }
    
    @Bean
    @Primary
    public HashtagElasticsearchRepository mockHashtagElasticsearchRepository() {
        return Mockito.mock(HashtagElasticsearchRepository.class);
    }
}
