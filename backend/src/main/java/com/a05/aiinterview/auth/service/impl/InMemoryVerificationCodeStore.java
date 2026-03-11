package com.a05.aiinterview.auth.service.impl;

import com.a05.aiinterview.auth.service.VerificationCodeStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无 Redis 时使用内存存储验证码（仅单机、重启即失效）。
 * 由 {@link com.a05.aiinterview.auth.config.VerificationCodeStoreConfig} 统一创建 Bean，本类不再使用 @Component。
 */
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private static final int DEFAULT_EXPIRE_SECONDS = 300;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String scene, String target, String code, int expireSeconds) {
        int sec = expireSeconds <= 0 ? DEFAULT_EXPIRE_SECONDS : expireSeconds;
        String key = key(scene, target);
        store.put(key, new Entry(code, System.currentTimeMillis() + sec * 1000L));
    }

    @Override
    public boolean verifyAndInvalidate(String scene, String target, String code) {
        String key = key(scene, target);
        Entry e = store.get(key);
        if (e == null || e.expiresAt < System.currentTimeMillis()) {
            store.remove(key);
            return false;
        }
        if (!e.code.equals(code)) {
            return false;
        }
        store.remove(key);
        return true;
    }

    private static String key(String scene, String target) {
        return scene + ":" + target.trim().toLowerCase();
    }

    private record Entry(String code, long expiresAt) {}
}
