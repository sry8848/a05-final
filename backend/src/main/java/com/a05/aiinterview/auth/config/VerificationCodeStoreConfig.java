package com.a05.aiinterview.auth.config;

import com.a05.aiinterview.auth.service.VerificationCodeStore;
import com.a05.aiinterview.auth.service.VerificationSendThrottleStore;
import com.a05.aiinterview.auth.service.impl.InMemoryVerificationCodeStore;
import com.a05.aiinterview.auth.service.impl.InMemoryVerificationSendThrottleStore;
import com.a05.aiinterview.auth.service.impl.RedisVerificationCodeStore;
import com.a05.aiinterview.auth.service.impl.RedisVerificationSendThrottleStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 验证码存储 Bean 配置。
 * 显式提供唯一的 VerificationCodeStore：有 Redis 时用 Redis 实现，否则用内存实现，
 * 避免仅靠 @ConditionalOnBean / @ConditionalOnMissingBean 时因 Bean 创建顺序导致没有实现类被注册。
 */
@Configuration
public class VerificationCodeStoreConfig {

    @Bean
    public VerificationCodeStore verificationCodeStore(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            return new RedisVerificationCodeStore(redis);
        }
        return new InMemoryVerificationCodeStore();
    }

    @Bean
    public VerificationSendThrottleStore verificationSendThrottleStore(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            return new RedisVerificationSendThrottleStore(redis);
        }
        return new InMemoryVerificationSendThrottleStore();
    }
}
