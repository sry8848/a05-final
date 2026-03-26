package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.impl.RagRetrievalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("RagRetrievalServiceImpl tests")
class RagRetrievalServiceImplTest {

    @Test
    @DisplayName("buildQueryText should treat difficultyHint as soft query hint")
    void buildQueryText_shouldTreatDifficultyHintAsSoftQueryHint() {
        RagRetrievalServiceImpl service = newService();
        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .focusPoint("缓存击穿")
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficultyHint("L4")
                .build();

        String queryText = ReflectionTestUtils.invokeMethod(service, "buildQueryText", request);

        assertThat(queryText)
                .contains("缓存击穿")
                .contains("redis")
                .contains("PRINCIPLE")
                .contains("深度 L4");
    }

    @Test
    @DisplayName("buildSearchRequest should not hard filter by difficultyHint")
    void buildSearchRequest_shouldNotHardFilterByDifficultyHint() {
        RagRetrievalServiceImpl service = newService();
        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficultyHint("L4")
                .focusPoint("缓存击穿")
                .build();

        SearchRequest searchRequest = ReflectionTestUtils.invokeMethod(
                service,
                "buildSearchRequest",
                "缓存击穿 redis PRINCIPLE 深度 L4",
                request
        );

        assertThat(searchRequest).isNotNull();
        assertThat(searchRequest.getFilterExpression().toString())
                .contains("domain_code")
                .doesNotContain("difficulty");
    }

    private RagRetrievalServiceImpl newService() {
        RagProperties properties = new RagProperties();
        properties.setTopK(5);
        properties.setMinScore(0.65);
        return new RagRetrievalServiceImpl(mock(VectorStore.class), properties);
    }
}
