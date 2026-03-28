package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
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
import java.util.Collection;
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
 * <p>固定链路：过滤 -> dense recall -> sparse recall -> RRF -> 业务重排 -> 结构化结果。
 * 不允许伪融合，也不允许忽略 mustHaveClues / avoidClues。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final int RRF_K = 60;
    private static final int SPARSE_FETCH_MULTIPLIER = 4;

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;
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
            List<Candidate> denseCandidates = denseRecall(request);
            List<Candidate> sparseCandidates = sparseRecall(request);

            Map<String, Candidate> fusedCandidates = fuseByRrf(denseCandidates, sparseCandidates);
            List<Candidate> reranked = rerank(fusedCandidates.values(), request);
            if (reranked.isEmpty()) {
                log.info("RAG 检索无命中, questionType={}, domainCode={}",
                        request.getQuestionType(), request.getDomainCode());
                return RagContext.empty();
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
                    .hitCount(retrievedMaterials.size())
                    .empty(false)
                    .build();
        } catch (Exception e) {
            log.error("RAG 检索异常, questionType={}, domainCode={}",
                    request.getQuestionType(), request.getDomainCode(), e);
            return RagContext.empty();
        }
    }

    private List<Candidate> denseRecall(RagRetrievalRequest request) {
        String queryText = resolveDenseQueryText(request);
        if (!hasText(queryText)) {
            return List.of();
        }

        SearchRequest searchRequest = buildDenseSearchRequest(queryText, request);
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            Candidate candidate = candidateFromDocument(docs.get(i));
            if (!candidate.active) {
                continue;
            }
            candidate.denseRank = i + 1;
            candidate.rrfScore += reciprocalRank(candidate.denseRank);
            candidates.add(candidate);
        }
        return candidates;
    }

    private List<Candidate> sparseRecall(RagRetrievalRequest request) throws Exception {
        if (request.getKeywordQueries() == null || request.getKeywordQueries().isEmpty()) {
            return List.of();
        }

        Points.ScrollPoints scrollRequest = buildSparseScrollRequest(request);
        Points.ScrollResponse response = qdrantClient.scrollAsync(scrollRequest).get(3, TimeUnit.SECONDS);
        if (response == null || response.getResultList().isEmpty()) {
            return List.of();
        }

        List<Candidate> scored = new ArrayList<>();
        for (Points.RetrievedPoint point : response.getResultList()) {
            Candidate candidate = candidateFromPoint(point);
            candidate.sparseKeywordScore = keywordScore(candidate, request.getKeywordQueries());
            if (candidate.sparseKeywordScore > 0) {
                scored.add(candidate);
            }
        }

        scored.sort((left, right) -> {
            int scoreCompare = Double.compare(right.sparseKeywordScore, left.sparseKeywordScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.questionId.compareTo(right.questionId);
        });

        for (int i = 0; i < scored.size(); i++) {
            Candidate candidate = scored.get(i);
            candidate.sparseRank = i + 1;
            candidate.rrfScore += reciprocalRank(candidate.sparseRank);
        }
        return scored;
    }

    private SearchRequest buildDenseSearchRequest(String queryText, RagRetrievalRequest request) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(queryText)
                .topK(resolveResultLimit())
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

    private Points.ScrollPoints buildSparseScrollRequest(RagRetrievalRequest request) {
        return Points.ScrollPoints.newBuilder()
                .setCollectionName(ragProperties.getCollectionName())
                .setFilter(buildSparseFilter(request))
                .setLimit(resolveSparseFetchLimit())
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false).build())
                .build();
    }

    private Points.Filter buildSparseFilter(RagRetrievalRequest request) {
        List<Points.Condition> must = new ArrayList<>();
        must.add(ConditionFactory.match("active", true));

        String questionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        if (hasText(questionType)) {
            must.add(ConditionFactory.matchKeyword("question_type", questionType));
        }
        if (hasText(request.getDomainCode())) {
            must.add(ConditionFactory.matchKeyword("domain_code", request.getDomainCode().trim()));
        }

        return Points.Filter.newBuilder().addAllMust(must).build();
    }

    private Map<String, Candidate> fuseByRrf(List<Candidate> denseCandidates, List<Candidate> sparseCandidates) {
        Map<String, Candidate> fused = new LinkedHashMap<>();
        mergeCandidates(fused, denseCandidates);
        mergeCandidates(fused, sparseCandidates);
        return fused;
    }

    private void mergeCandidates(Map<String, Candidate> fused, List<Candidate> incoming) {
        for (Candidate candidate : incoming) {
            Candidate existing = fused.get(candidate.questionId);
            if (existing == null) {
                fused.put(candidate.questionId, candidate);
                continue;
            }
            existing.merge(candidate);
        }
    }

    private List<Candidate> rerank(Collection<Candidate> fusedCandidates, RagRetrievalRequest request) {
        List<Candidate> reranked = new ArrayList<>(fusedCandidates);
        for (Candidate candidate : reranked) {
            candidate.mustHaveHits = clueHits(candidate.combinedText(), request.getMustHaveClues());
            candidate.avoidHits = clueHits(candidate.combinedText(), request.getAvoidClues());
            candidate.finalScore = candidate.rrfScore
                    + mustHaveBonus(candidate.mustHaveHits)
                    - avoidPenalty(candidate.avoidHits)
                    + difficultyBonus(candidate, request.getDifficultyHint())
                    + projectKeywordBonus(candidate, request);
        }

        reranked.sort((left, right) -> {
            int scoreCompare = Double.compare(right.finalScore, left.finalScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int rrfCompare = Double.compare(right.rrfScore, left.rrfScore);
            if (rrfCompare != 0) {
                return rrfCompare;
            }
            return left.questionId.compareTo(right.questionId);
        });
        return reranked;
    }

    private double mustHaveBonus(int hitCount) {
        return hitCount * 2.0d;
    }

    private double avoidPenalty(int hitCount) {
        return hitCount * 2.5d;
    }

    private double difficultyBonus(Candidate candidate, String difficultyHint) {
        int target = difficultyLevel(difficultyHint);
        int actual = difficultyLevel(candidate.difficulty);
        if (target <= 0 || actual <= 0) {
            return 0.0d;
        }
        int delta = Math.abs(target - actual);
        return switch (delta) {
            case 0 -> 1.0d;
            case 1 -> 0.5d;
            default -> 0.0d;
        };
    }

    private double projectKeywordBonus(Candidate candidate, RagRetrievalRequest request) {
        if (!"PROJECT".equalsIgnoreCase(normalizeQuestionTypeForCorpus(request.getQuestionType()))) {
            return 0.0d;
        }
        return keywordScore(candidate, request.getKeywordQueries()) * 0.25d;
    }

    private int clueHits(String haystack, List<String> clues) {
        if (!hasText(haystack) || clues == null || clues.isEmpty()) {
            return 0;
        }
        String normalizedHaystack = haystack.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String clue : clues) {
            if (hasText(clue) && normalizedHaystack.contains(clue.trim().toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    private double keywordScore(Candidate candidate, List<String> keywordQueries) {
        if (keywordQueries == null || keywordQueries.isEmpty()) {
            return 0.0d;
        }
        String haystack = candidate.combinedText().toLowerCase(Locale.ROOT);
        double score = 0.0d;
        for (String keyword : keywordQueries) {
            if (hasText(keyword) && haystack.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                score += 1.0d;
            }
        }
        return score;
    }

    private double reciprocalRank(int rank) {
        return 1.0d / (RRF_K + rank);
    }

    private List<String> collectFollowUpCandidates(List<Candidate> candidates, int resultLimit) {
        LinkedHashSet<String> followUps = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.avoidHits > 0 && candidate.mustHaveHits == 0) {
                continue;
            }
            for (String followUpId : safeList(candidate.followUpIds)) {
                followUps.add(followUpId);
                if (followUps.size() >= resultLimit * 2) {
                    return List.copyOf(followUps);
                }
            }
        }
        return List.copyOf(followUps);
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

    private int resolveSparseFetchLimit() {
        return Math.max(resolveResultLimit() * SPARSE_FETCH_MULTIPLIER, resolveResultLimit());
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

    private int difficultyLevel(String difficulty) {
        if (!hasText(difficulty)) {
            return -1;
        }
        try {
            return Integer.parseInt(difficulty.trim().substring(1));
        } catch (RuntimeException ignored) {
            return -1;
        }
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
        private Integer sparseRank;
        private double sparseKeywordScore;
        private double rrfScore;
        private double finalScore;
        private int mustHaveHits;
        private int avoidHits;

        private void merge(Candidate other) {
            if (other == null) {
                return;
            }
            if (denseRank == null && other.denseRank != null) {
                denseRank = other.denseRank;
            }
            if (sparseRank == null && other.sparseRank != null) {
                sparseRank = other.sparseRank;
            }
            sparseKeywordScore = Math.max(sparseKeywordScore, other.sparseKeywordScore);
            rrfScore += other.rrfScore;
            questionText = firstNonBlankValue(questionText, other.questionText);
            intentConcept = firstNonBlankValue(intentConcept, other.intentConcept);
            referenceContext = firstNonBlankValue(referenceContext, other.referenceContext);
            docContent = firstNonBlankValue(docContent, other.docContent);
            scoringKeyPoints = mergeDistinct(scoringKeyPoints, other.scoringKeyPoints);
            scoringPitfalls = mergeDistinct(scoringPitfalls, other.scoringPitfalls);
            followUpIds = mergeDistinct(followUpIds, other.followUpIds);
            keywords = mergeDistinct(keywords, other.keywords);
            domainCode = firstNonBlankValue(domainCode, other.domainCode);
            questionType = firstNonBlankValue(questionType, other.questionType);
            difficulty = firstNonBlankValue(difficulty, other.difficulty);
        }

        private String combinedText() {
            return StreamBuilder.of(questionText, intentConcept, referenceContext, docContent)
                    .addAll(scoringKeyPoints)
                    .addAll(scoringPitfalls)
                    .addAll(keywords)
                    .build();
        }

        private static String firstNonBlankValue(String left, String right) {
            if (left != null && !left.isBlank()) {
                return left;
            }
            return right == null ? "" : right;
        }

        private static List<String> mergeDistinct(List<String> left, List<String> right) {
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            if (left != null) {
                left.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).forEach(merged::add);
            }
            if (right != null) {
                right.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).forEach(merged::add);
            }
            return List.copyOf(merged);
        }
    }

    private static final class StreamBuilder {
        private final StringBuilder builder = new StringBuilder();

        private static StreamBuilder of(String... values) {
            StreamBuilder streamBuilder = new StreamBuilder();
            if (values != null) {
                for (String value : values) {
                    streamBuilder.add(value);
                }
            }
            return streamBuilder;
        }

        private StreamBuilder add(String value) {
            if (value != null && !value.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(value.trim());
            }
            return this;
        }

        private StreamBuilder addAll(List<String> values) {
            if (values != null) {
                for (String value : values) {
                    add(value);
                }
            }
            return this;
        }

        private String build() {
            return builder.toString();
        }
    }
}
