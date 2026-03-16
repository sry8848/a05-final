package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 评估决策 AI 调用入参。
 * 每次候选人提交回答时传入，包含当前题目信息、回答文本、上下文和状态账本，
 * 供 AI 决策当前域进度并给出下一题策略。
 */
@Data
@Builder
public class EvaluationDecisionInput {

    /** 面试会话 ID（审计透传） */
    private Long interviewId;

    /** 题目变体 ID（评估场景通常为空，统一审计字段保留） */
    private String variantId;

    /** 岗位编码，如 JAVA_BACKEND */
    private String positionCode;

    /** 工作年限枚举，如 SENIOR */
    private String experienceLevel;

    /** 面试模式：practice / professional */
    private String mode;

    /** 当前被回答的题目 ID */
    private Long currentQuestionId;

    /** 当前题目类型，如 PRINCIPLE */
    private String currentQuestionType;

    /** 当前题目所属知识域编码（与账本 domain_id 对应） */
    private String currentDomainCode;

    /** 当前题目所属知识域中文名 */
    private String currentDomainName;

    /** 当前题目所属知识域的数字 ID（用于账本 patch 写回 session_skill_states） */
    private Long currentDomainId;

    /** 当前题目目标深度，如 L3 */
    private String currentTargetDepth;

    /** 当前题目正文（供 AI 参考） */
    private String currentQuestionStem;

    /** 当前题目理想回答要点列表 */
    private List<String> expectedPoints;

    /** 候选人回答文本（语音模式为含 [停顿 Xs] 标签的富文本） */
    private String answerText;

    /** 候选人简历解析文本（可为空，供评估对齐候选人背景） */
    private String resumeText;

    /**
     * 语音停顿统计（语音模式专属，文字模式为 null）。
     * 包含 wpm（语速）、longPauseCount（长停顿次数）、longestPauseMs（最长停顿毫秒数）。
     * Prompt 构建时若此字段非 null，则追加"表达节奏观察"维度到评估指令中。
     */
    private Map<String, Object> pauseStats;

    /**
     * 当前状态账本（JSON），包含各知识域覆盖进度、题型配额消耗等。
     * AI 根据此决策下一步选哪个域、出什么类型的题。
     */
    private Map<String, Object> stateLedger;

    /**
     * 主考纲（JSON），包含完整知识域列表（含 domainId、domainCode、domainName）。
     * AI 决定下一题时用来查找 nextDomainId 等信息。
     */
    private Map<String, Object> syllabusJson;

    /**
     * 最近 N 题的完整 Q/A 上下文（按策略文档固定上下文窗口）。
     * 供 AI 评估候选人的整体作答趋势。
     */
    private List<QaContext> recentContext;

    /**
     * 单轮 Q/A 上下文载体。
     */
    @Data
    @Builder
    public static class QaContext {
        /** 题目正文 */
        private String stem;
        /** 候选人回答（可为空，表示该题尚无回答） */
        private String answer;
        /** 题目类型 */
        private String questionType;
        /** 知识域编码 */
        private String domainCode;
    }
}
