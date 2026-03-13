package com.a05.aiinterview.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "知识域聚合项")
public class SkillDomainItemDto {

    @Schema(description = "知识域编码", example = "java_concurrency")
    private String domainCode;

    @Schema(description = "知识域名称", example = "Java 并发")
    private String domainName;

    @Schema(description = "平均分")
    private BigDecimal averageScore;

    @Schema(description = "样本数", example = "5")
    private long sampleCount;

    @Schema(description = "最近考察时间", example = "2026-03-12T10:00:00")
    private String lastTestedAt;
}
