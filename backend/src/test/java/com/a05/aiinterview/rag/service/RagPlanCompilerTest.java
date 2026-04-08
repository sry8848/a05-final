package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagPlanCompiler tests")
class RagPlanCompilerTest {

    private final RagPlanCompiler compiler = new RagPlanCompiler();

    @Test
    @DisplayName("rag retrieval request should not expose domainCode as executable retrieval input")
    void ragRetrievalRequest_shouldNotExposeDomainCodeAsExecutableRetrievalInput() {
        assertThat(Arrays.stream(RagRetrievalRequest.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList())
                .doesNotContain("domainCode");
    }

    @Test
    @DisplayName("principle retrieval plan should compile into executable request")
    void principlePlan_shouldCompileIntoExecutableRequest() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "PRINCIPLE",
                        "HashMap扩容机制",
                        "",
                        List.of(retrievalPlan(
                                "寻找考察 Java HashMap 扩容触发条件、2 的幂容量设计与元素迁移机制的题目。",
                                List.of("HashMap", "resize", "2的幂", "线程不安全"),
                                "L2"
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(request.getQueryText())
                .isEqualTo("寻找考察 Java HashMap 扩容触发条件、2 的幂容量设计与元素迁移机制的题目。");
        assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
        assertThat(request.getSparseQueryText()).isEqualTo("HashMap resize 2的幂 线程不安全");
    }

    @Test
    @DisplayName("behavioral retrieval plan should compile executable query texts")
    void behavioralPlan_shouldCompileExecutableQueryTexts() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "BEHAVIORAL",
                        "讲一次和产品意见不一致的经历",
                        "",
                        List.of(retrievalPlan(
                                "寻找行为面试中考察与产品意见不一致时如何沟通、推进和复盘的题目。",
                                List.of("沟通", "推进", "冲突", "协作"),
                                "L2"
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("BEHAVIORAL");
        assertThat(request.getQueryText())
                .isEqualTo("寻找行为面试中考察与产品意见不一致时如何沟通、推进和复盘的题目。");
        assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
        assertThat(request.getSparseQueryText()).isEqualTo("沟通 推进 冲突 协作");
    }

    @Test
    @DisplayName("project retrieval should be skipped when there is no retrieval plan")
    void projectPlanWithoutRetrievalPlan_shouldSkipRetrieval() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "PROJECT_DEEP_DIVE",
                        "泛项目叙述",
                        "订单系统",
                        List.of()
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isFalse();
        assertThat(request.getQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(request.getQueryText()).isBlank();
        assertThat(request.getDenseQueryText()).isBlank();
        assertThat(request.getSparseQueryText()).isBlank();
    }

    @Test
    @DisplayName("project retrieval should be enabled when retrieval plan exists")
    void projectPlanWithRetrievalPlan_shouldRetrieve() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "PROJECT_DEEP_DIVE",
                        "Seata XID 丢失怎么修",
                        "订单系统",
                        List.of(retrievalPlan(
                                "寻找项目面试中考察 Seata AT 模式下 Feign 调用时 XID 丢失与 Header 透传修复的题目。",
                                List.of("Seata", "XID", "Feign", "Header透传"),
                                "L4"
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(request.getQueryText())
                .isEqualTo("寻找项目面试中考察 Seata AT 模式下 Feign 调用时 XID 丢失与 Header 透传修复的题目。");
        assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
        assertThat(request.getSparseQueryText()).isEqualTo("Seata XID Feign Header透传");
    }

    @Test
    @DisplayName("scenario retrieval should compile executable query texts")
    void scenarioPlan_shouldCompileExecutableQueryTexts() {
        RagRetrievalRequest request = compiler.compile(
                DecisionExecutionPlan.builder()
                        .targetQuestionType("SCENARIO")
                        .nextFocus("订单超时关闭 幂等性 DB+MQ顺序")
                        .nextItemName("订单系统")
                        .targetDomainCode("distributed")
                        .retrievalPlans(List.of(retrievalPlan(
                                "寻找订单超时关闭场景下，考察幂等、DB 与 MQ 顺序一致性及事务状态机的题目。",
                                List.of("订单超时关闭", "幂等", "DB+MQ", "顺序"),
                                "L4"
                        )))
                        .build(),
                "JAVA_BACKEND",
                "JUNIOR"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("SCENARIO");
        assertThat(request.getQueryText())
                .isEqualTo("寻找订单超时关闭场景下，考察幂等、DB 与 MQ 顺序一致性及事务状态机的题目。");
        assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
        assertThat(request.getSparseQueryText()).isEqualTo("订单超时关闭 幂等 DB+MQ 顺序");
    }

    @Test
    @DisplayName("empty keyword hints should still allow retrieval when query text exists")
    void emptyKeywordHints_shouldStillRetrieve() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "SCENARIO",
                        "缓存穿透的原理与防护",
                        "",
                        List.of(retrievalPlan(
                                "寻找考察缓存穿透原理与防护方案的题目。",
                                List.of(),
                                "L3"
                        ))
                ),
                "JAVA_BACKEND",
                "JUNIOR"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("SCENARIO");
        assertThat(request.getQueryText()).isEqualTo("寻找考察缓存穿透原理与防护方案的题目。");
        assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
        assertThat(request.getSparseQueryText()).isBlank();
    }

    @Test
    @DisplayName("focus point should not be appended into sparse query when keyword hints are empty")
    void focusPoint_shouldNotBeAppendedIntoSparseQuery() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "SCENARIO",
                        "Redis 热点 key 过期后的流量保护",
                        "",
                        List.of(retrievalPlan(
                                "寻找考察缓存击穿后流量保护与回源控制的题目。",
                                List.of(),
                                "L3"
                        ))
                ),
                "JAVA_BACKEND",
                "JUNIOR"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("SCENARIO");
        assertThat(request.getQueryText()).isEqualTo("寻找考察缓存击穿后流量保护与回源控制的题目。");
        assertThat(request.getDenseQueryText()).isEqualTo(request.getQueryText());
        assertThat(request.getSparseQueryText()).isBlank();
    }

    private DecisionExecutionPlan plan(
            String questionType,
            String nextFocus,
            String nextItemName,
            List<EvaluationDecisionOutput.RetrievalPlan> retrievalPlans
    ) {
        return DecisionExecutionPlan.builder()
                .targetQuestionType(questionType)
                .nextFocus(nextFocus)
                .nextItemName(nextItemName)
                .retrievalPlans(retrievalPlans)
                .build();
    }

    private EvaluationDecisionOutput.RetrievalPlan retrievalPlan(
            String queryText,
            List<String> keywordHints,
            String difficultyHint
    ) {
        return EvaluationDecisionOutput.RetrievalPlan.builder()
                .queryText(queryText)
                .keywordHints(keywordHints)
                .difficultyHint(difficultyHint)
                .build();
    }
}
