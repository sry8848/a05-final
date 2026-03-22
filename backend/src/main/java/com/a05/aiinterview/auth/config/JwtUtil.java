package com.a05.aiinterview.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    public static final String ACTOR_TYPE_CLAIM = "actorType";
    public static final String ACTOR_TYPE_USER = "USER";
    public static final String ACTOR_TYPE_ADMIN = "ADMIN";

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:604800000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ACTOR_TYPE_CLAIM, ACTOR_TYPE_USER)
                .claim("email", email)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public String generateAdminToken(String username, String displayName) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim(ACTOR_TYPE_CLAIM, ACTOR_TYPE_ADMIN)
                .claim("username", username)
                .claim("displayName", displayName)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 JWT，失败返回 null（过期或非法）。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserIdFromClaims(Claims claims) {
        if (!ACTOR_TYPE_USER.equalsIgnoreCase(getActorTypeFromClaims(claims))) {
            return null;
        }
        String sub = claims.getSubject();
        return sub == null ? null : Long.parseLong(sub);
    }

    public String getActorTypeFromClaims(Claims claims) {
        Object actorType = claims.get(ACTOR_TYPE_CLAIM);
        if (actorType == null) {
            return ACTOR_TYPE_USER;
        }
        return String.valueOf(actorType).trim().toUpperCase();
    }

    public String getUsernameFromClaims(Claims claims) {
        Object username = claims.get("username");
        if (username != null) {
            return String.valueOf(username);
        }
        return claims.getSubject();
    }

    public String getDisplayNameFromClaims(Claims claims) {
        Object displayName = claims.get("displayName");
        return displayName == null ? null : String.valueOf(displayName);
    }
}
