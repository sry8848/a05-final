package com.a05.aiinterview.auth.config;

import com.a05.aiinterview.auth.service.AuthEmailSender;
import com.a05.aiinterview.auth.service.AuthMailStartupValidator;
import com.a05.aiinterview.auth.service.MailConnectionTester;
import com.a05.aiinterview.auth.service.impl.NoopAuthEmailSender;
import com.a05.aiinterview.auth.service.impl.SmtpAuthEmailSender;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 邮件认证配置类。
 * <p>
 * 负责配置邮件发送相关的 Bean，包括：
 * <ul>
 *   <li>邮件连接测试器（MailConnectionTester）</li>
 *   <li>邮件发送器（AuthEmailSender）</li>
 *   <li>邮件启动验证器（AuthMailStartupValidator）</li>
 * </ul>
 *
 * <p>
 * <b>Profile 策略：</b>
 * <ul>
 *   <li>非 test 环境：使用真实的 SMTP 邮件发送实现</li>
 *   <li>test 环境：使用 Noop（空操作）实现，避免在测试时真实发送邮件</li>
 * </ul>
 *
 * @author AI Interview Backend Team
 * @see AuthEmailSender
 * @see MailConnectionTester
 * @see AuthMailStartupValidator
 * @see SmtpAuthEmailSender
 * @see NoopAuthEmailSender
 */
@Configuration
@EnableConfigurationProperties(AuthMailProperties.class)
public class AuthMailConfiguration {

    /**
     * 创建邮件连接测试器。
     * <p>
     * 用于验证 SMTP 服务器连接是否正常。
     * 该 Bean 仅在非 test 环境下创建，测试环境使用 Mock 实现。
     *
     * @return MailConnectionTester 实例
     */
    @Bean
    @Profile("!test")
    public MailConnectionTester mailConnectionTester() {
        // 返回一个 Lambda 表达式，调用发送者的 testConnection 方法
        return sender -> sender.testConnection();
    }

    /**
     * 创建邮件发送器。
     * <p>
     * 根据环境选择不同的实现：
     * <ul>
     *   <li>非 test 环境：SmtpAuthEmailSender（真实 SMTP 发送）</li>
     *   <li>test 环境：NoopAuthEmailSender（不发送）</li>
     * </ul>
     *
     * @param mailProperties         Spring Boot 邮件配置属性
     * @param authMailProperties    认证邮件配置属性
     * @param mailConnectionTester   邮件连接测试器
     * @return AuthEmailSender 实例
     */
    @Bean
    @Profile("!test")
    public AuthEmailSender authEmailSender(
            MailProperties mailProperties,
            AuthMailProperties authMailProperties,
            MailConnectionTester mailConnectionTester) {
        // 使用 SmtpAuthEmailSender 实现，通过 SMTP 协议发送真实邮件
        return new SmtpAuthEmailSender(mailProperties, authMailProperties, mailConnectionTester);
    }

    /**
     * 创建邮件启动验证器。
     * <p>
     * 在应用启动时验证邮件配置是否正确（如 SMTP 服务器是否可达）。
     * 该 Bean 仅在非 test 环境下创建。
     *
     * @param authEmailSender    邮件发送器
     * @param authMailProperties 认证邮件配置属性
     * @return AuthMailStartupValidator 实例
     */
    @Bean
    @Profile("!test")
    public AuthMailStartupValidator authMailStartupValidator(
            AuthEmailSender authEmailSender,
            AuthMailProperties authMailProperties) {
        return new AuthMailStartupValidator(authEmailSender, authMailProperties);
    }

    /**
     * 为测试环境提供空实现的邮件发送器。
     * <p>
     * 测试环境不发送真实邮件，避免干扰测试流程。
     *
     * @return NoopAuthEmailSender 实例（空操作实现）
     */
    @Bean
    @Profile("test")
    public AuthEmailSender noopAuthEmailSender() {
        return new NoopAuthEmailSender();
    }
}