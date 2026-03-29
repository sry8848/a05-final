package com.a05.aiinterview.auth.service;

import com.a05.aiinterview.auth.config.AuthMailProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthMailStartupValidatorTest {

    @Test
    void shouldSkipVerificationWhenStartupCheckDisabled() {
        AuthEmailSender authEmailSender = mock(AuthEmailSender.class);
        AuthMailProperties properties = new AuthMailProperties();
        properties.setStartupCheckEnabled(false);

        AuthMailStartupValidator validator = new AuthMailStartupValidator(authEmailSender, properties);

        assertDoesNotThrow(validator::afterSingletonsInstantiated);
        verifyNoInteractions(authEmailSender);
    }

    @Test
    void shouldFailFastWhenSenderVerificationFails() {
        AuthEmailSender authEmailSender = mock(AuthEmailSender.class);
        AuthMailProperties properties = new AuthMailProperties();
        properties.setStartupCheckEnabled(true);
        doThrow(new IllegalStateException("SMTP 认证失败")).when(authEmailSender).verifyStartup();

        AuthMailStartupValidator validator = new AuthMailStartupValidator(authEmailSender, properties);

        IllegalStateException error = assertThrows(IllegalStateException.class, validator::afterSingletonsInstantiated);
        verify(authEmailSender).verifyStartup();
        org.junit.jupiter.api.Assertions.assertEquals("SMTP 认证失败", error.getMessage());
    }
}
