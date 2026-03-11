package com.a05.aiinterview.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待入库的知识文档 DTO。
 *
 * <p>每份文档经 KnowledgeIngestionService 切片后，携带 metadata 写入 Qdrant。
 * metadata 字段是后续检索过滤的关键依据，必须准确标注。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识文档入库请求体")
public class KnowledgeDocument {

    @NotBlank
    @Schema(description = "文档正文内容", example = "Java 内存模型（JMM）规定了主内存与工作内存的交互规则...")
    private String content;

    @NotBlank
    @Schema(description = "知识类型：job_knowledge（岗位知识域说明）/ interview_question（面试题模板）",
            example = "job_knowledge")
    private String knowledgeType;

    @NotBlank
    @Schema(description = "所属知识域编码，与 position_skill_domains.domain_code 对应", example = "java_memory_model")
    private String domainCode;

    @Schema(description = "所属岗位编码，如 backend_java", example = "backend_java")
    private String positionCode;

    @Schema(description = "知识难度等级：L1~L5", example = "L3")
    private String difficulty;

    @Schema(description = "来源标识，如 official_doc / team_internal", example = "official_doc")
    private String source;

    @Schema(description = "适用题型（可为 null 表示通用），如 PRINCIPLE / SCENARIO", example = "PRINCIPLE")
    private String questionType;

    @Schema(description = "文档版本号，用于更新时追踪", example = "v1")
    private String version;
}
