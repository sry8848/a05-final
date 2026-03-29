package com.a05.aiinterview.auth.service;

public interface AuthEmailSender {

    void sendEmailCode(String scene, String email, String code, int expireSeconds);

    default void verifyStartup() {
        // no-op
    }
}
