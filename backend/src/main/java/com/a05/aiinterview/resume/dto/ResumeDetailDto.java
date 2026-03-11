package com.a05.aiinterview.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 简历详情，用于 GET /resumes/{id} 返回。
 */
@Data
@Schema(description = "简历详情")
public class ResumeDetailDto {

    @Schema(description = "简历ID", example = "9001")
    private Long id;
    @Schema(description = "简历名称", example = "张三_Java开发.pdf")
    private String name;
    @Schema(description = "来源类型", example = "file")
    private String sourceType;
    @Schema(description = "解析状态", example = "parsed")
    private String parseStatus;
    @Schema(description = "识别文本（可编辑后保存）", example = "3年 Java 后端开发经验...")
    private String parsedText;
    @Schema(description = "是否默认", example = "true")
    private Boolean isDefault;
    @Schema(description = "创建时间 ISO-8601", example = "2026-03-07T09:00:00Z")
    private String createdAt;
}
