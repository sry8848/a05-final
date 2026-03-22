package com.a05.aiinterview.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(classes = AdminRouteSecurityTest.TestContext.class)
@WebAppConfiguration
class AdminRouteSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void unauthenticatedRequestShouldBeRejectedFromAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/test").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenShouldBeForbiddenFromAdminRoute() throws Exception {
        String userToken = jwtUtil.generateToken(7L, "user@example.com");

        mockMvc.perform(get("/admin/test")
                        .header("Authorization", "Bearer " + userToken)
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenShouldReachAdminRoute() throws Exception {
        Method generateAdminToken = JwtUtil.class.getMethod("generateAdminToken", String.class, String.class);
        String adminToken = (String) generateAdminToken.invoke(jwtUtil, "super-admin", "超级管理员");

        mockMvc.perform(get("/admin/test")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @RestController
    @RequestMapping("/admin/test")
    static class AdminProbeController {
        @GetMapping
        String probe(Authentication authentication) {
            return authentication == null ? "anonymous" : "admin-ok";
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestContext {
        @Bean
        AdminProbeController adminProbeController() {
            return new AdminProbeController();
        }

        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil("ai_interview_test_secret_key_at_least_32_bytes", 60_000L);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
            return new JwtAuthenticationFilter(jwtUtil);
        }
    }
}
