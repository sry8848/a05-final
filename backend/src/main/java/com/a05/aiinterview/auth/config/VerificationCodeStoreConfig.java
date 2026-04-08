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
 * 验证码存储配置类。
 * <p>
 * 负责配置验证码存储和发送限流的 Bean。
 * 支持两种存储后端：
 * <ul>
 *   <li><b>Redis</b>：分布式环境使用，数据持久化，支持集群部署</li>
 *   <li><b>内存（In-Memory）</b>：单机或测试环境使用，数据存储在 JVM 内存中</li>
 * </ul>
 *
 * <p>
 * <b>设计说明：</b>
 * 本配置使用 {@link ObjectProvider} 来延迟获取 {@link StringRedisTemplate}。
 * 这种方式比直接注入更灵活：
 * <ul>
 *   <li>当 Redis 可用时，自动使用 Redis 实现</li>
 *   <li>当 Redis 不可用（如未配置或连接失败）时，自动回退到内存实现</li>
 * </ul>
 *
 * <p>
 * <b>使用 ObjectProvider 的原因：</b>
 * 如果直接使用 @Autowired 注入 StringRedisTemplate，
 * 当 Redis 未配置时会导致应用启动失败。
 * 使用 ObjectProvider.getIfAvailable() 可以在 Redis 不可用时返回 null，
 * 从而自动切换到内存实现，保证应用在各种环境下都能正常启动。
 *
 * @author AI Interview Backend Team
 * @see VerificationCodeStore
 * @see VerificationSendThrottleStore
 * @see RedisVerificationCodeStore
 * @see InMemoryVerificationCodeStore
 * @see ObjectProvider
 */
@Configuration
public class VerificationCodeStoreConfig {

    /**
     * 配置验证码存储 Bean。
     * <p>
     * 根据 Redis 是否可用选择不同的存储实现：
     * <ul>
     *   <li>Redis 可用：使用 {@link RedisVerificationCodeStore}</li>
     *   <li>Redis 不可用：使用 {@link InMemoryVerificationCodeStore}</li>
     * </ul>
     *
     * @param redisTemplateProvider Redis 模板提供者，延迟获取
     * @return VerificationCodeStore 实现实例
     */
    @Bean
    public VerificationCodeStore verificationCodeStore(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        // 尝试获取 Redis 模板实例
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            // Redis 可用，使用 Redis 存储（支持分布式）
            return new RedisVerificationCodeStore(redis);
        }
        // Redis 不可用，回退到内存存储（单机环境）
        return new InMemoryVerificationCodeStore();
    }

    /**
     * 配置验证码发送限流 Bean。
     * <p>
     * 用于控制验证码发送频率，防止恶意刷接口。
     * 根据 Redis 是否可用选择不同的存储实现：
     * <ul>
     *   <li>Redis 可用：使用 {@link RedisVerificationSendThrottleStore}</li>
     *   <li>Redis 不可用：使用 {@link InMemoryVerificationSendThrottleStore}</li>
     * </ul>
     *
     * @param redisTemplateProvider Redis 模板提供者，延迟获取
     * @return VerificationSendThrottleStore 实现实例
     */
    @Bean
    public VerificationSendThrottleStore verificationSendThrottleStore(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        // 尝试获取 Redis 模板实例
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            // Redis 可用，使用 Redis 存储（支持分布式限流）
            return new RedisVerificationSendThrottleStore(redis);
        }
        // Redis 不可用，回退到内存存储（单机限流）
        return new InMemoryVerificationSendThrottleStore();
    }
}