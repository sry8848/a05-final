package com.a05.aiinterview.auth.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtUtilAdminClaimsTest {

    private final JwtUtil jwtUtil = new JwtUtil("ai_interview_test_secret_key_at_least_32_bytes", 60_000L);

    @Test
    void userTokenShouldCarryUserActorType() {
        String token = jwtUtil.generateToken(42L, "user@example.com");

        Claims claims = jwtUtil.parseToken(token);

        assertNotNull(claims);
        assertEquals("USER", claims.get("actorType"));
        assertEquals("user@example.com", claims.get("email"));
    }

    @Test
    void adminTokenShouldCarryAdminIdentityClaims() throws Exception {
        Method generateAdminToken = JwtUtil.class.getMethod("generateAdminToken", String.class, String.class);

        String token = (String) generateAdminToken.invoke(jwtUtil, "super-admin", "超级管理员");
        Claims claims = jwtUtil.parseToken(token);

        assertNotNull(claims);
        assertEquals("ADMIN", claims.get("actorType"));
        assertEquals("super-admin", claims.get("username"));
        assertEquals("超级管理员", claims.get("displayName"));
    }
}
