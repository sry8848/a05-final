package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvaluationDecision AI 输出 DTO 契约测试。
 *
 * <p>覆盖三条路径：
 * <ol>
 *   <li>正常输出 → 解析成功，{@code signal} 非空、{@code patch} 可应用、
 *       signal != END 时 {@code nextStrategy} 非空</li>
 *   <li>缺少必填字段 → 兜底默认值填入（signal 降级为 NEXT_DOMAIN、patch 填最小对象）</li>
 *   <li>字段类型漂移 → 异常捕获 + 降级逻辑触发（强制 END 信号）</li>
 * </ol>
 */
@DisplayName("EvaluationDecision 契约测试")
class EvaluationDecisionContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    // ─────────────────────── 路径1：正常输出 ────────────────────────────────

    @Test
    @DisplayName("正常 NEXT_DOMAIN 信号 JSON → signal/patch/nextStrategy 均完整")
    void normalOutput_nextDomain_shouldParseSuccessfully() {
        String json = """
                {
                  "domainCode": "jvm",
                  "depthReached": "L3",
                  "saturated": true,
                  "signal": "NEXT_DOMAIN",
                  "patch": {
                    "domainCode": "jvm",
                    "domainId": 1,
                    "currentDepth": "L3",
                    "domainStatus": "COVERED",
                    "saturated": true,
                    "questionType": "PRINCIPLE"
                  },
                  "nextStrategy": {
                    "nextDomainId": 2,
                    "nextDomainCode": "concurrency",
                    "nextDomainName": "并发编程",
                    "questionType": "PRINCIPLE",
                    "targetDepth": "L3",
                    "focusPoint": "AQS 原理"
                  },
                  "reasoning": "JVM 知识域已充分覆盖，移入并发编程。"
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output).isNotNull();
        assertThat(output.getSignal()).isEqualTo("NEXT_DOMAIN");
        assertThat(output.getPatch()).isNotNull();
        assertThat(output.getPatch().getDomainCode()).isEqualTo("jvm");
        assertThat(output.getNextStrategy()).isNotNull();
        assertThat(output.getNextStrategy().getNextDomainCode()).isEqualTo("concurrency");
    }

    @Test
    @DisplayName("正常 END 信号 JSON → signal=END，nextStrategy 允许为 null")
    void normalOutput_end_shouldAllowNullNextStrategy() {
        String json = """
                {
                  "domainCode": "mysql",
                  "depthReached": "L2",
                  "saturated": false,
                  "signal": "END",
                  "patch": {
                    "domainCode": "mysql",
                    "domainId": 3,
                    "currentDepth": "L2",
                    "domainStatus": "COVERED",
                    "saturated": false,
                    "questionType": "SCENARIO"
                  },
                  "reasoning": "所有知识域均已覆盖，面试结束。"
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getSignal()).isEqualTo("END");
        // END 信号时 nextStrategy 为 null 是合法的
        assertThat(output.getPatch()).isNotNull();
    }

    @Test
    @DisplayName("正常 DEEPEN 信号 JSON → nextStrategy 包含追问方向")
    void normalOutput_deepen_shouldHaveNextStrategy() {
        String json = """
                {
                  "signal": "DEEPEN",
                  "patch": {
                    "domainCode": "redis",
                    "domainId": 4,
                    "currentDepth": "L2",
                    "domainStatus": "IN_PROGRESS",
                    "saturated": false,
                    "questionType": "PRINCIPLE"
                  },
                  "nextStrategy": {
                    "nextDomainId": 4,
                    "nextDomainCode": "redis",
                    "nextDomainName": "Redis",
                    "questionType": "SCENARIO",
                    "targetDepth": "L3",
                    "focusPoint": "缓存击穿场景处理"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getSignal()).isEqualTo("DEEPEN");
        assertThat(output.getNextStrategy().getFocusPoint()).contains("缓存击穿");
    }

    // ─────────────────────── 路径2：缺少必填字段 ─────────────────────────────

    @Test
    @DisplayName("signal 为 null → 兜底为 NEXT_DOMAIN，不抛出异常")
    void missingSignal_shouldFallbackToNextDomain() {
        String json = """
                {
                  "patch": {
                    "domainCode": "jvm",
                    "domainStatus": "IN_PROGRESS"
                  },
                  "nextStrategy": {
                    "nextDomainCode": "concurrency",
                    "nextDomainId": 2
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getSignal()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("patch 为 null → 填入最小 patch 兜底，不抛出异常")
    void missingPatch_shouldFallbackToMinimalPatch() {
        String json = """
                {
                  "signal": "NEXT_DOMAIN",
                  "domainCode": "jvm",
                  "nextStrategy": {
                    "nextDomainId": 2,
                    "nextDomainCode": "concurrency"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getPatch()).isNotNull();
    }

    @Test
    @DisplayName("signal=NEXT_DOMAIN 但 nextStrategy 为 null → 降级为 END 信号")
    void missingNextStrategyForNextDomain_shouldDowngradeToEnd() {
        String json = """
                {
                  "signal": "NEXT_DOMAIN",
                  "patch": {
                    "domainCode": "jvm",
                    "domainStatus": "COVERED"
                  }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getSignal()).isEqualTo("END");
    }

    @Test
    @DisplayName("完全空 JSON 对象 → 所有必填字段均兜底，signal 非空")
    void emptyJsonObject_shouldFallbackAllRequiredFields() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("{}");

        assertThat(output.getSignal()).isNotNull().isNotBlank();
        assertThat(output.getPatch()).isNotNull();
    }

    // ─────────────────────── 路径3：字段类型漂移 ─────────────────────────────

    @Test
    @DisplayName("patch 为数组而非对象（类型漂移）→ 捕获异常，返回 END 降级对象")
    void typeDrift_patchAsArray_shouldReturnFallback() {
        String json = """
                {
                  "signal": "NEXT_DOMAIN",
                  "patch": ["wrong", "array"],
                  "nextStrategy": {}
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output).isNotNull();
        assertThat(output.getSignal()).isNotNull();
    }

    @Test
    @DisplayName("saturated 为字符串而非布尔值（类型漂移）→ 捕获异常，返回降级对象")
    void typeDrift_saturatedAsString_shouldReturnFallback() {
        String json = """
                {
                  "signal": "END",
                  "saturated": "yes",
                  "patch": { "domainCode": "jvm" }
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output).isNotNull();
        assertThat(output.getSignal()).isNotNull();
    }

    @Test
    @DisplayName("非法 JSON 格式 → 捕获解析异常，返回强制 END 降级对象")
    void invalidJson_shouldReturnFallbackWithEndSignal() {
        String json = "{invalid}";

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output).isNotNull();
        assertThat(output.getSignal()).isEqualTo("END");
    }

    // ─────────────────────── 直接调用 validate() ────────────────────────────

    @Test
    @DisplayName("validate(null) → 返回 END 降级对象，不抛出 NullPointerException")
    void validateNull_shouldReturnFallbackWithEndSignal() {
        EvaluationDecisionOutput output = validator.validateEvaluationDecision(null);

        assertThat(output).isNotNull();
        assertThat(output.getSignal()).isEqualTo("END");
    }
}
