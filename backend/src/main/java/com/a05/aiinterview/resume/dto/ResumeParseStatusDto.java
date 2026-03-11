package com.a05.aiinterview.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询解析状态接口返回体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "简历解析状态")
public class ResumeParseStatusDto {

    @Schema(description = "简历ID", example = "9001")
    private Long resumeId;
    @Schema(description = "解析状态：parsing/parsed/failed", example = "parsed")
    private String parseStatus;
    @Schema(description = "解析文本预览（前一段），仅 parsed 时可选返回", example = "3年 Java 后端开发经验...")
    private String parsedTextPreview;
}
