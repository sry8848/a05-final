package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvaluationDecision 契约测试")
class EvaluationDecisionContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    @Test
    @DisplayName("正常 NEXT_DOMAIN 输出应保留最小决策字段")
    void normalOutput_nextDomain_shouldKeepMinimalDecision() {
        String json = """
                {
                  "passCurrentLevel": true,
                  "deepen": false,
                  "signal": "NEXT_DOMAIN",
                  "nextStrategy": {
                    "nextDomainId": 2,
                    "nextDomainCode": "concurrency",
                    "nextDomainName": "并发编程",
                    "questionType": "PRINCIPLE",
                    "targetDepth": "L2",
                    "difficulty": "L2",
                    "targetSkill": "线程池参数设计",
                    "expectedPoints": ["说明核心参数", "说明常见场景", "说明拒绝策略"],
                    "focusPoint": "线程池参数"
                  },
                  "reasoning": "当前层通过，切换下一个知识域。"
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.isPassCurrentLevel()).isTrue();
        assertThat(output.isDeepen()).isFalse();
        assertThat(output.getSignal()).isEqualTo("NEXT_DOMAIN");
        assertThat(output.getNextStrategy()).isNotNull();
        assertThat(output.getNextStrategy().getNextDomainCode()).isEqualTo("concurrency");
    }

    @Test
    @DisplayName("signal=DEEPEN 但 passCurrentLevel=false 时应降级")
    void deepenWithoutPassingCurrentLevel_shouldDowngrade() {
        String json = """
                {
                  "passCurrentLevel": false,
                  "deepen": true,
                  "signal": "DEEPEN",
                  "nextStrategy": {
                    "nextDomainId": 4,
                    "nextDomainCode": "redis",
                    "nextDomainName": "Redis",
                    "questionType": "PRINCIPLE",
                    "targetDepth": "L3",
                    "difficulty": "L3",
                    "targetSkill": "缓存击穿处理",
                    "expectedPoints": ["说明常见方案", "比较优缺点"],
                    "focusPoint": "缓存击穿"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.isPassCurrentLevel()).isFalse();
        assertThat(output.isDeepen()).isFalse();
        assertThat(output.getSignal()).isEqualTo("NEXT_DOMAIN");
    }

    @Test
    @DisplayName("RETRY_SAME_DOMAIN 应保留为合法同域重试信号")
    void retrySameDomain_shouldRemainRetrySignal() {
        String json = """
                {
                  "passCurrentLevel": false,
                  "deepen": false,
                  "signal": "RETRY_SAME_DOMAIN",
                  "nextStrategy": {
                    "nextDomainId": 6,
                    "nextDomainCode": "redis",
                    "nextDomainName": "Redis",
                    "questionType": "PRINCIPLE",
                    "targetDepth": "L2",
                    "difficulty": "L2",
                    "targetSkill": "缓存击穿处理",
                    "expectedPoints": ["说明常见方案", "比较优缺点"],
                    "focusPoint": "缓存击穿"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.isPassCurrentLevel()).isFalse();
        assertThat(output.isDeepen()).isFalse();
        assertThat(output.getSignal()).isEqualTo("RETRY_SAME_DOMAIN");
        assertThat(output.getNextStrategy()).isNotNull();
        assertThat(output.getNextStrategy().getNextDomainCode()).isEqualTo("redis");
    }

    @Test
    @DisplayName("signal=END 时 nextStrategy 应被清空")
    void endSignal_shouldDropNextStrategy() {
        String json = """
                {
                  "passCurrentLevel": true,
                  "deepen": false,
                  "signal": "END",
                  "nextStrategy": {
                    "nextDomainCode": "mysql",
                    "nextDomainName": "MySQL",
                    "questionType": "PRINCIPLE",
                    "targetDepth": "L2",
                    "difficulty": "L2",
                    "targetSkill": "事务隔离级别",
                    "expectedPoints": ["说明四种级别", "说明现象"],
                    "focusPoint": "事务隔离"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getSignal()).isEqualTo("END");
        assertThat(output.isDeepen()).isFalse();
        assertThat(output.getNextStrategy()).isNull();
    }

    @Test
    @DisplayName("多焦点 targetSkill 应收敛为单焦点")
    void multiFocusTargetSkill_shouldBeSanitized() {
        String json = """
                {
                  "passCurrentLevel": true,
                  "deepen": false,
                  "signal": "NEXT_DOMAIN",
                  "nextStrategy": {
                    "nextDomainId": 8,
                    "nextDomainCode": "mq",
                    "nextDomainName": "消息队列",
                    "questionType": "PROJECT_DEEP_DIVE",
                    "targetDepth": "L2",
                    "difficulty": "L2",
                    "targetSkill": "RabbitMQ 与 Seata 协同机制",
                    "expectedPoints": ["解释 RabbitMQ 延迟消息", "对比 Seata AT 与 TCC"],
                    "focusPoint": "RabbitMQ 与 Seata"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getNextStrategy()).isNotNull();
        assertThat(output.getNextStrategy().getTargetSkill()).doesNotContain("Seata");
        assertThat(output.getNextStrategy().getFocusPoint()).doesNotContain("Seata");
        assertThat(output.getNextStrategy().getExpectedPoints()).allMatch(point -> point.contains("RabbitMQ"));
    }

    @Test
    @DisplayName("空 JSON 应降级为 END")
    void emptyJson_shouldFallbackToEnd() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("{}");

        assertThat(output.getSignal()).isEqualTo("END");
        assertThat(output.isPassCurrentLevel()).isFalse();
        assertThat(output.isDeepen()).isFalse();
    }
}
