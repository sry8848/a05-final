package com.a05.aiinterview.admin.dashboard;

import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardModelStatusDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardOverviewDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardPromptSummaryDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardTrendsDto;
import com.a05.aiinterview.auth.config.JwtAuthenticationFilter;
import com.a05.aiinterview.auth.config.JwtUtil;
import com.a05.aiinterview.auth.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(classes = AdminDashboardControllerTest.TestContext.class)
@WebAppConfiguration
class AdminDashboardControllerTest {

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
    void anonymousRequestShouldBeRejectedFromOverview() throws Exception {
        mockMvc.perform(get("/admin/dashboard/overview").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenShouldBeForbiddenFromDashboardRoutes() throws Exception {
        String userToken = jwtUtil.generateToken(7L, "user@example.com");

        mockMvc.perform(get("/admin/dashboard/overview")
                        .header("Authorization", "Bearer " + userToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenShouldAccessOverview() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("super-admin", "超级管理员");

        mockMvc.perform(get("/admin/dashboard/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalUsers").value(0))
                .andExpect(jsonPath("$.data.totalTokensToday").value(0));
    }

    @Test
    void adminTokenShouldAccessTrends() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("super-admin", "超级管理员");

        mockMvc.perform(get("/admin/dashboard/trends")
                        .param("days", "7")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dates").isArray())
                .andExpect(jsonPath("$.data.newUsers").isArray())
                .andExpect(jsonPath("$.data.interviews").isArray())
                .andExpect(jsonPath("$.data.totalTokens").isArray());
    }

    @Test
    void adminTokenShouldAccessModels() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("super-admin", "超级管理员");

        mockMvc.perform(get("/admin/dashboard/models")
                        .param("windowMinutes", "15")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void adminTokenShouldAccessPrompts() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("super-admin", "超级管理员");

        mockMvc.perform(get("/admin/dashboard/prompts")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AdminDashboardController.class})
    static class TestContext {
        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil("ai_interview_test_secret_key_at_least_32_bytes", 60_000L);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
            return new JwtAuthenticationFilter(jwtUtil);
        }

        @Bean
        AdminDashboardService adminDashboardService() {
            AdminDashboardService service = mock(AdminDashboardService.class);

            AdminDashboardOverviewDto overviewDto = new AdminDashboardOverviewDto();
            overviewDto.setTotalUsers(0L);
            overviewDto.setNewUsersToday(0L);
            overviewDto.setTotalInterviews(0L);
            overviewDto.setInterviewsToday(0L);
            overviewDto.setActiveInterviews(0L);
            overviewDto.setTotalTokensToday(0L);

            AdminDashboardTrendsDto trendsDto = new AdminDashboardTrendsDto();
            trendsDto.setDates(List.of());
            trendsDto.setNewUsers(List.of());
            trendsDto.setInterviews(List.of());
            trendsDto.setTotalTokens(List.of());

            when(service.getOverview()).thenReturn(overviewDto);
            when(service.getTrends(7)).thenReturn(trendsDto);
            when(service.getModels(15)).thenReturn(List.of());
            when(service.getPrompts()).thenReturn(List.of());
            return service;
        }
    }
}
