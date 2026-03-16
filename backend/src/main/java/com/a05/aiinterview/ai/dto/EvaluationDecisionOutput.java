package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评估决策 AI 的最小输出。
 *
 * <p>AI 只负责判断当前层是否通过、是否继续深一层、以及下一题策略；
 * 正式账本与正式 diff 统一由后端代码生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecisionOutput {

    /** 当前题是否通过当前层级要求。 */
    private boolean passCurrentLevel;

    /** 当前知识域是否建议继续深一层。仅 signal=DEEPEN 时允许为 true。 */
    private boolean deepen;

    /** 决策信号：DEEPEN / RETRY_SAME_DOMAIN / NEXT_DOMAIN / END。 */
    private String signal;

    /** 下一题策略；signal=END 时必须为 null。 */
    private NextQuestionStrategy nextStrategy;

    /** AI 对本轮决策的简短说明，仅供日志与调试。 */
    private String reasoning;

    /** 可选摘要字段，便于兼容部分 prompt/日志消费方。 */
    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextQuestionStrategy {

        private Long nextDomainId;
        private String nextDomainCode;
        private String nextDomainName;
        private String questionType;
        private String targetDepth;
        private String targetSkill;
        private List<String> expectedPoints;
        private String difficulty;
        private String focusPoint;
    }
}
