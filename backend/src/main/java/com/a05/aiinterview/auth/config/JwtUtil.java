package com.a05.aiinterview.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT（JSON Web Token）工具类。
 * <p>
 * 负责 JWT Token 的生成、解析和 Claims 提取。
 * 采用 HMAC-SHA 算法（HS256）对 Token 进行签名和验签。
 *
 * <p>
 * 主要功能：
 * <ul>
 *   <li>生成用户 JWT Token：包含用户ID、邮箱、角色类型</li>
 *   <li>生成管理员 JWT Token：包含用户名、显示名、角色类型</li>
 *   <li>解析并验证 Token 签名和过期时间</li>
 *   <li>从 Claims 中提取用户身份信息</li>
 * </ul>
 *
 * @author AI Interview Backend Team
 * @see <a href="https://jwt.io/">JWT Official Site</a>
 */
@Component
public class JwtUtil {

    /**
     * JWT Claims 中的角色类型字段名。
     */
    public static final String ACTOR_TYPE_CLAIM = "actorType";

    /**
     * 普通用户角色标识。
     */
    public static final String ACTOR_TYPE_USER = "USER";

    /**
     * 管理员角色标识。
     */
    public static final String ACTOR_TYPE_ADMIN = "ADMIN";

    /**
     * 用于签名和验签的密钥。
     * 由配置文件注入，长度需至少 32 字节（256 bits）以满足 HS256 要求。
     */
    private final SecretKey key;

    /**
     * Token 过期时间，单位：毫秒。
     * 默认值为 7 天（604800000 ms）。
     */
    private final long expirationMs;

    /**
     * 构造函数，初始化 JWT 签名密钥和过期时间。
     *
     * @param secret      JWT 签名密钥，至少 32 字节
     * @param expirationMs Token 过期时间（毫秒）
     */
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:604800000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 生成普通用户的 JWT Token。
     * <p>
     * Token 中包含以下 Claims：
     * <ul>
     *   <li>subject：用户ID</li>
     *   <li>actorType：固定为 "USER"</li>
     *   <li>email：用户邮箱</li>
     *   <li>iat：签发时间</li>
     *   <li>exp：过期时间</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param email  用户邮箱
     * @return 生成的 JWT Token 字符串
     */
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

    /**
     * 生成管理员的 JWT Token。
     * <p>
     * Token 中包含以下 Claims：
     * <ul>
     *   <li>subject：管理员用户名</li>
     *   <li>actorType：固定为 "ADMIN"</li>
     *   <li>username：管理员用户名</li>
     *   <li>displayName：管理员显示名称</li>
     *   <li>iat：签发时间</li>
     *   <li>exp：过期时间</li>
     * </ul>
     *
     * @param username    管理员用户名
     * @param displayName 管理员显示名称
     * @return 生成的 JWT Token 字符串
     */
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
     * 解析并验证 JWT Token。
     * <p>
     * 验证内容包括：
     * <ul>
     *   <li>签名是否正确（防止篡改）</li>
     *   <li>Token 是否已过期</li>
     *   <li>Token 格式是否合法</li>
     * </ul>
     *
     * @param token JWT Token 字符串
     * @return 解析后的 Claims 对象；若验证失败（签名错误、过期等）则返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // 签名验证失败、Token 过期、格式错误等均返回 null
            // 调用方需自行处理 null 情况
            return null;
        }
    }

    /**
     * 从 Claims 中提取用户ID。
     * <p>
     * 仅当 Claims 中的 actorType 为 USER 时才返回有效值。
     *
     * @param claims 已解析的 JWT Claims 对象
     * @return 用户ID；若 actorType 不是 USER 或 subject 为空则返回 null
     */
    public Long getUserIdFromClaims(Claims claims) {
        if (!ACTOR_TYPE_USER.equalsIgnoreCase(getActorTypeFromClaims(claims))) {
            return null;
        }
        String sub = claims.getSubject();
        return sub == null ? null : Long.parseLong(sub);
    }

    /**
     * 从 Claims 中提取角色类型。
     *
     * @param claims 已解析的 JWT Claims 对象
     * @return 角色类型字符串（"USER" 或 "ADMIN"）；若未设置则默认返回 "USER"
     */
    public String getActorTypeFromClaims(Claims claims) {
        Object actorType = claims.get(ACTOR_TYPE_CLAIM);
        if (actorType == null) {
            return ACTOR_TYPE_USER;
        }
        return String.valueOf(actorType).trim().toUpperCase();
    }

    /**
     * 从 Claims 中提取用户名。
     * <p>
     * 优先返回 username claim 的值，若不存在则返回 subject。
     *
     * @param claims 已解析的 JWT Claims 对象
     * @return 用户名字符串
     */
    public String getUsernameFromClaims(Claims claims) {
        Object username = claims.get("username");
        if (username != null) {
            return String.valueOf(username);
        }
        return claims.getSubject();
    }

    /**
     * 从 Claims 中提取管理员显示名称。
     *
     * @param claims 已解析的 JWT Claims 对象
     * @return 显示名称；若未设置则返回 null
     */
    public String getDisplayNameFromClaims(Claims claims) {
        Object displayName = claims.get("displayName");
        return displayName == null ? null : String.valueOf(displayName);
    }
}