package com.a05.aiinterview.auth.service.impl;

import com.a05.aiinterview.auth.service.VerificationCodeStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 使用 Redis 存储验证码，支持多实例与重启后仍可校验（在过期时间内）。
 * 由 {@link com.a05.aiinterview.auth.config.VerificationCodeStoreConfig} 统一创建 Bean，本类不再使用 @Component。
 */
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String KEY_PREFIX = "auth:email:code:";
    private static final int DEFAULT_EXPIRE_SECONDS = 300;

    private final StringRedisTemplate redis;

    public RedisVerificationCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String scene, String target, String code, int expireSeconds) {
        String key = KEY_PREFIX + scene + ":" + target.trim().toLowerCase();
        redis.opsForValue().set(key, code, expireSeconds <= 0 ? DEFAULT_EXPIRE_SECONDS : expireSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean verifyAndInvalidate(String scene, String target, String code) {
        String key = KEY_PREFIX + scene + ":" + target.trim().toLowerCase();
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            return false;
        }
        redis.delete(key);
        return true;
    }
}
