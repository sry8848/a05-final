package com.a05.aiinterview.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 简历列表项，用于 GET /resumes 返回。
 */
@Data
@Schema(description = "简历列表项")
public class ResumeListItemDto {

    @Schema(description = "简历ID", example = "9001")
    private Long id;
    @Schema(description = "简历名称", example = "张三_Java开发.pdf")
    private String name;
    @Schema(description = "来源类型", example = "file")
    private String sourceType;
    @Schema(description = "解析状态：parsing/parsed/failed", example = "parsed")
    private String parseStatus;
    @Schema(description = "是否默认简历", example = "true")
    private Boolean isDefault;
    @Schema(description = "创建时间 ISO-8601", example = "2026-03-07T09:00:00Z")
    private String createdAt;
}
