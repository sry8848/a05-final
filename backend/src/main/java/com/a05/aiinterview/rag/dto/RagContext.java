package com.a05.aiinterview.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索结果上下文。
 *
 * <p>{@code contextText} 是聚合后可直接注入出题 Prompt 的字符串。
 * 空结果（无命中或降级）时 {@code empty=true}，{@code contextText} 为空字符串，
 * 出题链路按原逻辑继续运行，不受影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagContext {

    /** 聚合后可注入 Prompt 的知识片段文本；无命中时为空字符串 */
    private String contextText;

    /** 检索结果摘要，供出题阶段和审计日志使用。 */
    private String summary;

    /** 结构化命中题卡，供下游直接消费。 */
    @Builder.Default
    private List<RetrievedMaterial> retrievedMaterials = new ArrayList<>();

    /** 从 top 命中题卡聚合出的推荐追问候选。 */
    @Builder.Default
    private List<String> followUpCandidates = new ArrayList<>();

    /** 轻量检索审计信息，供日志、调试和出题上下文回放使用。 */
    private RetrievalAudit retrievalAudit;

    /** 实际命中的文档片段数量 */
    private int hitCount;

    /** true 表示无命中或降级，出题时跳过注入 */
    private boolean empty;

    /**
     * 构建空结果（无命中或 rag.enabled=false）。
     *
     * @return 空 RagContext 实例
     */
    public static RagContext empty() {
        return RagContext.builder()
                .contextText("")
                .summary("")
                .hitCount(0)
                .retrievedMaterials(List.of())
                .followUpCandidates(List.of())
                .retrievalAudit(RetrievalAudit.empty(false))
                .empty(true)
                .build();
    }

    public static RagContext emptyTriggered(RetrievalAudit retrievalAudit) {
        return RagContext.builder()
                .contextText("")
                .summary("")
                .hitCount(0)
                .retrievedMaterials(List.of())
                .followUpCandidates(List.of())
                .retrievalAudit(retrievalAudit == null ? RetrievalAudit.empty(true) : retrievalAudit)
                .empty(true)
                .build();
    }

    /**
     * 将检索结果序列化为审计日志可存储的 Map 结构。
     *
     * @return 包含 contextText/hitCount/empty 的 Map
     */
    public Map<String, Object> toAuditMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hitCount", hitCount);
        map.put("empty", empty);
        if (summary != null && !summary.isBlank()) {
            map.put("summary", summary);
        }
        if (retrievedMaterials != null && !retrievedMaterials.isEmpty()) {
            map.put("retrievedMaterials", retrievedMaterials);
        }
        if (followUpCandidates != null && !followUpCandidates.isEmpty()) {
            map.put("followUpCandidates", followUpCandidates);
        }
        if (retrievalAudit != null) {
            Map<String, Object> auditMap = new LinkedHashMap<>();
            auditMap.put("retrievalTriggered", retrievalAudit.isRetrievalTriggered());
            auditMap.put("denseCandidateCount", retrievalAudit.getDenseCandidateCount());
            auditMap.put("sparseCandidateCount", retrievalAudit.getSparseCandidateCount());
            auditMap.put("difficultyWindowApplied", retrievalAudit.isDifficultyWindowApplied());
            auditMap.put("difficultyWindowValues", retrievalAudit.getDifficultyWindowValues());
            auditMap.put("fusionTopQuestionIds", retrievalAudit.getFusionTopQuestionIds());
            auditMap.put("rerankPreTopQuestionIds", retrievalAudit.getRerankPreTopQuestionIds());
            auditMap.put("rerankPostTopQuestionIds", retrievalAudit.getRerankPostTopQuestionIds());
            auditMap.put("injectedQuestionIds", retrievalAudit.getInjectedQuestionIds());
            map.put("retrievalAudit", auditMap);
        }
        if (!empty && contextText != null && !contextText.isBlank()) {
            // 审计日志只截取前 500 字符，避免日志过大
            map.put("contextPreview", contextText.length() > 500
                    ? contextText.substring(0, 500) + "..."
                    : contextText);
        }
        return map;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedMaterial {
        private String questionId;
        private String questionText;
        private String intentConcept;
        private String referenceContext;
        @Builder.Default
        private List<String> scoringKeyPoints = new ArrayList<>();
        @Builder.Default
        private List<String> scoringPitfalls = new ArrayList<>();
        @Builder.Default
        private List<String> followUpIds = new ArrayList<>();
        private String domainCode;
        private String questionType;
        private String difficulty;
        @Builder.Default
        private List<String> keywords = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalAudit {
        private boolean retrievalTriggered;
        private int denseCandidateCount;
        private int sparseCandidateCount;
        private boolean difficultyWindowApplied;
        @Builder.Default
        private List<String> difficultyWindowValues = new ArrayList<>();
        @Builder.Default
        private List<String> fusionTopQuestionIds = new ArrayList<>();
        @Builder.Default
        private List<String> rerankPreTopQuestionIds = new ArrayList<>();
        @Builder.Default
        private List<String> rerankPostTopQuestionIds = new ArrayList<>();
        @Builder.Default
        private List<String> injectedQuestionIds = new ArrayList<>();

        public static RetrievalAudit empty(boolean retrievalTriggered) {
            return RetrievalAudit.builder()
                    .retrievalTriggered(retrievalTriggered)
                    .denseCandidateCount(0)
                    .sparseCandidateCount(0)
                    .difficultyWindowApplied(false)
                    .difficultyWindowValues(List.of())
                    .fusionTopQuestionIds(List.of())
                    .rerankPreTopQuestionIds(List.of())
                    .rerankPostTopQuestionIds(List.of())
                    .injectedQuestionIds(List.of())
                    .build();
        }
    }
}
