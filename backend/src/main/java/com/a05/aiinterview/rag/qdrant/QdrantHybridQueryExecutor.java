package com.a05.aiinterview.rag.qdrant;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 执行 Qdrant 原生 hybrid 分支查询。
 */
@Component
@RequiredArgsConstructor
public class QdrantHybridQueryExecutor {

    private static final int QUERY_TIMEOUT_SECONDS = 3;

    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;

    public List<SearchHit> denseRecall(RagRetrievalRequest request) {
        if (request == null || isBlank(request.getDenseQueryText())) {
            return List.of();
        }

        float[] embedding = embeddingModel.embed(request.getDenseQueryText());
        Points.QueryPoints query = Points.QueryPoints.newBuilder()
                .setCollectionName(ragProperties.getCollectionName())
                .setQuery(QueryFactory.nearest(toFloatList(embedding)))
                .setUsing(ragProperties.getDenseVectorName())
                .setFilter(buildBaseFilter(request))
                .setLimit(Math.max(1, ragProperties.getDenseTopK()))
                .setScoreThreshold((float) ragProperties.getMinScore())
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false).build())
                .build();
        return execute(query);
    }

    public List<SearchHit> sparseRecall(RagRetrievalRequest request) {
        if (request == null || isBlank(request.getSparseQueryText())) {
            return List.of();
        }

        Points.VectorInput sparseInput = Points.VectorInput.newBuilder()
                .setDocument(Points.Document.newBuilder()
                        .setText(request.getSparseQueryText())
                        .setModel("qdrant/bm25")
                        .build())
                .build();

        Points.QueryPoints query = Points.QueryPoints.newBuilder()
                .setCollectionName(ragProperties.getCollectionName())
                .setQuery(QueryFactory.nearest(sparseInput))
                .setUsing(ragProperties.getSparseVectorName())
                .setFilter(buildBaseFilter(request))
                .setLimit(Math.max(1, ragProperties.getSparseTopK()))
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false).build())
                .build();
        return execute(query);
    }

    private List<SearchHit> execute(Points.QueryPoints query) {
        try {
            ListenableFuture<List<Points.ScoredPoint>> future = qdrantClient.queryAsync(query);
            List<Points.ScoredPoint> result = future.get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result == null || result.isEmpty()) {
                return List.of();
            }
            List<SearchHit> hits = new ArrayList<>(result.size());
            for (Points.ScoredPoint point : result) {
                SearchHit hit = fromPoint(point);
                if (hit.active()) {
                    hits.add(hit);
                }
            }
            return hits;
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant hybrid query 执行失败", e);
        }
    }

    private Points.Filter buildBaseFilter(RagRetrievalRequest request) {
        List<Points.Condition> must = new ArrayList<>();
        must.add(ConditionFactory.match("active", true));

        String questionType = normalizeQuestionTypeForCorpus(request.getQuestionType());
        if (!questionType.isBlank()) {
            must.add(ConditionFactory.matchKeyword("question_type", questionType));
        }

        return Points.Filter.newBuilder()
                .addAllMust(must)
                .build();
    }

    private SearchHit fromPoint(Points.ScoredPoint point) {
        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
        return new SearchHit(
                payloadString(payload, "question_id"),
                payloadString(payload, "question_text"),
                payloadString(payload, "intent_concept"),
                payloadString(payload, "reference_context"),
                payloadList(payload, "scoring_key_points"),
                payloadList(payload, "scoring_pitfalls"),
                payloadList(payload, "follow_up_ids"),
                payloadString(payload, "domain_code"),
                payloadString(payload, "question_type"),
                payloadString(payload, "difficulty"),
                payloadList(payload, "keywords"),
                payloadBoolean(payload, "active"),
                point.getScore()
        );
    }

    private List<Float> toFloatList(float[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        List<Float> floats = new ArrayList<>(values.length);
        for (float value : values) {
            floats.add(value);
        }
        return floats;
    }

    private String normalizeQuestionTypeForCorpus(String questionType) {
        if (isBlank(questionType)) {
            return "";
        }
        String normalized = questionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PROJECT_DEEP_DIVE" -> "PROJECT";
            default -> normalized;
        };
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
                .filter(item -> !isBlank(item))
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record SearchHit(
            String questionId,
            String questionText,
            String intentConcept,
            String referenceContext,
            List<String> scoringKeyPoints,
            List<String> scoringPitfalls,
            List<String> followUpIds,
            String domainCode,
            String questionType,
            String difficulty,
            List<String> keywords,
            boolean active,
            float score
    ) {
    }
}
