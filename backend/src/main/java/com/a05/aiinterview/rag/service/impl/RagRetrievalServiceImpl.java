package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRerankService;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 面试题卡检索服务真实实现。
 *
 * <p>固定链路：lexical 预过滤 -> dense recall -> 业务重排 -> 结构化结果。
 * lexical 仅负责候选收缩，不承担独立排序；不再保留旧的伪 sparse + RRF 链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final int LEXICAL_PREFILTER_MULTIPLIER = 4;

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;
    private final RagRerankService ragRerankService;
    private final RagProperties ragProperties;

    @Override
    public RagContext retrieve(RagRetrievalRequest request) {
        if (request == null || !request.isShouldRetrieve()) {
            log.debug("RAG 跳过检索, shouldRetrieve=false");
            return RagContext.empty();
        }

        log.info("RAG 检索开始, questionType={}, domainCode={}, difficultyHint={}, displayQuery={}",
                request.getQuestionType(), request.getDomainCode(),
                request.getDifficultyHint(), request.getDisplayQuery());
        try {
            List<Candidate> lexicalCandidates = lexicalPrefilter(request);
            List<Candidate> denseCandidates = denseRecall(request, lexicalCandidates);
            List<Candidate> rerankInputCandidates = List.copyOf(denseCandidates);
            List<Candidate> reranked = rerank(rerankInputCandidates, request);
            List<String> rerankPostTopQuestionIds = topQuestionIds(reranked, resolveResultLimit());
            reranked = applyHardGuardrails(reranked, request);
            RagContext.RetrievalAudit retrievalAudit = RagContext.RetrievalAudit.builder()
                    .retrievalTriggered(true)
                    .lexicalCandidateCount(lexicalCandidates.size())
                    .denseCandidateCount(denseCandidates.size())
                    .rerankPreTopQuestionIds(topQuestionIds(rerankInputCandidates, resolveResultLimit()))
                    .rerankPostTopQuestionIds(rerankPostTopQuestionIds)
                    .injectedQuestionIds(List.of())
                    .build();
            if (reranked.isEmpty()) {
                log.info("RAG 检索无命中, questionType={}, domainCode={}",
                        request.getQuestionType(), request.getDomainCode());
                return RagContext.emptyTriggered(retrievalAudit);
            }

            int resultLimit = resolveResultLimit();
            List<Candidate> topCandidates = reranked.stream()
                    .limit(resultLimit)
                    .toList();
            List<RagContext.RetrievedMaterial> retrievedMaterials = topCandidates.stream()
                    .map(this::toRetrievedMaterial)
                    .toList();
            List<String> followUpCandidates = collectFollowUpCandidates(topCandidates, resultLimit);
            String summary = buildSummary(retrievedMaterials);
            String contextText = buildContextText(retrievedMaterials);

            return RagContext.builder()
                    .summary(summary)
                    .contextText(contextText)
                    .retrievedMaterials(retrievedMaterials)
                    .followUpCandidates(followUpCandidates)
                    .retrievalAudit(RagContext.RetrievalAudit.builder()
                            .retrievalTriggered(true)
                            .lexicalCandidateCount(retrievalAudit.getLexicalCandidateCount())
                            .denseCandidateCount(retrievalAudit.getDenseCandidateCount())
                            .rerankPreTopQuestionIds(retrievalAudit.getRerankPreTopQuestionIds())
                            .rerankPostTopQuestionIds(retrievalAudit.getRerankPostTopQuestionIds())
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

    private List<Candidate> denseRecall(RagRetrievalRequest request, List<Candidate> lexicalCandidates) {
        String queryText = resolveDenseQueryText(request);
        if (!hasText(queryText)) {
            return List.of();
        }

        SearchRequest searchRequest = buildDenseSearchRequest(queryText, request, hasLexicalPrefilter(lexicalCandidates));
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> allowedQuestionIds = allowedQuestionIds(lexicalCandidates);
        List<Candidate> candidates = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            Candidate candidate = candidateFromDocument(docs.get(i));
            if (!candidate.active) {
                continue;
            }
            if (!allowedQuestionIds.isEmpty() && !allowedQuestionIds.contains(candidate.questionId)) {
                continue;
            }
            candidate.denseRank = candidates.size() + 1;
            candidates.add(candidate);
        }
        return candidates;
    }

    private List<Candidate> lexicalPrefilter(RagRetrievalRequest request) throws Exception {
        List<String> lexicalTerms = resolveLexicalTerms(request);
        if (lexicalTerms.isEmpty()) {
            return List.of();
        }

        Points.ScrollPoints scrollRequest = buildLexicalPrefilterScrollRequest(request, lexicalTerms);
        Points.ScrollResponse response = qdrantClient.scrollAsync(scrollRequest).get(3, TimeUnit.SECONDS);
        if (response == null || response.getResultList().isEmpty()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Points.RetrievedPoint point : response.getResultList()) {
            Candidate candidate = candidateFromPoint(point);
            if (candidate.active) {
                candidates.add(candidate);
            }
        }
        return dedupeByQuestionId(candidates);
    }

    private SearchRequest buildDenseSearchRequest(String queryText, RagRetrievalRequest request, boolean widenForPrefilter) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(queryText)
                .topK(resolveDenseFetchLimit(widenForPrefilter))
                .similarityThreshold(ragProperties.getMinScore());

        String filterExpression = buildDenseFilterExpression(request);
        if (hasText(filterExpression)) {
            builder.filterExpression(filterExpression);
        }
        return builder.build();
    }

    private String buildDenseFilterExpression(RagRetrievalRequest request) {
        List<String> filters = new ArrayList<>();
        String questionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        if (hasText(questionType)) {
            filters.add("question_type == '" + questionType + "'");
        }
        if (hasText(request.getDomainCode())) {
            filters.add("domain_code == '" + request.getDomainCode().trim() + "'");
        }
        return String.join(" && ", filters);
    }

    private Points.ScrollPoints buildLexicalPrefilterScrollRequest(RagRetrievalRequest request, List<String> lexicalTerms) {
        return Points.ScrollPoints.newBuilder()
                .setCollectionName(ragProperties.getCollectionName())
                .setFilter(buildLexicalPrefilterFilter(request, lexicalTerms))
                .setLimit(resolveLexicalPrefilterLimit())
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false).build())
                .build();
    }

    private Points.Filter buildLexicalPrefilterFilter(RagRetrievalRequest request, List<String> lexicalTerms) {
        List<Points.Condition> must = new ArrayList<>();
        must.add(ConditionFactory.match("active", true));

        String questionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        if (hasText(questionType)) {
            must.add(ConditionFactory.matchKeyword("question_type", questionType));
        }
        if (hasText(request.getDomainCode())) {
            must.add(ConditionFactory.matchKeyword("domain_code", request.getDomainCode().trim()));
        }

        List<Points.Condition> should = new ArrayList<>();
        for (String term : lexicalTerms) {
            should.add(ConditionFactory.matchText("question_text", term));
            should.add(ConditionFactory.matchText("intent_concept", term));
            should.add(ConditionFactory.matchKeyword("keywords", term));
        }
        return Points.Filter.newBuilder()
                .addAllMust(must)
                .setMinShould(Points.MinShould.newBuilder()
                        .addAllConditions(should)
                        .setMinCount(1)
                        .build())
                .build();
    }

    private List<Candidate> rerank(List<Candidate> denseCandidates, RagRetrievalRequest request) {
        List<Candidate> reranked = new ArrayList<>(denseCandidates);
        if (reranked.isEmpty()) {
            return reranked;
        }

        try {
            List<RagRerankService.RerankCandidate> rerankCandidates = reranked.stream()
                    .map(this::toRerankCandidate)
                    .toList();
            Map<String, Double> rerankScores = ragRerankService.rerank(request, rerankCandidates).stream()
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
            log.warn("RAG 商业 rerank 失败，回退到本地排序, displayQuery={}, reason={}",
                    request.getDisplayQuery(), e.getMessage());
            for (Candidate candidate : reranked) {
                candidate.finalScore = denseRankScore(candidate.denseRank);
            }
        }

        reranked.sort((left, right) -> {
            int scoreCompare = Double.compare(right.finalScore, left.finalScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int denseRankCompare = Integer.compare(nullSafeRank(left.denseRank), nullSafeRank(right.denseRank));
            if (denseRankCompare != 0) {
                return denseRankCompare;
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

    private double denseRankScore(Integer denseRank) {
        if (denseRank == null || denseRank <= 0) {
            return 0.0d;
        }
        return 1.0d / denseRank;
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
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> candidate.questionId)
                .filter(this::hasText)
                .limit(Math.max(0, limit))
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

    private Candidate candidateFromDocument(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        return Candidate.builder()
                .questionId(asString(metadata.get("question_id")))
                .questionText(firstNonBlank(asString(metadata.get("question_text")), firstLine(doc.getText())))
                .intentConcept(asString(metadata.get("intent_concept")))
                .referenceContext(firstNonBlank(asString(metadata.get("reference_context")), doc.getText()))
                .scoringKeyPoints(asStringList(metadata.get("scoring_key_points")))
                .scoringPitfalls(asStringList(metadata.get("scoring_pitfalls")))
                .followUpIds(asStringList(metadata.get("follow_up_ids")))
                .domainCode(asString(metadata.get("domain_code")))
                .questionType(asString(metadata.get("question_type")))
                .difficulty(asString(metadata.get("difficulty")))
                .keywords(asStringList(metadata.get("keywords")))
                .docContent(doc.getText())
                .active(asBoolean(metadata.get("active")))
                .build();
    }

    private Candidate candidateFromPoint(Points.RetrievedPoint point) {
        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
        return Candidate.builder()
                .questionId(payloadString(payload, "question_id"))
                .questionText(payloadString(payload, "question_text"))
                .intentConcept(payloadString(payload, "intent_concept"))
                .referenceContext(payloadString(payload, "reference_context"))
                .scoringKeyPoints(payloadList(payload, "scoring_key_points"))
                .scoringPitfalls(payloadList(payload, "scoring_pitfalls"))
                .followUpIds(payloadList(payload, "follow_up_ids"))
                .domainCode(payloadString(payload, "domain_code"))
                .questionType(payloadString(payload, "question_type"))
                .difficulty(payloadString(payload, "difficulty"))
                .keywords(payloadList(payload, "keywords"))
                .docContent(payloadString(payload, "doc_content"))
                .active(payloadBoolean(payload, "active"))
                .build();
    }

    private String payloadString(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null) {
            return "";
        }
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INTEGER_VALUE -> String.valueOf(value.getIntegerValue());
            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
            default -> "";
        };
    }

    private List<String> payloadList(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null || !value.hasListValue()) {
            return List.of();
        }
        return value.getListValue().getValuesList().stream()
                .map(this::payloadScalar)
                .filter(this::hasText)
                .toList();
    }

    private String payloadScalar(JsonWithInt.Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INTEGER_VALUE -> String.valueOf(value.getIntegerValue());
            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
            default -> "";
        };
    }

    private boolean payloadBoolean(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        return value != null && value.hasBoolValue() && value.getBoolValue();
    }

    private String resolveDenseQueryText(RagRetrievalRequest request) {
        if (hasText(request.getQueryText())) {
            return request.getQueryText().trim();
        }
        List<String> segments = new ArrayList<>();
        addIfHasText(segments, request.getFocusPoint());
        addAllIfHasText(segments, request.getKeywordQueries());
        addAllIfHasText(segments, request.getMustHaveClues());
        return String.join(" ", segments).trim();
    }

    private void addIfHasText(List<String> values, String text) {
        if (hasText(text)) {
            values.add(text.trim());
        }
    }

    private void addAllIfHasText(List<String> values, List<String> texts) {
        if (texts == null) {
            return;
        }
        for (String text : texts) {
            addIfHasText(values, text);
        }
    }

    private int resolveResultLimit() {
        return Math.max(1, ragProperties.getTopK());
    }

    private int resolveDenseFetchLimit(boolean widenForPrefilter) {
        if (!widenForPrefilter) {
            return resolveResultLimit();
        }
        return Math.max(resolveResultLimit() * LEXICAL_PREFILTER_MULTIPLIER, resolveResultLimit());
    }

    private int resolveLexicalPrefilterLimit() {
        return Math.max(resolveResultLimit() * LEXICAL_PREFILTER_MULTIPLIER, resolveResultLimit());
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

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::asString)
                    .filter(this::hasText)
                    .toList();
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String firstNonBlank(String primary, String fallback) {
        return hasText(primary) ? primary.trim() : asString(fallback).trim();
    }

    private String firstLine(String text) {
        if (!hasText(text)) {
            return "";
        }
        int index = text.indexOf('\n');
        return index >= 0 ? text.substring(0, index).trim() : text.trim();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(this::hasText).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasLexicalPrefilter(List<Candidate> lexicalCandidates) {
        return lexicalCandidates != null && !lexicalCandidates.isEmpty();
    }

    private LinkedHashSet<String> allowedQuestionIds(List<Candidate> lexicalCandidates) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if (lexicalCandidates == null) {
            return allowed;
        }
        for (Candidate candidate : lexicalCandidates) {
            if (hasText(candidate.questionId)) {
                allowed.add(candidate.questionId);
            }
        }
        return allowed;
    }

    private List<Candidate> dedupeByQuestionId(List<Candidate> candidates) {
        Map<String, Candidate> byQuestionId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (hasText(candidate.questionId)) {
                byQuestionId.putIfAbsent(candidate.questionId, candidate);
            }
        }
        return List.copyOf(byQuestionId.values());
    }

    private List<String> resolveLexicalTerms(RagRetrievalRequest request) {
        LinkedHashSet<String> lexicalTerms = new LinkedHashSet<>();
        addLexicalTerms(lexicalTerms, request.getKeywordQueries());
        if (lexicalTerms.isEmpty()) {
            addLexicalTerm(lexicalTerms, request.getDisplayQuery());
            addLexicalTerm(lexicalTerms, request.getFocusPoint());
        }
        return List.copyOf(lexicalTerms);
    }

    private void addLexicalTerms(LinkedHashSet<String> collector, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addLexicalTerm(collector, value);
        }
    }

    private void addLexicalTerm(LinkedHashSet<String> collector, String value) {
        if (!hasText(value)) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            collector.add(normalized);
        }
    }

    private int nullSafeRank(Integer rank) {
        return rank == null ? Integer.MAX_VALUE : rank;
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
        private String docContent;
        private boolean active;
        private Integer denseRank;
        private double finalScore;
    }
}
