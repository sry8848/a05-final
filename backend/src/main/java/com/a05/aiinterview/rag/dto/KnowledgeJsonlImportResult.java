package com.a05.aiinterview.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * JSONL 导入结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JSONL 导入结果")
public class KnowledgeJsonlImportResult {

    @Schema(description = "文件名", example = "rag-java-backend.jsonl")
    private String fileName;

    @Schema(description = "总行数", example = "20")
    private int totalLines;

    @Schema(description = "通过校验的行数", example = "17")
    private int validLines;

    @Schema(description = "实际入库条数", example = "17")
    private int ingestedCount;

    @Builder.Default
    @Schema(description = "错误详情列表")
    private List<KnowledgeJsonlImportError> errors = new ArrayList<>();

    public static KnowledgeJsonlImportResult success(String fileName, int totalLines, int validLines, int ingestedCount) {
        return KnowledgeJsonlImportResult.builder()
                .fileName(fileName)
                .totalLines(totalLines)
                .validLines(validLines)
                .ingestedCount(ingestedCount)
                .errors(List.of())
                .build();
    }

    public static KnowledgeJsonlImportResult failure(String fileName,
                                                     int totalLines,
                                                     int validLines,
                                                     List<KnowledgeJsonlImportError> errors) {
        return KnowledgeJsonlImportResult.builder()
                .fileName(fileName)
                .totalLines(totalLines)
                .validLines(validLines)
                .ingestedCount(0)
                .errors(errors == null ? List.of() : List.copyOf(errors))
                .build();
    }

    public boolean isSuccess() {
        return errors == null || errors.isEmpty();
    }

    public boolean isIngestionFailure() {
        return errors != null
                && errors.size() == 1
                && "file".equals(errors.getFirst().getScope())
                && "知识入库失败".equals(errors.getFirst().getReason());
    }
}
