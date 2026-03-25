package com.a05.aiinterview.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth.mail")
public class AuthMailProperties {

    private String from;
    private boolean startupCheckEnabled = true;
}
