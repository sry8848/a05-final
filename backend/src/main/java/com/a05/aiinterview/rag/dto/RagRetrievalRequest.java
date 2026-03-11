package com.a05.aiinterview.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 检索请求，由 AnswerSubmitService 在 Step6 组装并传入 RagRetrievalService。
 *
 * <p>各字段均来自 EvaluationDecisionOutput.NextQuestionStrategy，
 * 检索服务根据这些维度构建查询向量并过滤元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrievalRequest {

    /** 目标知识域编码，用于 metadata 精确过滤 */
    private String domainCode;

    /** 目标题目类型，如 PRINCIPLE / SCENARIO，可用于进一步过滤 */
    private String questionType;

    /** 目标深度等级，如 L3 */
    private String targetDepth;

    /**
     * 核心考察焦点（自然语言）。
     * 由 EvaluationDecisionOutput.NextQuestionStrategy.focusPoint 提供，
     * 作为向量检索的主要查询文本。
     */
    private String focusPoint;

    /** 岗位编码，用于跨知识域的语义增强 */
    private String positionCode;
}
