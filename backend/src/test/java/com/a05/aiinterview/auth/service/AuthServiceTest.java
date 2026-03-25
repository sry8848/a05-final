package com.a05.aiinterview.auth.service;

import com.a05.aiinterview.auth.config.JwtUtil;
import com.a05.aiinterview.auth.dto.LoginEmailCodeRequest;
import com.a05.aiinterview.auth.dto.RegisterRequest;
import com.a05.aiinterview.auth.dto.SendEmailCodeRequest;
import com.a05.aiinterview.auth.dto.SendEmailCodeResponse;
import com.a05.aiinterview.auth.entity.User;
import com.a05.aiinterview.auth.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private VerificationCodeStore verificationCodeStore;

    @Mock
    private AuthEmailSender authEmailSender;

    @Mock
    private VerificationSendThrottleStore verificationSendThrottleStore;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<String> codeCaptor;

    @Test
    void sendEmailCodeShouldSendRealEmailAndPersistCodeAfterSuccess() {
        SendEmailCodeRequest request = new SendEmailCodeRequest();
        request.setEmail("User@Example.com");
        request.setScene("login");

        when(verificationSendThrottleStore.tryAcquire(anyString(), anyString(), anyInt())).thenReturn(true);

        SendEmailCodeResponse response = authService.sendEmailCode(request);

        verify(authEmailSender).sendEmailCode(eq("login"), eq("user@example.com"), codeCaptor.capture(), eq(300));
        verify(verificationCodeStore).save(eq("login"), eq("user@example.com"), eq(codeCaptor.getValue()), eq(300));
        assertEquals(6, codeCaptor.getValue().length());
        assertNull(response.getDevCode());
    }

    @Test
    void sendEmailCodeShouldReleaseThrottleWhenMailDeliveryFails() {
        SendEmailCodeRequest request = new SendEmailCodeRequest();
        request.setEmail("user@example.com");
        request.setScene("login");

        when(verificationSendThrottleStore.tryAcquire(anyString(), anyString(), anyInt())).thenReturn(true);
        doThrow(new AuthMailDeliveryException("验证码发送失败，请稍后重试"))
                .when(authEmailSender)
                .sendEmailCode(anyString(), anyString(), anyString(), anyInt());

        AuthMailDeliveryException error = assertThrows(AuthMailDeliveryException.class,
                () -> authService.sendEmailCode(request));

        assertEquals("验证码发送失败，请稍后重试", error.getMessage());
        verify(verificationSendThrottleStore).release("login", "user@example.com");
        verify(verificationCodeStore, never()).save(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void sendEmailCodeShouldRejectRepeatedRequestDuringCooldown() {
        SendEmailCodeRequest request = new SendEmailCodeRequest();
        request.setEmail("user@example.com");
        request.setScene("register");

        when(verificationSendThrottleStore.tryAcquire(anyString(), anyString(), anyInt())).thenReturn(false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> authService.sendEmailCode(request));

        assertEquals("请求过于频繁，请 60 秒后再试", error.getMessage());
        verifyNoInteractions(authEmailSender);
        verify(verificationCodeStore, never()).save(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void registerShouldMarkEmailVerifiedAfterSuccessfulCodeVerification() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("User@Example.com");
        request.setCode("123456");
        request.setNickname("新人");
        request.setPassword("secret123");

        when(verificationCodeStore.verifyAndInvalidate("register", "user@example.com", "123456")).thenReturn(true);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");

        authService.register(request);

        verify(userMapper).insert(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals("user@example.com", created.getEmail());
        assertEquals("新人", created.getNickname());
        assertEquals("hashed-password", created.getPasswordHash());
        assertTrue(created.getEmailVerified());
        assertFalse(created.getPhoneVerified());
        assertEquals("active", created.getStatus());
    }

    @Test
    void loginByEmailCodeShouldBackfillEmailVerifiedForExistingUser() {
        LoginEmailCodeRequest request = new LoginEmailCodeRequest();
        request.setEmail("user@example.com");
        request.setCode("123456");

        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setNickname("面试者");
        user.setStatus("active");
        user.setEmailVerified(false);

        when(verificationCodeStore.verifyAndInvalidate("login", "user@example.com", "123456")).thenReturn(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtUtil.generateToken(7L, "user@example.com")).thenReturn("jwt-token");

        authService.loginByEmailCode(request);

        verify(userMapper).updateById(userCaptor.capture());
        User updated = userCaptor.getValue();
        assertEquals(7L, updated.getId());
        assertTrue(updated.getEmailVerified());
    }
}
