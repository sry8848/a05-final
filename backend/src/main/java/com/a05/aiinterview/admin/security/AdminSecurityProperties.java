package com.a05.aiinterview.admin.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "admin.security")
public class AdminSecurityProperties {

    private List<Account> accounts = new ArrayList<>();

    @Data
    public static class Account {
        private String username;
        private String passwordHash;
        private String displayName;
    }
}
