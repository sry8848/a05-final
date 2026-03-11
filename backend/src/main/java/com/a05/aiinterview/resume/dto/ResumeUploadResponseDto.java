package com.a05.aiinterview.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传简历接口返回体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上传简历返回")
public class ResumeUploadResponseDto {

    @Schema(description = "简历ID", example = "9001")
    private Long resumeId;
    @Schema(description = "解析状态", example = "parsing")
    private String parseStatus;
}
