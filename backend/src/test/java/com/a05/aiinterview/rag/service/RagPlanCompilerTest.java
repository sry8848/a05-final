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
                                "补充高频理论题问法和关键误区",
                                "HashMap扩容机制",
                                "Java HashMap 扩容机制 触发条件 2的幂 元素迁移 线程不安全",
                                List.of("HashMap", "resize", "2的幂", "线程不安全"),
                                "L2",
                                List.of("触发条件", "元素迁移", "2的幂原因"),
                                List.of("集合框架泛介绍")
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(request.getDisplayQuery()).isEqualTo("HashMap扩容机制");
        assertThat(request.getQueryText()).contains("HashMap");
        assertThat(request.getKeywordQueries()).contains("HashMap", "resize", "2的幂", "线程不安全");
        assertThat(request.getMustHaveClues()).contains("触发条件", "元素迁移");
        assertThat(request.getAvoidClues()).contains("集合框架泛介绍");
        assertThat(request.getDifficultyHint()).isEqualTo("L2");
        assertThat(request.getPreferredDifficultyLevels()).containsExactly("L1", "L2", "L3");
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
                                "补充行为题高频问法和复盘追问角度",
                                "与产品意见不一致",
                                "行为面试 与产品意见不一致 冲突沟通 推进结果 复盘",
                                List.of("沟通", "推进", "冲突", "协作"),
                                "L2",
                                List.of("沟通动作", "推进过程", "结果复盘"),
                                List.of("空泛价值观表态")
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
    @DisplayName("project retrieval should be skipped when there is no explicit technical hook")
    void projectPlanWithoutHook_shouldSkipRetrieval() {
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
    @DisplayName("project retrieval should be enabled when explicit technical hook exists")
    void projectPlanWithHook_shouldRetrieve() {
        RagRetrievalRequest request = compiler.compile(
                plan(
                        "PROJECT_DEEP_DIVE",
                        "Seata XID 丢失怎么修",
                        "订单系统",
                        List.of(retrievalPlan(
                                "补充 Seata 项目链路里的技术钩子和修复问法",
                                "Seata XID 丢失怎么修",
                                "Seata AT 模式 Feign 调用 XID 丢失 Header 透传 拦截器修复",
                                List.of("Seata", "XID", "Feign", "Header透传"),
                                "L4",
                                List.of("透传链路", "丢失位置", "拦截器修复"),
                                List.of("Seata 基础定义")
                        ))
                ),
                "JAVA_BACKEND",
                "FRESH_GRAD"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getProjectName()).isEqualTo("订单系统");
        assertThat(request.getKeywordQueries()).contains("Seata", "XID", "Feign", "Header透传");
        assertThat(request.getPreferredDifficultyLevels()).containsExactly("L3", "L4", "L5");
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
                                "补充订单超时关闭的场景化追问和工程取舍",
                                "订单超时关闭 幂等性 DB+MQ顺序",
                                "订单超时关闭 幂等性 DB 和 MQ 顺序 事务状态机 消费重复",
                                List.of("订单超时关闭", "幂等", "DB+MQ", "顺序"),
                                "L4",
                                List.of("状态机", "消费幂等", "顺序错乱"),
                                List.of("纯概念定义")
                        )))
                        .build(),
                "JAVA_BACKEND",
                "JUNIOR"
        );

        assertThat(request.isShouldRetrieve()).isTrue();
        assertThat(request.getDomainCode()).isEqualTo("distributed");
        assertThat(request.getPreferredDifficultyLevels()).containsExactly("L3", "L4", "L5");
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
            String goal,
            String displayQuery,
            String queryText,
            List<String> keywordHints,
            String difficultyHint,
            List<String> mustHaveClues,
            List<String> avoidClues
    ) {
        return EvaluationDecisionOutput.RetrievalPlan.builder()
                .goal(goal)
                .displayQuery(displayQuery)
                .queryText(queryText)
                .keywordHints(keywordHints)
                .difficultyHint(difficultyHint)
                .mustHaveClues(mustHaveClues)
                .avoidClues(avoidClues)
                .build();
    }
}
