package com.a05.aiinterview.position.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 岗位信息 DTO，用于 GET /positions 接口的返回值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "岗位信息")
public class PositionDto {

    @Schema(description = "岗位编码，与 targetRole 枚举一致", example = "JAVA_BACKEND")
    private String positionCode;

    @Schema(description = "岗位中文名", example = "Java 后端开发")
    private String positionName;
}
