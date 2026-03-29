package com.a05.aiinterview.auth.service;

public interface VerificationSendThrottleStore {

    boolean tryAcquire(String scene, String target, int cooldownSeconds);

    void release(String scene, String target);
}
