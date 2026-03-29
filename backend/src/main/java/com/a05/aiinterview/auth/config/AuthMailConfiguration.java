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

@Configuration
@EnableConfigurationProperties(AuthMailProperties.class)
public class AuthMailConfiguration {

    @Bean
    @Profile("!test")
    public MailConnectionTester mailConnectionTester() {
        return sender -> sender.testConnection();
    }

    @Bean
    @Profile("!test")
    public AuthEmailSender authEmailSender(
            MailProperties mailProperties,
            AuthMailProperties authMailProperties,
            MailConnectionTester mailConnectionTester) {
        return new SmtpAuthEmailSender(mailProperties, authMailProperties, mailConnectionTester);
    }

    @Bean
    @Profile("!test")
    public AuthMailStartupValidator authMailStartupValidator(
            AuthEmailSender authEmailSender,
            AuthMailProperties authMailProperties) {
        return new AuthMailStartupValidator(authEmailSender, authMailProperties);
    }

    @Bean
    @Profile("test")
    public AuthEmailSender noopAuthEmailSender() {
        return new NoopAuthEmailSender();
    }
}
