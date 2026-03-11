package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 考纲摘要 DTO，用于加载页展示"本次面试将考察哪些知识域"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "考纲摘要")
public class SyllabusSummaryDto {

    @Schema(description = "本场计划考察的知识域名称列表", example = "[\"Java 核心基础\", \"并发编程\", \"MySQL 数据库\"]")
    private List<String> plannedDomains;
}
