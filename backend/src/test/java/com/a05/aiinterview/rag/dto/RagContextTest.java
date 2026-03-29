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
                        .lexicalCandidateCount(5)
                        .denseCandidateCount(3)
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
                .containsEntry("lexicalCandidateCount", 5)
                .containsEntry("denseCandidateCount", 3)
                .containsEntry("injectedQuestionIds", List.of("q2"));
    }
}
