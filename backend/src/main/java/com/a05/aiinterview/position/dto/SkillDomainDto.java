package com.a05.aiinterview.position.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识域信息 DTO，用于 GET /positions/{positionCode}/skill-domains 接口的返回值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识域信息")
public class SkillDomainDto {

    @Schema(description = "知识域主键 ID", example = "1")
    private Long domainId;

    @Schema(description = "知识域编码", example = "java_core")
    private String domainCode;

    @Schema(description = "知识域中文名", example = "Java 核心基础")
    private String domainName;

    @Schema(description = "知识域说明", example = "Java 语法、泛型、集合框架、IO")
    private String description;
}
