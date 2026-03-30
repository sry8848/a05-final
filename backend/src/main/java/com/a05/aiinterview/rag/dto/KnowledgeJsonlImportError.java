package com.a05.aiinterview.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSONL 导入错误详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JSONL 导入错误详情")
public class KnowledgeJsonlImportError {

    @Schema(description = "错误范围：file 或 line", example = "line")
    private String scope;

    @Schema(description = "错误行号；文件级错误为空", example = "18")
    private Integer lineNo;

    @Schema(description = "错误字段；文件级错误为空", example = "difficulty")
    private String field;

    @Schema(description = "给管理员看的错误原因", example = "字段值非法")
    private String reason;

    @Schema(description = "期望值说明", example = "L1-L5")
    private String expected;

    public static KnowledgeJsonlImportError file(String reason, String expected) {
        return KnowledgeJsonlImportError.builder()
                .scope("file")
                .reason(reason)
                .expected(expected)
                .build();
    }

    public static KnowledgeJsonlImportError line(int lineNo, String field, String reason, String expected) {
        return KnowledgeJsonlImportError.builder()
                .scope("line")
                .lineNo(lineNo)
                .field(field)
                .reason(reason)
                .expected(expected)
                .build();
    }
}
