package com.a05.aiinterview.auth.config;

import com.a05.aiinterview.common.TraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 核心配置类。
 * <p>
 * 本类负责配置整个应用的安全策略，包括：
 * <ul>
 *   <li>密码加密策略（BCrypt）</li>
 *   <li>HTTP 请求的权限控制规则</li>
 *   <li>JWT 认证过滤器的注册</li>
 *   <li>认证/授权异常的统一处理</li>
 *   <li>跨域（CORS）配置</li>
 * </ul>
 *
 * @author AI Interview Backend Team
 * @see SecurityFilterChain
 * @see PasswordEncoder
 * @see JwtAuthenticationFilter
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * JWT 认证过滤器，负责从请求头中解析并验证 JWT Token。
     * 将其注入以便在 SecurityFilterChain 中注册。
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 构造函数注入 JwtAuthenticationFilter 依赖。
     *
     * @param jwtAuthenticationFilter JWT 认证过滤器实例
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 配置密码加密器。
     * <p>
     * 使用 BCrypt 算法对用户密码进行哈希存储。
     * BCrypt 是业界推荐的密码哈希方案，自带盐值，可有效防止彩虹表攻击。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置 Spring Security 的安全过滤链。
     * <p>
     * 核心安全策略：
     * <ul>
     *   <li>禁用 CSRF：采用 JWT 无状态认证，无需 CSRF 保护</li>
     *   <li>无状态会话：不创建 HttpSession，所有认证信息由 Token 携带</li>
     *   <li>路由级权限控制：根据 URL 路径匹配角色</li>
     *   <li>统一异常处理：认证失败返回 401，授权失败返回 403</li>
     * </ul>
     *
     * @param http HttpSecurity 配置对象
     * @return 配置完成的安全过滤链
     * @throws Exception 配置过程中的异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // ==================== 公开接口（无需认证）====================
                        // 鉴权接口：注册、登录、验证码发送无需登录
                        .requestMatchers("/auth/register").permitAll()
                        .requestMatchers("/auth/login/**").permitAll()
                        .requestMatchers("/auth/admin/login").permitAll()
                        .requestMatchers("/auth/email-code/send").permitAll()
                        // 简历接口：需要登录（已注释，原为 hasRole("USER")）
                        // 岗位与知识域：公开数据，无需登录
                        .requestMatchers("/positions/**").permitAll()
                        // 系统探针：无需登录
                        .requestMatchers("/system/ping").permitAll()
                        // ASR 停顿阈值配置：公开静态数据，无需登录
                        .requestMatchers("/config/asr-pause-thresholds").permitAll()
                        // ASR 代理 WebSocket：通过 ticket 校验，不走 JWT
                        .requestMatchers("/asr/stream").permitAll()

                        // ==================== 用户接口（需要 USER 角色）====================
                        // 用户相关：获取当前用户信息、登出
                        .requestMatchers("/auth/me", "/auth/logout").hasRole("USER")
                        // 简历接口：需要登录
                        .requestMatchers("/resumes/**").hasRole("USER")
                        // 面试会话接口：需要登录
                        .requestMatchers("/interviews/**").hasRole("USER")
                        // 面试偏好接口：需要登录
                        .requestMatchers("/interview-preferences/**").hasRole("USER")
                        // 用户档案：需要登录
                        .requestMatchers("/profile/**").hasRole("USER")
                        // 问答库：需要登录
                        .requestMatchers("/question-bank/**").hasRole("USER")
                        // ASR 凭证接口：需要登录（避免 API Key 被匿名获取）
                        .requestMatchers("/asr/token").hasRole("USER")

                        // ==================== 管理员接口（需要 ADMIN 角色）====================
                        // 管理员相关：获取管理员信息、登出、管理后台
                        .requestMatchers("/auth/admin/me", "/auth/admin/logout").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // ==================== 默认策略 ====================
                        // 其他未匹配到的请求默认放行（可根据业务需要调整）
                        .anyRequest().permitAll()
                )
                .exceptionHandling(e -> e
                        // 认证失败处理（未登录或 Token 无效）：返回 401
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            String traceId = TraceContext.getOrCreateTraceId();
                            String requestId = TraceContext.getOrCreateRequestId();
                            res.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
                            res.setHeader(TraceContext.REQUEST_ID_HEADER, requestId);
                            res.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null,\"traceId\":\""
                                    + traceId + "\"}");
                        })
                        // 授权失败处理（已登录但无权限）：返回 403
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            String traceId = TraceContext.getOrCreateTraceId();
                            String requestId = TraceContext.getOrCreateRequestId();
                            res.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
                            res.setHeader(TraceContext.REQUEST_ID_HEADER, requestId);
                            res.getWriter().write("{\"code\":403,\"message\":\"无权限访问\",\"data\":null,\"traceId\":\""
                                    + traceId + "\"}");
                        })
                )
                // 将 JWT 认证过滤器注册到用户名密码认证过滤器之前
                // 确保请求在进入 Controller 之前已完成身份认证
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}