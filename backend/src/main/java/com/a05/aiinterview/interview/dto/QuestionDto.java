package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题目信息 DTO，用于会话详情与流式 done 事件返回权威题目快照。
 */
@Data
@Schema(description = "题目信息")
public class QuestionDto {

    @Schema(description = "题目 ID", example = "5001")
    private Long questionId;

    @Schema(description = "题号（在本场面试中的序号）", example = "1")
    private Integer questionNo;

    @Schema(description = "题目类型枚举值", example = "INTRO")
    private String questionType;

    @Schema(description = "主知识域 code", example = "java_core")
    private String domainCode;

    @Schema(description = "主知识域中文名", example = "Java 核心基础")
    private String domainName;

    @Schema(description = "题目正文", example = "请先做一个简单的自我介绍...")
    private String stem;

    @Schema(description = "核心考察点", example = "项目经验表达")
    private String targetSkill;

    @Schema(description = "AI 结果状态：success/fallback", example = "success")
    private String aiResultStatus;

    @Schema(description = "是否可获取提示", example = "true")
    private Boolean hintAvailable;
}
