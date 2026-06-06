package com.anurag.inventory_sync.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${REDIS_PORT:6379}")
    private int redisPort;

    @Value("${REDIS_PASSWORD:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        String protocol = redisPassword.isEmpty() ? "redis://" : "rediss://";
        String address = protocol + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(2)
                .setConnectionMinimumIdleSize(1)
                .setConnectTimeout(10000)
                .setTimeout(5000)
                .setRetryAttempts(3);

        return Redisson.create(config);
    }
}