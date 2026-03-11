package com.a05.aiinterview.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Planner AI 调用出参（主考纲）。
 * 格式对齐 面试流程策略.md §3 和 prompt-strategy.md §4.1 的输出规范。
 */
@Data
public class PlannerOutput {

    /** 本场面试标题，由 AI 生成，如 "Java 后端开发模拟面试" */
    private String title;

    /**
     * 题型数量配额，key 为 QuestionType 枚举值，value 为计划题数。
     * 示例：{"INTRO":1,"PROJECT_DEEP_DIVE":3,"SCENARIO":3,"PRINCIPLE":4,"BEHAVIORAL":2}
     */
    private Map<String, Integer> questionMixPlan;

    /** 知识域考察计划列表 */
    private List<DomainPlan> domains;

    /** 简历项目锚点列表（用于项目深挖题） */
    private List<ProjectAnchor> projects;

    /** 用户侧重点摘要（Planner 提炼后的版本） */
    private List<String> focusAreas;

    /** 知识域考察计划 */
    @Data
    public static class DomainPlan {
        /** 知识域 ID */
        private Long domainId;
        /** 知识域编码 */
        private String domainCode;
        /** 知识域中文名 */
        private String domainName;
        /** 目标深度：L1~L5 */
        private String targetDepth;
        /** 重点考察子方向，如 ["concurrency", "jvm"] */
        private List<String> focusPoints;
        /** 优先级：high / medium / low */
        private String priority;
    }

    /** 项目锚点（来自简历解析） */
    @Data
    public static class ProjectAnchor {
        /** 项目锚点 ID，由 Planner 分配，如 p_order_risk */
        private String projectId;
        /** 项目名称 */
        private String name;
        /** 项目业务目标 */
        private String bizGoal;
        /** 候选人在项目中的角色 */
        private String role;
        /** 技术栈列表 */
        private List<String> techStack;
        /** 候选人核心职责描述，用于 PROJECT_DEEP_DIVE 出题 */
        private List<String> responsibilities;
        /** 技术难点描述，如 "百万 QPS 限流" */
        private List<String> hardPoints;
        /** 量化指标，如 ["QPS 从 1k 提升到 100k", "P99 降低 40%"] */
        private List<String> metrics;
        /** 候选人个人贡献（排除团队协作部分），用于追问时聚焦个人 */
        private String personalContribution;
    }
}
