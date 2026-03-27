package com.a05.aiinterview.rag.data;

import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SampleKnowledgeDataLoader tests")
class SampleKnowledgeDataLoaderTest {

    @Test
    @DisplayName("sample documents should cover the planned question types and new card fields")
    @SuppressWarnings("unchecked")
    void sampleDocuments_shouldCoverPlannedQuestionTypesAndNewCardFields() {
        SampleKnowledgeDataLoader loader = new SampleKnowledgeDataLoader(mock(KnowledgeIngestionService.class));

        List<KnowledgeDocument> samples = (List<KnowledgeDocument>) ReflectionTestUtils
                .invokeMethod(loader, "buildSampleDocuments");

        assertThat(samples)
                .hasSize(7)
                .allSatisfy(sample -> {
                    assertThat(sample.getId()).isNotBlank();
                    assertThat(sample.getQuestionText()).isNotBlank();
                    assertThat(sample.getIntentConcept()).isNotBlank();
                    assertThat(sample.getReferenceContext()).isNotBlank();
                    assertThat(sample.getScoringKeyPoints()).isNotEmpty();
                    assertThat(sample.getDomain()).isNotBlank();
                    assertThat(sample.getQuestionType()).isNotBlank();
                    assertThat(sample.getDifficulty()).startsWith("L");
                    assertThat(sample.getKeywords()).isNotEmpty();
                    assertThat(sample.getSource()).isEqualTo("manual_curated");
                    assertThat(sample.isActive()).isTrue();
                    assertThat(sample.getVersion()).isEqualTo("v1");
                });

        assertThat(samples)
                .extracting(KnowledgeDocument::getQuestionType)
                .contains("PRINCIPLE", "SCENARIO", "BEHAVIORAL", "PROJECT");
        assertThat(samples)
                .extracting(KnowledgeDocument::getDomain)
                .contains("java_core", "redis", "distributed", "behavioral");
    }
}
