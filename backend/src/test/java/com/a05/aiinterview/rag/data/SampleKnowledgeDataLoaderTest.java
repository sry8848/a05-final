package com.a05.aiinterview.rag.data;

import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SampleKnowledgeDataLoader tests")
class SampleKnowledgeDataLoaderTest {

    private static final Set<String> JAVA_BACKEND_DOMAIN_CODES = Set.of(
            "java_core",
            "concurrency",
            "jvm",
            "mysql",
            "redis",
            "spring",
            "mq",
            "microservice",
            "distributed",
            "cs_basics"
    );

    @Test
    @DisplayName("sample documents should use canonical Java backend position and domain codes")
    @SuppressWarnings("unchecked")
    void sampleDocuments_shouldUseCanonicalJavaBackendPositionAndDomainCodes() {
        SampleKnowledgeDataLoader loader = new SampleKnowledgeDataLoader(mock(KnowledgeIngestionService.class));

        List<KnowledgeDocument> samples = (List<KnowledgeDocument>) ReflectionTestUtils
                .invokeMethod(loader, "buildSampleDocuments");

        assertThat(samples)
                .isNotEmpty()
                .allSatisfy(sample -> {
                    assertThat(sample.getPositionCode()).isEqualTo("JAVA_BACKEND");
                    assertThat(sample.getDomainCode()).isIn(JAVA_BACKEND_DOMAIN_CODES);
                });
        assertThat(samples)
                .extracting(KnowledgeDocument::getDomainCode)
                .contains("concurrency", "jvm");
    }
}
