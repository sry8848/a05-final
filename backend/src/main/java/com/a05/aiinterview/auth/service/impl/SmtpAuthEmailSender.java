package com.a05.aiinterview.auth.service.impl;

import com.a05.aiinterview.auth.config.AuthMailProperties;
import com.a05.aiinterview.auth.service.AuthEmailSender;
import com.a05.aiinterview.auth.service.AuthMailDeliveryException;
import com.a05.aiinterview.auth.service.MailConnectionTester;
import jakarta.mail.MessagingException;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SmtpAuthEmailSender implements AuthEmailSender {

    private final JavaMailSenderImpl mailSender;
    private final AuthMailProperties authMailProperties;
    private final MailConnectionTester mailConnectionTester;

    public SmtpAuthEmailSender(
            MailProperties mailProperties,
            AuthMailProperties authMailProperties,
            MailConnectionTester mailConnectionTester) {
        this.authMailProperties = authMailProperties;
        this.mailConnectionTester = mailConnectionTester;
        this.mailSender = buildMailSender(mailProperties);
    }

    @Override
    public void sendEmailCode(String scene, String email, String code, int expireSeconds) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(authMailProperties.getFrom());
        message.setTo(email);
        message.setSubject(resolveSubject(scene));
        message.setText(buildBody(code, expireSeconds));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new AuthMailDeliveryException("验证码发送失败，请稍后重试", e);
        }
    }

    @Override
    public void verifyStartup() {
        validateConfig();
        try {
            mailConnectionTester.testConnection(mailSender);
        } catch (MessagingException e) {
            throw new IllegalStateException("SMTP 连接或认证失败，请检查邮箱配置", e);
        }
    }

    private void validateConfig() {
        requireText(mailSender.getHost(), "SMTP_HOST 未配置");
        if (mailSender.getPort() <= 0) {
            throw new IllegalStateException("SMTP_PORT 未配置或无效");
        }
        requireText(mailSender.getUsername(), "SMTP_USERNAME 未配置");
        requireText(mailSender.getPassword(), "SMTP_PASSWORD 未配置");
        requireText(authMailProperties.getFrom(), "SMTP_FROM 未配置");
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static String resolveSubject(String scene) {
        return "register".equals(scene) ? "AI面试官注册验证码" : "AI面试官登录验证码";
    }

    private static String buildBody(String code, int expireSeconds) {
        int minutes = Math.max(1, (int) Math.ceil(expireSeconds / 60.0));
        return """
                你好，

                你的 AI 面试官验证码为：%s
                验证码 %d 分钟内有效，请勿泄露给他人。

                如果这不是你的操作，请直接忽略这封邮件。
                """.formatted(code, minutes);
    }

    private static JavaMailSenderImpl buildMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost());
        if (mailProperties.getPort() != null) {
            sender.setPort(mailProperties.getPort());
        }
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());
        sender.setProtocol(mailProperties.getProtocol());
        if (mailProperties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());
        } else {
            sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        }
        Properties properties = new Properties();
        properties.putAll(mailProperties.getProperties());
        sender.setJavaMailProperties(properties);
        return sender;
    }
}
