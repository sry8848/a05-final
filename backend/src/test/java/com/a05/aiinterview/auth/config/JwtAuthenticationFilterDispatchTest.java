package com.a05.aiinterview.auth.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterDispatchTest {

    @Test
    void shouldApplyFilterOnAsyncAndErrorDispatch() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtUtil.class));

        assertFalse(filter.shouldNotFilterAsyncDispatch());
        assertFalse(filter.shouldNotFilterErrorDispatch());
    }
}
