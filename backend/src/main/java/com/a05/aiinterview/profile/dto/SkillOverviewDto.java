package com.a05.aiinterview.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "知识域成长概览")
public class SkillOverviewDto {

    @Schema(description = "岗位编码", example = "JAVA_BACKEND")
    private String positionCode;

    @Schema(description = "知识域条目")
    private List<SkillDomainItemDto> domains;
}
