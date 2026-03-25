package com.a05.aiinterview.auth.service.impl;

import com.a05.aiinterview.auth.service.VerificationSendThrottleStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVerificationSendThrottleStore implements VerificationSendThrottleStore {

    private static final int DEFAULT_COOLDOWN_SECONDS = 60;

    private final Map<String, Long> store = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean tryAcquire(String scene, String target, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        String key = key(scene, target);
        Long expiresAt = store.get(key);
        if (expiresAt != null && expiresAt > now) {
            return false;
        }
        int ttlSeconds = cooldownSeconds > 0 ? cooldownSeconds : DEFAULT_COOLDOWN_SECONDS;
        store.put(key, now + ttlSeconds * 1000L);
        return true;
    }

    @Override
    public void release(String scene, String target) {
        store.remove(key(scene, target));
    }

    private static String key(String scene, String target) {
        return scene + ":" + target.trim().toLowerCase();
    }
}
