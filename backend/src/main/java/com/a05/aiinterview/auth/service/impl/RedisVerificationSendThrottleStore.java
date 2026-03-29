package com.a05.aiinterview.auth.service.impl;

import com.a05.aiinterview.auth.service.VerificationSendThrottleStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisVerificationSendThrottleStore implements VerificationSendThrottleStore {

    private static final String KEY_PREFIX = "auth:email:cooldown:";
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;

    private final StringRedisTemplate redis;

    public RedisVerificationSendThrottleStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String scene, String target, int cooldownSeconds) {
        String key = key(scene, target);
        int ttlSeconds = cooldownSeconds > 0 ? cooldownSeconds : DEFAULT_COOLDOWN_SECONDS;
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String scene, String target) {
        redis.delete(key(scene, target));
    }

    private static String key(String scene, String target) {
        return KEY_PREFIX + scene + ":" + target.trim().toLowerCase();
    }
}
