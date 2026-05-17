package config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import redis.embedded.RedisServer;

@Configuration
public class EmbeddedRedisConfig {
    private RedisServer redisServer;
    @PostConstruct
    public void startRedis() {
        try {
            redisServer = RedisServer.builder().port(6379).setting("maxmemory 128M").build();
            redisServer.start();
        } catch (Exception e) {}
    }
    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) redisServer.stop();
    }
}
