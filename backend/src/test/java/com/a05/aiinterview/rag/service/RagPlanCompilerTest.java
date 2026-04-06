package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagPlanCompiler tests")
class RagPlanCompilerTest {

    private final RagPlanCompiler compiler = new RagPlanCompiler();

    @Test
    @DisplayName("principle retrieval plan should compile into executable request")
    void principlePlan_shouldCompileIntoExecutableRequest() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "PRINCIPLE",
                        "HashMap扩容机制",
                        "",
                        List.of(retrievalPlan(
                                "Java HashMap 扩容机制 触发条件 2的幂 元素迁移 线程不安全",
                                List.of("HashMap", "resize", "2的幂", "线程不安全"),
                                "L2"
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(request.getFocusPoint()).isEqualTo("HashMap扩容机制");
        assertThat(request.getQueryText()).contains("HashMap");
        assertThat(request.getKeywordQueries()).contains("HashMap", "resize", "2的幂", "线程不安全");
        assertThat(request.getDifficultyHint()).isEqualTo("L2");
    }

    @Test
    @DisplayName("behavioral retrieval plan should retrieve without technical domain binding")
    void behavioralPlan_shouldRetrieveWithoutTechnicalDomainBinding() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "BEHAVIORAL",
                        "讲一次和产品意见不一致的经历",
                        "",
                        List.of(retrievalPlan(
                                "行为面试 与产品意见不一致 冲突沟通 推进结果 复盘",
                                List.of("沟通", "推进", "冲突", "协作"),
                                "L2"
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("BEHAVIORAL");
        assertThat(request.getDomainCode()).isBlank();
        assertThat(request.getKeywordQueries()).contains("沟通", "推进", "冲突", "协作");
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
        assertThat(request.getQueryText()).isBlank();
        assertThat(request.getKeywordQueries()).isEmpty();
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
                                "Seata AT 模式 Feign 调用 XID 丢失 Header 透传 拦截器修复",
                                List.of("Seata", "XID", "Feign", "Header透传"),
                                "L4"
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getProjectName()).isEqualTo("订单系统");
        assertThat(request.getKeywordQueries()).contains("Seata", "XID", "Feign", "Header透传");
    }

    @Test
    @DisplayName("scenario retrieval should preserve explicit domain and neighbor difficulty range")
    void scenarioPlan_shouldPreserveDomainAndNeighborDifficultyRange() {
        RagRetrievalRequest request = compiler.compile(
                DecisionExecutionPlan.builder()
                        .targetQuestionType("SCENARIO")
                        .nextFocus("订单超时关闭 幂等性 DB+MQ顺序")
                        .nextItemName("订单系统")
                        .targetDomainCode("distributed")
                        .retrievalPlans(List.of(retrievalPlan(
                                "订单超时关闭 幂等性 DB 和 MQ 顺序 事务状态机 消费重复",
                                List.of("订单超时关闭", "幂等", "DB+MQ", "顺序"),
                                "L4"
                        )))
                        .build(),
                "JAVA_BACKEND",
                "JUNIOR"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getDomainCode()).isEqualTo("distributed");
        assertThat(request.getDifficultyHint()).isEqualTo("L4");
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
                                "缓存穿透的原理与防护",
                                List.of(),
                                "L3"
                        ))
                ),
                "JAVA_BACKEND",
                "JUNIOR"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getKeywordQueries()).isEmpty();
        assertThat(request.getQueryText()).isEqualTo("缓存穿透的原理与防护");
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
