package com.a05.aiinterview.rag.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagContext tests")
class RagContextTest {

    @Test
    @DisplayName("toAuditMap should include retrieval audit fields")
    void toAuditMap_shouldIncludeRetrievalAuditFields() {
        RagContext context = RagContext.builder()
                .summary("命中 1 张题卡")
                .retrievedMaterials(List.of())
                .followUpCandidates(List.of("follow-up-001"))
                .retrievalAudit(RagContext.RetrievalAudit.builder()
                        .retrievalTriggered(true)
                        .denseCandidateCount(3)
                        .sparseCandidateCount(5)
                        .difficultyWindowApplied(true)
                        .difficultyWindowValues(List.of("L1", "L2", "L3"))
                        .fusionTopQuestionIds(List.of("q2", "q1"))
                        .rerankPreTopQuestionIds(List.of("q1", "q2"))
                        .rerankPostTopQuestionIds(List.of("q2", "q1"))
                        .injectedQuestionIds(List.of("q2"))
                        .build())
                .hitCount(1)
                .empty(false)
                .build();

        Map<String, Object> auditMap = context.toAuditMap();

        assertThat(auditMap).containsEntry("hitCount", 1);
        assertThat(auditMap).containsEntry("empty", false);
        assertThat(auditMap).containsEntry("followUpCandidates", List.of("follow-up-001"));
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievalAudit = (Map<String, Object>) auditMap.get("retrievalAudit");
        assertThat(retrievalAudit)
                .containsEntry("retrievalTriggered", true)
                .containsEntry("denseCandidateCount", 3)
                .containsEntry("sparseCandidateCount", 5)
                .containsEntry("difficultyWindowApplied", true)
                .containsEntry("difficultyWindowValues", List.of("L1", "L2", "L3"))
                .containsEntry("fusionTopQuestionIds", List.of("q2", "q1"))
                .containsEntry("rerankPreTopQuestionIds", List.of("q1", "q2"))
                .containsEntry("rerankPostTopQuestionIds", List.of("q2", "q1"))
                .containsEntry("injectedQuestionIds", List.of("q2"));
    }
}
