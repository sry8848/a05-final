package com.a05.aiinterview.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新简历请求体（名称、识别文本、是否默认）。
 */
@Data
@Schema(description = "更新简历请求")
public class ResumeUpdateRequestDto {

    @Schema(description = "简历名称", example = "张三_Java开发_优化版")
    private String name;
    @Schema(description = "识别文本（编辑后保存，作为面试上下文使用）", example = "3年 Java 后端开发经验...")
    private String parsedText;
    @Schema(description = "是否设为默认简历", example = "true")
    private Boolean isDefault;
}
