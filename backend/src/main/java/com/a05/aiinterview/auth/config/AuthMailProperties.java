package com.a05.aiinterview.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证邮件配置属性类。
 * <p>
 * 用于绑定配置文件中的 {@code auth.mail.*} 配置项。
 * 提供认证邮件发送所需的配置参数。
 *
 * <p>
 * 对应配置文件中的示例：
 * <pre>
 * auth:
 *   mail:
 *     from: noreply@aiinterview.com
 *     startup-check-enabled: true
 * </pre>
 *
 * @author AI Interview Backend Team
 * @see ConfigurationProperties
 * @see org.springframework.boot.context.properties.EnableConfigurationProperties
 */
@Data
@ConfigurationProperties(prefix = "auth.mail")
public class AuthMailProperties {

    /**
     * 邮件发送者地址（From 地址）。
     * <p>
     * 邮件发送时显示的发件人邮箱地址。
     */
    private String from;

    /**
     * 是否在应用启动时检查邮件配置。
     * <p>
     * 默认为 true，表示启动时验证 SMTP 连接是否正常。
     * 在测试环境或无邮件服务器场景下可关闭。
     */
    private boolean startupCheckEnabled = true;
}