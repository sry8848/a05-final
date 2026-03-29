package com.a05.aiinterview.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 回答尝试实体，对应 interview_attempts 表。
 * 每条记录代表候选人对一道题目的一次回答，是"提交回答"主链路的核心落库对象。
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@code attemptId} 由客户端生成，全局唯一，用于幂等校验（同 ID 重试直接返回历史结果）。</li>
 *   <li>{@code evaluationJson} 存储评估决策结果快照，供后续报告生成和审计使用。</li>
 *   <li>{@code isFinal} 预留语音"Final 帧"场景，文字模式默认为 true。</li>
 * </ul>
 */
@Data
@TableName(value = "interview_attempts", autoResultMap = true)
public class InterviewAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    private Long sessionId;

    /** 被回答的题目 ID（关联 interview_questions.id） */
    private Long questionId;

    /**
     * 客户端生成的全局唯一幂等键（UUID）。
     * 同一个 attemptId 重试时直接返回历史结果，不重复处理。
     */
    private String attemptId;

    /** 候选人回答文本（文字模式）或语音转写结果 */
    private String answerText;

    /** 是否为最终版回答（文字模式默认 true；语音模式需等 Final 帧才为 true） */
    private Boolean isFinal;

    /**
     * 评估决策结果快照（JSON）。
     * 当前以 decision、answerVerdict、targetFocus、nextQuestionGoal、
     * nextDomain*、questionType、focusPoint、domainOutcome 等字段为主，
     * 由 callEvaluationDecision 返回后序列化存储，供报告生成和审计使用。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evaluationJson;

    /**
     * 单题详细评估状态：
     * pending / generating / ready / failed。
     */
    private String detailEvaluationStatus;

    /**
     * 单题详细评估结构化结果。
     * 仅存储 QuestionDetailEvaluationOutput 对应业务字段，不混入调试元信息。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> detailEvaluationJson;

    private LocalDateTime createdAt;
}
