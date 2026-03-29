package com.a05.aiinterview.profile.dto;

import com.a05.aiinterview.common.dto.RadarDimensionScoreDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "成长统计")
public class ProfileStatisticsDto {

    @Schema(description = "累计面试次数", example = "12")
    private long totalSessions;

    @Schema(description = "累计时长（分钟）", example = "420")
    private long totalMinutes;

    @Schema(description = "平均分", example = "77.8")
    private BigDecimal averageScore;

    @Schema(description = "最近 8 场专业模式能力雷达")
    private List<RadarDimensionScoreDto> professionalRadarScores;

    @Schema(description = "参与专业模式雷达均值的样本数", example = "6")
    private long professionalSampleCount;

    @Schema(description = "分数趋势")
    private List<ScoreTrendPointDto> scoreTrend;
}
