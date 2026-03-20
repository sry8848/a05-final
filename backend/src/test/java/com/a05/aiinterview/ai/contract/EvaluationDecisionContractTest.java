package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvaluationDecision 契约测试")
class EvaluationDecisionContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    @Test
    @DisplayName("正常 followup 输出应保留最小决策字段")
    void normalOutput_followup_shouldKeepMinimalDecision() {
        String json = """
                {
                  "answerAssessment": "候选人说明了延迟消息的基本实现，但并发支付和关单冲突处理还不够清晰。",
                  "answerVerdict": "PARTIAL",
                  "decision": "followup",
                  "targetFocus": "支付回调与关单并发冲突",
                  "targetAngle": "boundary",
                  "difficultyAdjustment": "same",
                  "nextQuestionGoal": "继续在当前项目里验证并发冲突处理能力",
                  "nextDomainId": 8,
                  "nextDomainCode": "mq",
                  "nextDomainName": "消息队列",
                  "questionType": "PROJECT_DEEP_DIVE",
                  "focusPoint": "支付回调与关单并发冲突",
                  "domainOutcome": "continue",
                  "retrievalIntent": {
                    "domainHint": "mq",
                    "focusQuery": "支付回调 关单 并发冲突 幂等",
                    "questionTypeHint": "PROJECT_DEEP_DIVE",
                    "avoidRecentFamilies": ["mq.delay-message.definition"]
                  },
                  "statePatch": {
                    "activeProjectId": "p_order",
                    "currentFocus": "订单超时关闭",
                    "weakSignalsAdd": ["并发冲突处理"]
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getAnswerVerdict()).isEqualTo("PARTIAL");
        assertThat(output.getDecision()).isEqualTo("followup");
        assertThat(output.getTargetFocus()).isEqualTo("支付回调与关单并发冲突");
        assertThat(output.getQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(output.getDomainOutcome()).isEqualTo("continue");
        assertThat(output.getRetrievalIntent()).isNotNull();
        assertThat(output.getRetrievalIntent().getFocusQuery()).contains("并发冲突");
    }

    @Test
    @DisplayName("wrapup 决策应清空下一问相关字段")
    void wrapupDecision_shouldDropNextQuestionFields() {
        String json = """
                {
                  "answerAssessment": "当前轮已获得足够证据，可以收束。",
                  "answerVerdict": "STRONG",
                  "decision": "wrapup",
                  "targetFocus": "综合收束",
                  "targetAngle": "role",
                  "difficultyAdjustment": "same",
                  "nextQuestionGoal": "结束面试并进入总结",
                  "nextDomainCode": "mysql",
                  "questionType": "PRINCIPLE",
                  "focusPoint": "事务隔离",
                  "domainOutcome": "covered",
                  "retrievalIntent": {
                    "focusQuery": "事务隔离"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getDecision()).isEqualTo("wrapup");
        assertThat(output.getTargetFocus()).isEqualTo("综合收束");
        assertThat(output.getNextDomainCode()).isNull();
        assertThat(output.getQuestionType()).isNull();
        assertThat(output.getFocusPoint()).isNull();
        assertThat(output.getRetrievalIntent()).isNull();
    }

    @Test
    @DisplayName("wrapup 决策不应把 targetFocus 强制改写为固定文案")
    void wrapupDecision_shouldPreserveAiTargetFocus() {
        String json = """
                {
                  "answerAssessment": "当前轮已经拿到足够证据，可以结束。",
                  "answerVerdict": "STRONG",
                  "decision": "wrapup",
                  "targetFocus": "项目真实性与薄弱点总结",
                  "targetAngle": "role",
                  "difficultyAdjustment": "same",
                  "nextQuestionGoal": "结束面试并总结",
                  "focusPoint": "项目真实性",
                  "domainOutcome": "covered"
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getDecision()).isEqualTo("wrapup");
        assertThat(output.getTargetFocus()).isEqualTo("项目真实性与薄弱点总结");
        assertThat(output.getFocusPoint()).isNull();
    }

    @Test
    @DisplayName("非法 decision 应降级为 wrapup")
    void invalidDecision_shouldFallbackToWrapup() {
        String json = """
                {
                  "answerAssessment": "回答一般。",
                  "answerVerdict": "WEAK",
                  "decision": "deepen",
                  "targetFocus": "缓存击穿",
                  "targetAngle": "boundary",
                  "difficultyAdjustment": "down",
                  "nextQuestionGoal": "继续追问",
                  "questionType": "PRINCIPLE",
                  "focusPoint": "缓存击穿",
                  "domainOutcome": "continue"
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getDecision()).isEqualTo("wrapup");
        assertThat(output.getQuestionType()).isNull();
        assertThat(output.getDomainOutcome()).isEqualTo("covered");
    }

    @Test
    @DisplayName("多子句焦点信息不应被契约层强行收缩")
    void multiClauseFocusFields_shouldBePreserved() {
        String json = """
                {
                  "answerAssessment": "候选人提到了 RabbitMQ 和 Seata，但重点不够聚焦。",
                  "answerVerdict": "PARTIAL",
                  "decision": "probe",
                  "targetFocus": "RabbitMQ 与 Seata 协同机制",
                  "targetAngle": "tradeoff",
                  "difficultyAdjustment": "same",
                  "nextQuestionGoal": "回到当前项目继续验证消息一致性方案",
                  "nextDomainCode": "mq",
                  "nextDomainName": "消息队列",
                  "questionType": "PROJECT_DEEP_DIVE",
                  "focusPoint": "RabbitMQ 与 Seata",
                  "domainOutcome": "continue",
                  "retrievalIntent": {
                    "focusQuery": "RabbitMQ 与 Seata 协同机制"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getTargetFocus()).isEqualTo("RabbitMQ 与 Seata 协同机制");
        assertThat(output.getFocusPoint()).isEqualTo("RabbitMQ 与 Seata");
        assertThat(output.getRetrievalIntent().getFocusQuery()).isEqualTo("RabbitMQ 与 Seata 协同机制");
    }

    @Test
    @DisplayName("缺少关键字段时应降级为 wrapup")
    void missingCriticalFields_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "answerAssessment": "回答较弱"
                }
                """);

        assertThat(output.getDecision()).isEqualTo("wrapup");
        assertThat(output.getAnswerVerdict()).isEqualTo("WEAK");
        assertThat(output.getDomainOutcome()).isEqualTo("covered");
        assertThat(output.getStatePatch()).isEmpty();
    }

    @Test
    @DisplayName("retrievalIntent 的重复家族应保留为列表")
    void retrievalIntent_shouldPreserveAvoidRecentFamilies() {
        String json = """
                {
                  "answerAssessment": "回答基本可用，下一轮补一个基础点。",
                  "answerVerdict": "PARTIAL",
                  "decision": "broaden",
                  "targetFocus": "事务边界",
                  "targetAngle": "implementation",
                  "difficultyAdjustment": "same",
                  "nextQuestionGoal": "补一个 MySQL 事务边界基础题",
                  "nextDomainId": 3,
                  "nextDomainCode": "mysql",
                  "nextDomainName": "MySQL",
                  "questionType": "PRINCIPLE",
                  "focusPoint": "事务边界",
                  "domainOutcome": "circuit_broken",
                  "retrievalIntent": {
                    "domainHint": "mysql",
                    "focusQuery": "事务边界 Spring 事务失效",
                    "questionTypeHint": "PRINCIPLE",
                    "avoidRecentFamilies": ["mysql.transaction.boundary", "project.order.timeout.impl"]
                  },
                  "statePatch": {
                    "coveredPointsAdd": ["mq:delay_message"],
                    "weakSignalsAdd": ["事务边界不清晰"]
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getDecision()).isEqualTo("broaden");
        assertThat(output.getRetrievalIntent().getAvoidRecentFamilies())
                .containsExactly("mysql.transaction.boundary", "project.order.timeout.impl");
        assertThat(output.getStatePatch()).containsEntry("weakSignalsAdd", List.of("事务边界不清晰"));
    }

    @Test
    @DisplayName("旧 signal-only 输出不再作为合法契约接受")
    void legacySignalOnlyOutput_shouldFallbackToWrapup() {
        String json = """
                {
                  "signal": "NEXT_DOMAIN",
                  "nextStrategy": {
                    "nextDomainCode": "redis",
                    "nextDomainName": "Redis",
                    "questionType": "PRINCIPLE",
                    "targetDepth": "L2",
                    "focusPoint": "缓存击穿"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getDecision()).isEqualTo("wrapup");
        assertThat(output.getAnswerVerdict()).isEqualTo("WEAK");
        assertThat(output.getQuestionType()).isNull();
        assertThat(output.getFocusPoint()).isNull();
    }
}
