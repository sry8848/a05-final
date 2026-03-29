package com.a05.aiinterview.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 通用雷达维度分数 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "雷达维度分数")
public class RadarDimensionScoreDto {

    @Schema(description = "维度编码", example = "fundamentals")
    private String dimensionKey;

    @Schema(description = "维度名称", example = "基础原理掌握")
    private String dimensionName;

    @Schema(description = "维度分数", example = "82.0")
    private BigDecimal score;
}
