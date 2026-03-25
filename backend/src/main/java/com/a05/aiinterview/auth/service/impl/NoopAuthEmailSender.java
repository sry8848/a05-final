package com.a05.aiinterview.auth.service.impl;

import com.a05.aiinterview.auth.service.AuthEmailSender;

public class NoopAuthEmailSender implements AuthEmailSender {

    @Override
    public void sendEmailCode(String scene, String email, String code, int expireSeconds) {
        // 测试环境不依赖真实 SMTP。
    }
}
