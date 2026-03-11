package com.a05.aiinterview.auth.config;

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // 鉴权接口：注册、登录、验证码发送无需登录
                        .requestMatchers("/auth/register").permitAll()
                        .requestMatchers("/auth/login/**").permitAll()
                        .requestMatchers("/auth/email-code/send").permitAll()
                        .requestMatchers("/auth/me", "/auth/logout").authenticated()
                        // 简历接口：需要登录
                        .requestMatchers("/resumes/**").authenticated()
                        // 岗位与知识域：公开数据，无需登录
                        .requestMatchers("/positions/**").permitAll()
                        // 系统探针：无需登录
                        .requestMatchers("/system/ping").permitAll()
                        // ASR 停顿阈值配置：公开静态数据，无需登录
                        .requestMatchers("/config/asr-pause-thresholds").permitAll()
                        // ASR 凭证接口：需要登录（避免 API Key 被匿名获取）
                        .requestMatchers("/asr/token").authenticated()
                        // 面试会话接口：需要登录
                        .requestMatchers("/interviews/**").authenticated()
                        // 面试偏好接口：需要登录
                        .requestMatchers("/interview-preferences/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            res.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null,\"traceId\":null}");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
