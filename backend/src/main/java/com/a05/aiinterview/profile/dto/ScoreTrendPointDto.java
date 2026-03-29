package com.a05.aiinterview.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "成长统计趋势点")
public class ScoreTrendPointDto {

    @Schema(description = "日期（yyyy-MM-dd）", example = "2026-03-01")
    private String date;

    @Schema(description = "分数", example = "82.0")
    private BigDecimal score;
}
