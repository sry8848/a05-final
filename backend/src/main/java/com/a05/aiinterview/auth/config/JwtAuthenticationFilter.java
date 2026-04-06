package com.a05.aiinterview.auth.config;

import com.a05.aiinterview.admin.security.AdminPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 * <p>
 * 作为 Spring Security 过滤器链的一部分，负责从 HTTP 请求头中提取 JWT Token，
 * 解析并验证 Token 的有效性，然后根据 Token 中的角色信息构建认证对象并存入 SecurityContext。
 *
 * <p>
 * 过滤器执行流程：
 * <ol>
 *   <li>从请求头 Authorization 字段中提取 Bearer Token</li>
 *   <li>调用 JwtUtil 解析 Token 获取 Claims</li>
 *   <li>根据 Claims 中的 actorType 判断是管理员还是普通用户</li>
 *   <li>构建相应的 Authentication 对象并存入 SecurityContextHolder</li>
 *   <li>继续执行后续过滤器</li>
 * </ol>
 *
 * <p>
 * <b>重要设计点：</b>
 * <ul>
 *   <li>重写了 {@link #shouldNotFilterAsyncDispatch()} 和 {@link #shouldNotFilterErrorDispatch()}，
 *       返回 false 以确保异步请求（SSE）和错误分发时保留认证上下文</li>
 *   <li>Token 验证失败不会阻止请求继续执行，请求会继续以匿名身份流转，
 *       由后续的权限控制规则决定是否允许访问</li>
 * </ul>
 *
 * @author AI Interview Backend Team
 * @see JwtUtil
 * @see SecurityContextHolder
 * @see OncePerRequestFilter
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JWT 工具类，负责 Token 的生成、解析和验证。
     */
    private final JwtUtil jwtUtil;

    /**
     * 构造函数，注入 JWT 工具类。
     *
     * @param jwtUtil JWT 工具类实例
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 过滤器的核心处理方法。
     * <p>
     * 对每个请求执行以下逻辑：
     * <ul>
     *   <li>检查请求是否包含有效的 Authorization 头（Bearer Token 格式）</li>
     *   <li>解析并验证 Token</li>
     *   <li>根据 Token 中的角色类型创建对应的认证对象</li>
     *   <li>将认证对象设置到 SecurityContext 中</li>
     * </ul>
     *
     * @param request     HTTP 请求对象
     * @param response    HTTP 响应对象
     * @param filterChain 过滤器链，用于将请求传递给下一个过滤器
     * @throws IOException      处理请求/响应时可能发生的 IO 异常
     * @throws ServletException 处理请求时可能发生的 Servlet 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 从请求头中获取 Authorization 字段
        String authHeader = request.getHeader("Authorization");
        // 检查是否存在且为 Bearer Token 格式
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // 提取 Token 主体部分（去掉 "Bearer " 前缀）
            String token = authHeader.substring(7);
            // 解析并验证 Token
            var claims = jwtUtil.parseToken(token);
            if (claims != null) {
                // 从 Claims 中提取角色类型
                String actorType = jwtUtil.getActorTypeFromClaims(claims);
                if (JwtUtil.ACTOR_TYPE_ADMIN.equals(actorType)) {
                    // ==================== 管理员认证 ====================
                    String username = jwtUtil.getUsernameFromClaims(claims);
                    String displayName = jwtUtil.getDisplayNameFromClaims(claims);
                    if (StringUtils.hasText(username)) {
                        // 创建管理员认证令牌
                        var auth = new UsernamePasswordAuthenticationToken(
                                new AdminPrincipal(username, displayName),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                        // 将认证信息存入 SecurityContext，供后续授权决策使用
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } else {
                    // ==================== 普通用户认证 ====================
                    Long userId = jwtUtil.getUserIdFromClaims(claims);
                    if (userId != null) {
                        // 创建用户认证令牌，Principal 为用户ID
                        var auth = new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        // 继续执行过滤器链，即使认证失败也不阻止（会以匿名身份继续）
        filterChain.doFilter(request, response);
    }

    /**
     * 配置异步分发时是否跳过此过滤器。
     * <p>
     * 返回 false 表示在异步请求（如 SSE、异步Controller）分发时会重新执行此过滤器，
     * 确保异步线程也能获取到认证上下文。
     *
     * <p>
     * <b>问题背景：</b>
     * SSE/异步请求会触发 Async Dispatch。若此处返回 true（默认行为），
     * 异步分发阶段不会重新建立认证上下文，可能被后续授权过滤器判定为匿名请求并抛 AccessDenied。
     *
     * @return 始终返回 false，确保异步分发时也执行认证
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 配置错误分发时是否跳过此过滤器。
     * <p>
     * 返回 false 表示在错误分发（如异常处理）时会保留认证上下文，
     * 避免 "response already committed" 场景下再次出现匿名访问判定。
     *
     * @return 始终返回 false，确保错误分发时也保留认证上下文
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}