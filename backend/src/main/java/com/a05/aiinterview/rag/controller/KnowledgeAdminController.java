package com.a05.aiinterview.rag.controller;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理 Controller（内部接口，需在网关层限制访问）。
 *
 * <p>仅当 {@code rag.enabled=true} 时注册该路由，避免在未接入 RAG 环境中暴露无效接口。
 */
@Slf4j
@Tag(name = "知识库管理（内部接口）")
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class KnowledgeAdminController {

    private final KnowledgeIngestionService knowledgeIngestionService;

    /**
     * 批量将知识文档切片并写入 Qdrant 向量库。
     *
     * <p>支持岗位知识库（job_knowledge）和面试题知识库（interview_question）两类文档。
     * 每次调用为追加写入，不会清空已有数据。
     *
     * @param request 包含多份知识文档的请求体
     * @return 实际写入的文档片段数量
     */
    @Operation(summary = "批量入库知识文档（切片+向量化+写 Qdrant）")
    @PostMapping("/ingest")
    public ApiResponse<Map<String, Object>> ingest(@RequestBody @Valid IngestRequest request) {
        log.info("知识入库接口被调用, 文档数={}", request.getDocuments().size());
        int count = knowledgeIngestionService.ingest(request.getDocuments());
        log.info("知识入库接口完成, 写入切片数={}", count);
        return ApiResponse.ok(Map.of("ingestedChunks", count));
    }

    /**
     * 批量入库请求体。
     */
    @Data
    @Schema(description = "知识文档批量入库请求")
    public static class IngestRequest {
        @NotEmpty
        @Schema(description = "待入库的知识文档列表，每份文档可包含多个段落")
        private List<KnowledgeDocument> documents;
    }
}
