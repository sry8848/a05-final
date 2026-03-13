package com.a05.aiinterview.ai.dto;

import com.a05.aiinterview.common.enums.DomainStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评估决策 AI 调用结果。
 * 包含当前域的状态判断（深度、饱和度）、账本 Patch 描述和下一题策略三部分。
 *
 * <p>signal 含义：
 * <ul>
 *   <li>{@code NEXT_DOMAIN} - 当前域考察充分，移动到下一个域</li>
 *   <li>{@code DEEPEN} - 当前域尚未达到目标深度，继续追问</li>
 *   <li>{@code END} - 所有域均已覆盖或配额耗尽，结束面试</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecisionOutput {

    /**
     * 本次回答对应的知识域编码（与账本 domain_id 对应）。
     * INTRO 等无域题目时可为 null。
     */
    private String domainCode;

    /** 候选人本题实际达到的深度等级（L1~L5）*/
    private String depthReached;

    /** 该知识域是否已"问透"（saturated=true 时写入账本） */
    private boolean saturated;

    /**
     * 决策信号：NEXT_DOMAIN / DEEPEN / END。
     * 主链路根据此字段决定是生成下一题还是结束面试。
     */
    private String signal;

    /** AI 决策的账本 Patch 描述，由 StateLedgerPatchService 应用 */
    private LedgerPatch patch;

    /**
     * 下一题策略（signal=END 时为 null）。
     * AnswerSubmitService 将此策略落库，QuestionStreamService 再据此发起流式出题。
     */
    private NextQuestionStrategy nextStrategy;

    /** AI 决策推理说明（供日志和调试使用，不影响主流程） */
    private String reasoning;

    // ────────────────────────────────────────────

    /**
     * 账本 Patch 描述符。
     * StateLedgerPatchService 根据此对象更新 state_ledger_json 和 session_skill_states。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerPatch {

        /** 需要更新的知识域编码（与账本 domain_id 字段对应） */
        private String domainCode;

        /** 知识域数字 ID（用于更新 session_skill_states 表） */
        private Long domainId;

        /** 新的当前深度等级，如 L3 */
        private String currentDepth;

        /**
         * 新的域状态（写入账本 domain_states[i].status）。
         * 使用 {@link DomainStatus} 枚举，序列化到账本时调用 {@code getValue()}（大写名称）。
         */
        private DomainStatus domainStatus;

        /** 是否已"问透" */
        private boolean saturated;

        /** 本题题目 ID，追加到账本 evidence_refs（可为 null） */
        private Long evidenceQuestionId;

        /** 本题题目类型（用于递增 question_mix_progress 计数） */
        private String questionType;
    }

    // ────────────────────────────────────────────

    /**
     * 下一题策略，由 QuestionStreamService 用于流式出题。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextQuestionStrategy {

        /** 下一题目标知识域数字 ID */
        private Long nextDomainId;

        /** 下一题目标知识域编码 */
        private String nextDomainCode;

        /** 下一题目标知识域中文名 */
        private String nextDomainName;

        /** 下一题题目类型，如 PRINCIPLE / SCENARIO / BEHAVIORAL */
        private String questionType;

        /** 下一题目标深度等级，如 L3 */
        private String targetDepth;

        /** 下一题核心考察点，落库到 interview_questions.target_skill */
        private String targetSkill;

        /** 下一题理想回答要点，落库到 interview_questions.expected_points */
        private List<String> expectedPoints;

        /** 下一题难度等级：L1~L5 */
        private String difficulty;

        /**
         * 下一题的核心考察焦点（自然语言描述）。
         * 预留给 RAG 检索使用：以此为查询词向量检索相关知识片段。
         */
        private String focusPoint;
    }
}
