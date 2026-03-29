package com.a05.aiinterview.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "知识域聚合项")
public class SkillDomainItemDto {

    @Schema(description = "知识域编码", example = "java_concurrency")
    private String domainCode;

    @Schema(description = "知识域名称", example = "Java 并发")
    private String domainName;

    @Schema(description = "趋势分")
    private BigDecimal score;

    @Schema(description = "相对基准 50 的累计变化值")
    private BigDecimal scoreDelta;

    @Schema(description = "最近 8 场窗口内该域出现次数", example = "5")
    private long appearanceCount;

    @Schema(description = "是否满足红黑榜资格")
    private boolean rankingEligible;

    @Schema(description = "聚合薄弱点描述")
    private String weaknessSummary;

    @Schema(description = "聚合后的薄弱点，最多 3 项")
    private List<String> weaknessPoints;

    @Schema(description = "最近 8 场内该域的得分轨迹")
    private java.util.List<ScoreTrendPointDto> recentScores;

    @Schema(description = "平均分（兼容旧前端）")
    private BigDecimal averageScore;

    @Schema(description = "样本数（兼容旧前端）", example = "5")
    private long sampleCount;

    @Schema(description = "最近考察时间", example = "2026-03-12T10:00:00")
    private String lastTestedAt;
}
