package com.a05.aiinterview.admin.security;

import com.a05.aiinterview.auth.config.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil = new JwtUtil("ai_interview_test_secret_key_at_least_32_bytes", 60_000L);

    @Test
    void loginShouldAcceptConfiguredAdminAccountAndReturnToken() throws Exception {
        Object service = createServiceWithSingleAccount("super-admin", "admin123", "超级管理员");

        Method login = service.getClass().getMethod("login", String.class, String.class);
        Object result = login.invoke(service, "super-admin", "admin123");

        assertNotNull(result);
        Method getUsername = result.getClass().getMethod("getUsername");
        Method getDisplayName = result.getClass().getMethod("getDisplayName");
        Method getToken = result.getClass().getMethod("getToken");
        assertEquals("super-admin", getUsername.invoke(result));
        assertEquals("超级管理员", getDisplayName.invoke(result));
        assertNotNull(getToken.invoke(result));
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        Object service = createServiceWithSingleAccount("super-admin", "admin123", "超级管理员");

        Method login = service.getClass().getMethod("login", String.class, String.class);

        assertThrows(Exception.class, () -> login.invoke(service, "super-admin", "bad-password"));
    }

    private Object createServiceWithSingleAccount(String username, String rawPassword, String displayName) throws Exception {
        Class<?> propertiesClass = Class.forName("com.a05.aiinterview.admin.security.AdminSecurityProperties");
        Object properties = propertiesClass.getConstructor().newInstance();

        Class<?> accountClass = Class.forName("com.a05.aiinterview.admin.security.AdminSecurityProperties$Account");
        Object account = accountClass.getConstructor().newInstance();
        accountClass.getMethod("setUsername", String.class).invoke(account, username);
        accountClass.getMethod("setPasswordHash", String.class).invoke(account, passwordEncoder.encode(rawPassword));
        accountClass.getMethod("setDisplayName", String.class).invoke(account, displayName);

        propertiesClass.getMethod("setAccounts", List.class).invoke(properties, List.of(account));

        Class<?> serviceClass = Class.forName("com.a05.aiinterview.admin.security.AdminAuthService");
        return serviceClass
                .getConstructor(propertiesClass, PasswordEncoder.class, JwtUtil.class)
                .newInstance(properties, passwordEncoder, jwtUtil);
    }
}
