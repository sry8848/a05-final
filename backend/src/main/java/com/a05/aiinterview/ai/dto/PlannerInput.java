package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Planner AI 调用入参。
 * 包含生成本场考纲所需的全部上下文信息。
 */
@Data
@Builder
public class PlannerInput {

    /** 面试会话 ID（审计透传） */
    private Long interviewId;

    /** 当前题目 ID（Planner 场景通常为空，统一审计字段保留） */
    private Long questionId;

    /** 题目变体 ID（Planner 场景通常为空，统一审计字段保留） */
    private String variantId;

    /** 岗位编码，如 JAVA_BACKEND */
    private String positionCode;

    /** 岗位中文名，如 Java 后端开发 */
    private String positionName;

    /** 工作年限枚举，如 SENIOR */
    private String experienceLevel;

    /** 面试模式：practice / professional */
    private String mode;

    /** JD 文本，可为空 */
    private String jobDescription;

    /** 简历确认文本（parsedText），可为空 */
    private String resumeText;

    /** 用户自定义侧重知识点，可为空 */
    private String focusTopics;

    /** 该岗位所有知识域列表（供 Planner 参考分配权重） */
    private List<DomainInfo> domains;

    /** 知识域信息（用于 Planner 上下文） */
    @Data
    @Builder
    public static class DomainInfo {
        private Long domainId;
        private String domainCode;
        private String domainName;
    }
}
