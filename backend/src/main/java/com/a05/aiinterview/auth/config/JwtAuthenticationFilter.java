package com.a05.aiinterview.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            var claims = jwtUtil.parseToken(token);
            if (claims != null) {
                Long userId = jwtUtil.getUserIdFromClaims(claims);
                if (userId != null) {
                    var auth = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * SSE/异步请求会触发 Async Dispatch。
     * 若此处返回 true（默认行为），异步分发阶段不会重新建立认证上下文，
     * 可能被后续授权过滤器判定为匿名请求并抛 AccessDenied。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 错误分发阶段同样保留鉴权上下文，避免 "response already committed" 场景下再次出现匿名访问判定。
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
