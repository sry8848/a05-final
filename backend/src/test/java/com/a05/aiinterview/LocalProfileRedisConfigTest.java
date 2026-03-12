package com.a05.aiinterview;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalProfileRedisConfigTest {

    @Test
    void localProfileShouldNotExcludeRedisAutoConfiguration() throws IOException {
        String yaml = new ClassPathResource("application-local.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertFalse(
                yaml.contains("org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"),
                "local profile excludes Redis auto-configuration, which prevents StringRedisTemplate from being created"
        );
    }
}
