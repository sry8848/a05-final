package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.qdrant.QdrantHybridQueryExecutor;
import com.a05.aiinterview.rag.qdrant.RrfFusion;
import com.a05.aiinterview.rag.service.RagRerankService;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面试题卡检索服务真实实现。
 *
 * <p>现行主链路：dense 独立召回 -> sparse/BM25 独立召回 -> RRF 融合 -> rerank -> 硬护栏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final QdrantHybridQueryExecutor queryExecutor;
    private final RrfFusion rrfFusion;
    private final RagRerankService ragRerankService;
    private final RagProperties ragProperties;

    @Override
    public RagContext retrieve(RagRetrievalRequest request) {
        if (request == null || !request.isShouldRetrieve()) {
            log.debug("RAG 跳过检索, shouldRetrieve=false");
            return RagContext.empty();
        }

        log.info("RAG 检索开始, questionType={}, domainCode={}, difficultyHint={}, denseQueryText={}, sparseQueryText={}",
                request.getQuestionType(), request.getDomainCode(), request.getDifficultyHint(),
                request.getDenseQueryText(), request.getSparseQueryText());
        try {
            List<QdrantHybridQueryExecutor.SearchHit> denseHits = queryExecutor.denseRecall(request);
            List<QdrantHybridQueryExecutor.SearchHit> sparseHits = queryExecutor.sparseRecall(request);

            List<String> denseIds = denseHits.stream().map(QdrantHybridQueryExecutor.SearchHit::questionId).toList();
            List<String> sparseIds = sparseHits.stream().map(QdrantHybridQueryExecutor.SearchHit::questionId).toList();
            List<RrfFusion.FusedScore> fusedScores = rrfFusion.fuse(
                    List.of(denseIds, sparseIds),
                    resolveFusionLimit()
            );

            Map<String, Candidate> candidatePool = mergeCandidates(denseHits, sparseHits);
            List<Candidate> fusionOrdered = orderByFusion(fusedScores, candidatePool, denseIds, sparseIds);

            RagContext.RetrievalAudit audit = RagContext.RetrievalAudit.builder()
                    .retrievalTriggered(true)
                    .denseCandidateCount(denseHits.size())
                    .sparseCandidateCount(sparseHits.size())
                    .fusionTopQuestionIds(topQuestionIds(fusionOrdered, resolveFusionLimit()))
                    .rerankPreTopQuestionIds(topQuestionIds(fusionOrdered, resolveFusionLimit()))
                    .rerankPostTopQuestionIds(List.of())
                    .injectedQuestionIds(List.of())
                    .build();

            if (fusionOrdered.isEmpty()) {
                return RagContext.emptyTriggered(audit);
            }

            List<Candidate> reranked = rerank(fusionOrdered, request);
            List<String> rerankPostIds = topQuestionIds(reranked, resolveResultLimit());
            List<Candidate> guarded = applyHardGuardrails(reranked, request);
            if (guarded.isEmpty()) {
                return RagContext.emptyTriggered(RagContext.RetrievalAudit.builder()
                        .retrievalTriggered(true)
                        .denseCandidateCount(audit.getDenseCandidateCount())
                        .sparseCandidateCount(audit.getSparseCandidateCount())
                        .fusionTopQuestionIds(audit.getFusionTopQuestionIds())
                        .rerankPreTopQuestionIds(audit.getRerankPreTopQuestionIds())
                        .rerankPostTopQuestionIds(rerankPostIds)
                        .injectedQuestionIds(List.of())
                        .build());
            }

            int resultLimit = resolveResultLimit();
            List<Candidate> topCandidates = guarded.stream()
                    .limit(resultLimit)
                    .toList();
            List<RagContext.RetrievedMaterial> retrievedMaterials = topCandidates.stream()
                    .map(this::toRetrievedMaterial)
                    .toList();
            List<String> followUpCandidates = collectFollowUpCandidates(topCandidates, resultLimit);

            return RagContext.builder()
                    .summary(buildSummary(retrievedMaterials))
                    .contextText(buildContextText(retrievedMaterials))
                    .retrievedMaterials(retrievedMaterials)
                    .followUpCandidates(followUpCandidates)
                    .retrievalAudit(RagContext.RetrievalAudit.builder()
                            .retrievalTriggered(true)
                            .denseCandidateCount(denseHits.size())
                            .sparseCandidateCount(sparseHits.size())
                            .fusionTopQuestionIds(audit.getFusionTopQuestionIds())
                            .rerankPreTopQuestionIds(audit.getRerankPreTopQuestionIds())
                            .rerankPostTopQuestionIds(rerankPostIds)
                            .injectedQuestionIds(topQuestionIds(topCandidates, resultLimit))
                            .build())
                    .hitCount(retrievedMaterials.size())
                    .empty(false)
                    .build();
        } catch (Exception e) {
            log.error("RAG 检索异常, questionType={}, domainCode={}",
                    request.getQuestionType(), request.getDomainCode(), e);
            return RagContext.emptyTriggered(RagContext.RetrievalAudit.empty(true));
        }
    }

    private Map<String, Candidate> mergeCandidates(List<QdrantHybridQueryExecutor.SearchHit> denseHits,
                                                   List<QdrantHybridQueryExecutor.SearchHit> sparseHits) {
        Map<String, Candidate> pool = new LinkedHashMap<>();
        for (int index = 0; index < denseHits.size(); index++) {
            QdrantHybridQueryExecutor.SearchHit hit = denseHits.get(index);
            Candidate candidate = pool.computeIfAbsent(hit.questionId(), id -> candidateFromHit(hit));
            candidate.denseRank = index + 1;
            candidate.denseScore = hit.score();
        }
        for (int index = 0; index < sparseHits.size(); index++) {
            QdrantHybridQueryExecutor.SearchHit hit = sparseHits.get(index);
            Candidate candidate = pool.computeIfAbsent(hit.questionId(), id -> candidateFromHit(hit));
            candidate.sparseRank = index + 1;
            candidate.sparseScore = hit.score();
        }
        return pool;
    }

    private List<Candidate> orderByFusion(List<RrfFusion.FusedScore> fusedScores,
                                          Map<String, Candidate> candidatePool,
                                          List<String> denseIds,
                                          List<String> sparseIds) {
        if (fusedScores.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Candidate> ordered = new ArrayList<>();
        for (RrfFusion.FusedScore fusedScore : fusedScores) {
            Candidate candidate = candidatePool.get(fusedScore.questionId());
            if (candidate == null || !seen.add(candidate.questionId)) {
                continue;
            }
            candidate.fusionScore = fusedScore.score();
            ordered.add(candidate);
        }
        ordered.sort((left, right) -> {
            int scoreCompare = Double.compare(right.fusionScore, left.fusionScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.questionId.compareTo(right.questionId);
        });
        return ordered;
    }

    private List<Candidate> rerank(List<Candidate> fusionOrdered, RagRetrievalRequest request) {
        if (fusionOrdered.isEmpty()) {
            return List.of();
        }

        List<Candidate> reranked = new ArrayList<>(fusionOrdered);
        try {
            List<RagRerankService.RerankCandidate> candidates = reranked.stream()
                    .map(this::toRerankCandidate)
                    .toList();
            Map<String, Double> rerankScores = ragRerankService.rerank(request, candidates).stream()
                    .collect(Collectors.toMap(
                            RagRerankService.RerankResult::questionId,
                            RagRerankService.RerankResult::relevanceScore,
                            Math::max,
                            LinkedHashMap::new
                    ));
            for (Candidate candidate : reranked) {
                candidate.finalScore = rerankScores.getOrDefault(candidate.questionId, 0.0d);
            }
        } catch (Exception e) {
            log.warn("RAG 商业 rerank 失败，回退到 fusion 排序, queryText={}, reason={}",
                    request.getQueryText(), e.getMessage());
            for (Candidate candidate : reranked) {
                candidate.finalScore = candidate.fusionScore;
            }
        }

        reranked.sort((left, right) -> {
            int scoreCompare = Double.compare(right.finalScore, left.finalScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int fusionCompare = Double.compare(right.fusionScore, left.fusionScore);
            if (fusionCompare != 0) {
                return fusionCompare;
            }
            return left.questionId.compareTo(right.questionId);
        });
        return reranked;
    }

    private List<Candidate> applyHardGuardrails(List<Candidate> candidates, RagRetrievalRequest request) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String expectedQuestionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        String expectedDomainCode = normalizeText(request.getDomainCode());
        return candidates.stream()
                .filter(candidate -> matchesHardGuardrails(candidate, expectedQuestionType, expectedDomainCode))
                .toList();
    }

    private boolean matchesHardGuardrails(Candidate candidate, String expectedQuestionType, String expectedDomainCode) {
        String actualQuestionType = normalizeQuestionTypeForCorpus(candidate.questionType);
        String actualDomainCode = normalizeText(candidate.domainCode);

        if ("BEHAVIORAL".equals(expectedQuestionType)) {
            return "BEHAVIORAL".equals(actualQuestionType) && actualDomainCode.isBlank();
        }

        if ("PROJECT".equals(expectedQuestionType)) {
            if (!"PROJECT".equals(actualQuestionType)) {
                return false;
            }
            if (expectedDomainCode.isBlank()) {
                return true;
            }
            return expectedDomainCode.equals(actualDomainCode);
        }

        if (!expectedQuestionType.isBlank() && !expectedQuestionType.equals(actualQuestionType)) {
            return false;
        }
        if (expectedDomainCode.isBlank()) {
            return true;
        }
        return expectedDomainCode.equals(actualDomainCode);
    }

    private RagRerankService.RerankCandidate toRerankCandidate(Candidate candidate) {
        return new RagRerankService.RerankCandidate(
                candidate.questionId,
                candidate.questionText,
                candidate.intentConcept,
                candidate.referenceContext,
                candidate.scoringKeyPoints,
                candidate.scoringPitfalls
        );
    }

    private Candidate candidateFromHit(QdrantHybridQueryExecutor.SearchHit hit) {
        return Candidate.builder()
                .questionId(hit.questionId())
                .questionText(hit.questionText())
                .intentConcept(hit.intentConcept())
                .referenceContext(hit.referenceContext())
                .scoringKeyPoints(hit.scoringKeyPoints())
                .scoringPitfalls(hit.scoringPitfalls())
                .followUpIds(hit.followUpIds())
                .domainCode(hit.domainCode())
                .questionType(hit.questionType())
                .difficulty(hit.difficulty())
                .keywords(hit.keywords())
                .active(hit.active())
                .build();
    }

    private List<String> collectFollowUpCandidates(List<Candidate> candidates, int resultLimit) {
        LinkedHashSet<String> followUps = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            for (String followUpId : safeList(candidate.followUpIds)) {
                followUps.add(followUpId);
                if (followUps.size() >= resultLimit * 2) {
                    return List.copyOf(followUps);
                }
            }
        }
        return List.copyOf(followUps);
    }

    private List<String> topQuestionIds(List<Candidate> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> candidate.questionId)
                .filter(this::hasText)
                .limit(limit)
                .toList();
    }

    private String buildSummary(List<RagContext.RetrievedMaterial> materials) {
        return materials.stream()
                .map(material -> material.getQuestionText() + "：" + material.getIntentConcept())
                .collect(Collectors.joining("\n"));
    }

    private String buildContextText(List<RagContext.RetrievedMaterial> materials) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < materials.size(); i++) {
            RagContext.RetrievedMaterial material = materials.get(i);
            sb.append("--- 题卡 ").append(i + 1).append(" ---\n");
            sb.append("题目：").append(material.getQuestionText()).append("\n");
            sb.append("考点：").append(material.getIntentConcept()).append("\n");
            sb.append("语境：").append(material.getReferenceContext()).append("\n");
            if (!safeList(material.getScoringKeyPoints()).isEmpty()) {
                sb.append("关键点：").append(String.join("；", material.getScoringKeyPoints())).append("\n");
            }
            if (!safeList(material.getScoringPitfalls()).isEmpty()) {
                sb.append("误区：").append(String.join("；", material.getScoringPitfalls())).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private RagContext.RetrievedMaterial toRetrievedMaterial(Candidate candidate) {
        return RagContext.RetrievedMaterial.builder()
                .questionId(candidate.questionId)
                .questionText(candidate.questionText)
                .intentConcept(candidate.intentConcept)
                .referenceContext(candidate.referenceContext)
                .scoringKeyPoints(candidate.scoringKeyPoints)
                .scoringPitfalls(candidate.scoringPitfalls)
                .followUpIds(candidate.followUpIds)
                .domainCode(candidate.domainCode)
                .questionType(candidate.questionType)
                .difficulty(candidate.difficulty)
                .keywords(candidate.keywords)
                .build();
    }

    private int resolveResultLimit() {
        return Math.max(1, ragProperties.getTopK());
    }

    private int resolveFusionLimit() {
        return Math.max(resolveResultLimit(), ragProperties.getFusionTopK());
    }

    private String normalizeQuestionTypeForCorpus(String questionType) {
        if (!hasText(questionType)) {
            return "";
        }
        String normalized = questionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PROJECT_DEEP_DIVE" -> "PROJECT";
            default -> normalized;
        };
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(this::hasText).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @lombok.Builder
    private static class Candidate {
        private String questionId;
        private String questionText;
        private String intentConcept;
        private String referenceContext;
        @lombok.Builder.Default
        private List<String> scoringKeyPoints = List.of();
        @lombok.Builder.Default
        private List<String> scoringPitfalls = List.of();
        @lombok.Builder.Default
        private List<String> followUpIds = List.of();
        private String domainCode;
        private String questionType;
        private String difficulty;
        @lombok.Builder.Default
        private List<String> keywords = List.of();
        private boolean active;
        private Integer denseRank;
        private Integer sparseRank;
        private float denseScore;
        private float sparseScore;
        private double fusionScore;
        private double finalScore;
    }
}
