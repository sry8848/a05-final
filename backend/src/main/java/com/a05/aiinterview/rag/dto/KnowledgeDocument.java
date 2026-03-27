package com.a05.aiinterview.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 单库题目卡片 DTO。
 *
 * <p>每条记录同时承载题面、考察点、参考语境和评分锚点，
 * 入库时统一拼接为 {@code retrieval_text} 写入向量库正文，
 * 业务字段则保留在 metadata 中供过滤、重排和追问扩展使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题目卡片入库请求体")
public class KnowledgeDocument {

    @NotBlank
    @Schema(description = "题目唯一标识", example = "redis-cache-penetration-001")
    private String id;

    @NotBlank
    @Schema(description = "题目原文", example = "讲一下 Redis 缓存穿透")
    private String questionText;

    @NotBlank
    @Schema(description = "核心考察点", example = "考察空值缓存、布隆过滤器和数据库保护方案")
    private String intentConcept;

    @NotBlank
    @Schema(description = "正确处理逻辑和业务上下文", example = "高并发查询不存在数据时，缓存层需要做兜底，避免数据库被持续打穿。")
    private String referenceContext;

    @Builder.Default
    @Schema(description = "关键得分点")
    private List<String> scoringKeyPoints = new ArrayList<>();

    @Builder.Default
    @Schema(description = "常见错误或负面信号")
    private List<String> scoringPitfalls = new ArrayList<>();

    @Builder.Default
    @Schema(description = "推荐追问题目 ID 列表")
    private List<String> followUpIds = new ArrayList<>();

    @NotBlank
    @Schema(description = "所属领域", example = "redis")
    private String domain;

    @NotBlank
    @Schema(description = "题型", example = "principle")
    private String questionType;

    @NotBlank
    @Schema(description = "题目深度", example = "L2")
    private String difficulty;

    @Builder.Default
    @Schema(description = "关键词列表")
    private List<String> keywords = new ArrayList<>();

    @NotBlank
    @Schema(description = "来源", example = "manual_curated")
    private String source;

    @Schema(description = "是否启用", example = "true")
    private boolean active;

    @NotBlank
    @Schema(description = "版本号", example = "v1")
    private String version;

    /**
     * 统一拼接向量化主文本，避免各处各自拼接导致语义漂移。
     */
    public String toRetrievalText() {
        return Stream.of(
                        questionText,
                        intentConcept,
                        referenceContext,
                        joinList(scoringKeyPoints),
                        joinList(keywords)
                )
                .filter(this::hasText)
                .collect(Collectors.joining("\n"));
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(this::hasText)
                .collect(Collectors.joining("\n"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
