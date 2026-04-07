package com.a05.aiinterview.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 检索请求，由 AnswerSubmitService 在 Step6 组装并传入 RagRetrievalService。
 *
 * <p>各字段均来自上游 retrieval plan。
 * 检索服务会将 queryText 作为语义视图输入 dense/rerank，
 * 将 keywordQueries/sparseQueryText 作为词法视图输入 sparse/BM25，
 * 并根据 questionType/difficultyHint 追加业务过滤。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrievalRequest {

    /** 是否应该发起检索；为 false 时调用方应直接跳过检索。 */
    private boolean shouldRetrieve;

    /** 上游生成的独立、完整自然语言语义查询。 */
    private String queryText;

    /** dense 分支执行的自然语言语义查询文本。 */
    private String denseQueryText;

    /** sparse/BM25 分支执行的术语锚点文本；为空时直接跳过 sparse。 */
    private String sparseQueryText;

    /** sparse/BM25 使用的术语锚点列表。 */
    @Builder.Default
    private List<String> keywordQueries = List.of();

    /** 目标题目类型，如 PRINCIPLE / SCENARIO，可用于进一步过滤 */
    private String questionType;

    /** 当前轮目标难度提示；开启配置时会被解析成相邻一级 difficulty window 硬过滤。 */
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
