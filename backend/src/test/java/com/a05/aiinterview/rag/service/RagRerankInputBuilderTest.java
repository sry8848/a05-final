package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagRerankInputBuilder tests")
class RagRerankInputBuilderTest {

    @Test
    @DisplayName("buildQueryText should keep semantic query while removing retrieval metadata labels")
    void buildQueryText_shouldKeepSemanticQueryWhileRemovingRetrievalMetadataLabels() throws Exception {
        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .queryText("寻找考察 Redis 缓存穿透防护方案的题目。")
                .denseQueryText("寻找考察 Redis 缓存穿透防护方案的题目。")
                .sparseQueryText("Redis 缓存穿透 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透"))
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("重点包含布隆过滤器与空值缓存")
                .build();

        assertThat(invokeBuildQueryText(request))
                .contains("寻找考察 Redis 缓存穿透防护方案的题目")
                .doesNotContain("题型")
                .doesNotContain("目标难度")
                .doesNotContain("Redis；缓存穿透")
                .doesNotContain("术语查询");
    }

    @Test
    @DisplayName("buildQueryText should fall back to semantic query when focus point is blank")
    void buildQueryText_shouldFallBackToSemanticQueryWhenFocusPointIsBlank() throws Exception {
        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .queryText("寻找考察 Redis 缓存穿透防护方案的题目。")
                .denseQueryText("寻找考察 Redis 缓存穿透防护方案的题目。")
                .sparseQueryText("Redis 缓存穿透 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透"))
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint(" ")
                .build();

        assertThat(invokeBuildQueryText(request))
                .isEqualTo("寻找考察 Redis 缓存穿透防护方案的题目。");
    }

    private String invokeBuildQueryText(RagRetrievalRequest request) throws Exception {
        Object builder = instantiateBuilder();
        Method method = builder.getClass().getMethod("buildQueryText", RagRetrievalRequest.class);
        return (String) method.invoke(builder, request);
    }

    private Object instantiateBuilder() {
        try {
            Class<?> builderClass = Class.forName("com.a05.aiinterview.rag.service.impl.RagRerankInputBuilder");
            return builderClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("RagRerankInputBuilder should exist", e);
        }
    }
}
