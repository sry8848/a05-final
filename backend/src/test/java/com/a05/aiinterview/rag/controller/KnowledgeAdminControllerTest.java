package com.a05.aiinterview.rag.controller;

import com.a05.aiinterview.auth.config.JwtAuthenticationFilter;
import com.a05.aiinterview.auth.config.JwtUtil;
import com.a05.aiinterview.auth.config.SecurityConfig;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportError;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportResult;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import com.a05.aiinterview.rag.service.KnowledgeJsonlImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(classes = KnowledgeAdminControllerTest.TestContext.class)
@WebAppConfiguration
@TestPropertySource(properties = "rag.enabled=true")
class KnowledgeAdminControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private KnowledgeJsonlImportService knowledgeJsonlImportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void adminTokenShouldImportJsonlAndReturnSuccessContract() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("admin", "管理员");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":\"q1\"}\n".getBytes(StandardCharsets.UTF_8)
        );
        when(knowledgeJsonlImportService.importJsonl(any())).thenReturn(
                KnowledgeJsonlImportResult.success("cards.jsonl", 1, 1, 1)
        );

        mockMvc.perform(multipart("/admin/knowledge/import-jsonl")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("导入成功"))
                .andExpect(jsonPath("$.data.fileName").value("cards.jsonl"))
                .andExpect(jsonPath("$.data.ingestedCount").value(1));
    }

    @Test
    void adminTokenShouldReturnStructuredValidationFailure() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("admin", "管理员");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":\"q1\"}\n".getBytes(StandardCharsets.UTF_8)
        );
        when(knowledgeJsonlImportService.importJsonl(any())).thenReturn(
                KnowledgeJsonlImportResult.failure(
                        "cards.jsonl",
                        2,
                        1,
                        List.of(KnowledgeJsonlImportError.line(2, "difficulty", "字段值非法", "L1-L5"))
                )
        );

        mockMvc.perform(multipart("/admin/knowledge/import-jsonl")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("JSONL校验失败"))
                .andExpect(jsonPath("$.data.totalLines").value(2))
                .andExpect(jsonPath("$.data.errors[0].lineNo").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").value("difficulty"))
                .andExpect(jsonPath("$.data.errors[0].reason").value("字段值非法"))
                .andExpect(jsonPath("$.data.errors[0].expected").value("L1-L5"));
    }

    @Test
    void adminTokenShouldReceiveFieldMissingErrorWhenMultipartFileIsAbsent() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("admin", "管理员");
        when(knowledgeJsonlImportService.importJsonl(any())).thenReturn(
                KnowledgeJsonlImportResult.failure(
                        "",
                        0,
                        0,
                        List.of(KnowledgeJsonlImportError.file("缺少上传文件", "multipart/form-data 字段 file"))
                )
        );

        mockMvc.perform(multipart("/admin/knowledge/import-jsonl")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("JSONL校验失败"))
                .andExpect(jsonPath("$.data.errors[0].reason").value("缺少上传文件"))
                .andExpect(jsonPath("$.data.errors[0].expected").value("multipart/form-data 字段 file"));
    }

    @Test
    void adminTokenShouldReturnDedicatedIngestionFailureMessage() throws Exception {
        String adminToken = jwtUtil.generateAdminToken("admin", "管理员");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.jsonl",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":\"q1\"}\n".getBytes(StandardCharsets.UTF_8)
        );
        when(knowledgeJsonlImportService.importJsonl(any())).thenReturn(
                KnowledgeJsonlImportResult.failure(
                        "cards.jsonl",
                        1,
                        1,
                        List.of(KnowledgeJsonlImportError.file("知识入库失败", null))
                )
        );

        mockMvc.perform(multipart("/admin/knowledge/import-jsonl")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("知识入库失败"))
                .andExpect(jsonPath("$.data.errors[0].reason").value("知识入库失败"));
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, KnowledgeAdminController.class})
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
        KnowledgeIngestionService knowledgeIngestionService() {
            return mock(KnowledgeIngestionService.class);
        }

        @Bean
        KnowledgeJsonlImportService knowledgeJsonlImportService() {
            return mock(KnowledgeJsonlImportService.class);
        }
    }
}
