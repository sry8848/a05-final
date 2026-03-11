package com.a05.aiinterview.auth.service;

/**
 * 验证码存储（Redis 或内存），用于发送/校验邮箱或手机验证码。
 */
public interface VerificationCodeStore {

    /**
     * 保存验证码，并设置过期时间（秒）。
     */
    void save(String scene, String target, String code, int expireSeconds);

    /**
     * 校验验证码是否正确，校验成功后应使该验证码失效。
     *
     * @return true 表示验证通过
     */
    boolean verifyAndInvalidate(String scene, String target, String code);
}
