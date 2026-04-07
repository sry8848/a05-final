package com.a05.aiinterview.rag.qdrant;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将题卡映射为 Qdrant hybrid point。
 */
@Component
public class QdrantHybridPointMapper {

    public Points.PointStruct toPoint(KnowledgeDocument doc,
                                      float[] denseVector,
                                      RagProperties ragProperties) {
        Map<String, JsonWithInt.Value> payload = buildPayload(doc);
        String denseText = doc.toDenseRetrievalText();
        String sparseText = doc.toSparseRetrievalText();

        Points.NamedVectors namedVectors = Points.NamedVectors.newBuilder()
                .putVectors(ragProperties.getDenseVectorName(), denseVector(denseVector))
                .putVectors(ragProperties.getSparseVectorName(), sparseDocument(sparseText))
                .build();

        return Points.PointStruct.newBuilder()
                .setId(PointIdFactory.id(UUID.nameUUIDFromBytes(nullSafe(doc.getId()).getBytes())))
                .setVectors(Points.Vectors.newBuilder().setVectors(namedVectors).build())
                .putAllPayload(payload)
                .build();
    }

    private Points.Vector denseVector(float[] values) {
        return Points.Vector.newBuilder()
                .addAllData(toFloatList(values))
                .build();
    }

    private Points.Vector sparseDocument(String sparseText) {
        return Points.Vector.newBuilder()
                .setDocument(Points.Document.newBuilder()
                        .setText(sparseText)
                        .setModel("qdrant/bm25")
                        .build())
                .build();
    }

    private Map<String, JsonWithInt.Value> buildPayload(KnowledgeDocument doc) {
        Map<String, JsonWithInt.Value> payload = new LinkedHashMap<>();
        payload.put("question_id", ValueFactory.value(nullSafe(doc.getId())));
        payload.put("question_text", ValueFactory.value(nullSafe(doc.getQuestionText())));
        payload.put("intent_concept", ValueFactory.value(nullSafe(doc.getIntentConcept())));
        payload.put("reference_context", ValueFactory.value(nullSafe(doc.getReferenceContext())));
        payload.put("scoring_key_points", listValue(doc.getScoringKeyPoints()));
        payload.put("scoring_pitfalls", listValue(doc.getScoringPitfalls()));
        payload.put("domain_code", ValueFactory.value(nullSafe(doc.getDomainCode())));
        payload.put("question_type", ValueFactory.value(nullSafe(doc.getQuestionType())));
        payload.put("difficulty", ValueFactory.value(nullSafe(doc.getDifficulty())));
        payload.put("keywords", listValue(doc.getKeywords()));
        payload.put("source", ValueFactory.value(nullSafe(doc.getSource())));
        payload.put("active", ValueFactory.value(doc.isActive()));
        payload.put("version", ValueFactory.value(nullSafe(doc.getVersion())));
        payload.put("follow_up_ids", listValue(doc.getFollowUpIds()));
        return payload;
    }

    private JsonWithInt.Value listValue(List<String> values) {
        List<JsonWithInt.Value> normalized = values == null ? List.of() : values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .map(ValueFactory::value)
                .toList();
        return ValueFactory.value(normalized);
    }

    private List<Float> toFloatList(float[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        java.util.ArrayList<Float> floats = new java.util.ArrayList<>(values.length);
        for (float value : values) {
            floats.add(value);
        }
        return floats;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
