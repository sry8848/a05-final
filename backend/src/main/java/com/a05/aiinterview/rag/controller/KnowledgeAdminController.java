package com.a05.aiinterview.rag.controller;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportError;
import com.a05.aiinterview.rag.dto.KnowledgeJsonlImportResult;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import com.a05.aiinterview.rag.service.KnowledgeJsonlImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理 Controller（内部接口，需在网关层限制访问）。
 */
@Slf4j
@Tag(name = "知识库管理（内部接口）")
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeAdminController {

    private final ObjectProvider<KnowledgeIngestionService> knowledgeIngestionServiceProvider;
    private final ObjectProvider<KnowledgeJsonlImportService> knowledgeJsonlImportServiceProvider;

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
        KnowledgeIngestionService knowledgeIngestionService = knowledgeIngestionServiceProvider.getIfAvailable();
        if (knowledgeIngestionService == null) {
            return ApiResponse.fail(503, "RAG 未启用，无法导入语料");
        }
        log.info("知识入库接口被调用, 文档数={}", request.getDocuments().size());
        int count = knowledgeIngestionService.ingest(request.getDocuments());
        log.info("知识入库接口完成, 写入切片数={}", count);
        return ApiResponse.ok(Map.of("ingestedChunks", count));
    }

    @Operation(summary = "上传 JSONL 题卡并执行全量校验后入库")
    @PostMapping(value = "/import-jsonl", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeJsonlImportResult> importJsonl(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        KnowledgeJsonlImportService knowledgeJsonlImportService = knowledgeJsonlImportServiceProvider.getIfAvailable();
        if (knowledgeJsonlImportService == null) {
            String fileName = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            KnowledgeJsonlImportResult result = KnowledgeJsonlImportResult.failure(
                    fileName,
                    0,
                    0,
                    List.of(KnowledgeJsonlImportError.file("RAG 未启用，无法导入 JSONL 题卡", "请先开启 rag.enabled 并初始化向量库"))
            );
            return new ApiResponse<>(503, "RAG 未启用，无法导入 JSONL 题卡", result,
                    com.a05.aiinterview.common.TraceContext.getOrCreateTraceId());
        }
        KnowledgeJsonlImportResult result = knowledgeJsonlImportService.importJsonl(file);
        if (result.isSuccess()) {
            return new ApiResponse<>(0, "导入成功", result, com.a05.aiinterview.common.TraceContext.getOrCreateTraceId());
        }
        if (result.isIngestionFailure()) {
            return new ApiResponse<>(500, "知识入库失败", result, com.a05.aiinterview.common.TraceContext.getOrCreateTraceId());
        }
        return new ApiResponse<>(400, "JSONL校验失败", result, com.a05.aiinterview.common.TraceContext.getOrCreateTraceId());
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
