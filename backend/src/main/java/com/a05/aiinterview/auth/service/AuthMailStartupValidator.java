package com.a05.aiinterview.auth.service;

import com.a05.aiinterview.auth.config.AuthMailProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class AuthMailStartupValidator implements SmartInitializingSingleton {

    private final AuthEmailSender authEmailSender;
    private final AuthMailProperties authMailProperties;

    public AuthMailStartupValidator(AuthEmailSender authEmailSender, AuthMailProperties authMailProperties) {
        this.authEmailSender = authEmailSender;
        this.authMailProperties = authMailProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!authMailProperties.isStartupCheckEnabled()) {
            return;
        }
        authEmailSender.verifyStartup();
    }
}
