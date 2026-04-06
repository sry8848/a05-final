package com.a05.aiinterview.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /** 是否应该发起检索；为 false 时调用方应直接跳过检索。 */
    private boolean shouldRetrieve;

    /** 真实执行的主查询文本。 */
    private String queryText;

    /** 关键词检索候选词。 */
    @Builder.Default
    private List<String> keywordQueries = List.of();

    /** 目标知识域编码，用于 metadata 精确过滤 */
    private String domainCode;

    /** 目标题目类型，如 PRINCIPLE / SCENARIO，可用于进一步过滤 */
    private String questionType;

    /** 当前轮希望探到的目标深度提示，如 L3；只作为软提示参与查询/排序 */
    private String difficultyHint;

    /**
     * 核心考察焦点（自然语言）。
     * 由 EvaluationDecisionOutput.NextQuestionStrategy.focusPoint 提供，
     * 作为向量检索的主要查询文本。
     */
    private String focusPoint;

    /** 岗位编码，用于跨知识域的语义增强 */
    private String positionCode;

    /** 候选人资历，仅作为默认深度包络和排序偏置。 */
    private String experienceLevel;

    /** 项目题时的项目名；非项目题为空。 */
    private String projectName;
}
