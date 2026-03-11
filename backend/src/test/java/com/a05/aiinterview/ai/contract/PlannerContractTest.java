package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planner AI 输出 DTO 契约测试。
 *
 * <p>覆盖三条路径：
 * <ol>
 *   <li>正常输出 → 解析成功，{@code domains} 非空、{@code questionMixPlan} 有效、
 *       {@code projects} 关键字段完整</li>
 *   <li>缺少必填字段 → 兜底默认值填入 + warning 日志（日志由 Slf4j 打印，不断言日志内容）</li>
 *   <li>字段类型漂移 → 异常捕获 + 降级逻辑触发，返回最小可用对象</li>
 * </ol>
 */
@DisplayName("Planner 契约测试")
class PlannerContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    // ─────────────────────── 路径1：正常输出 ────────────────────────────────

    @Test
    @DisplayName("正常 JSON → 解析成功，domains/questionMixPlan/projects 关键字段完整")
    void normalOutput_shouldParseSuccessfully() {
        String json = """
                {
                  "title": "Java 后端开发模拟面试",
                  "questionMixPlan": {
                    "INTRO": 1,
                    "PROJECT_DEEP_DIVE": 2,
                    "SCENARIO": 2,
                    "PRINCIPLE": 3,
                    "BEHAVIORAL": 1
                  },
                  "domains": [
                    {
                      "domainId": 1,
                      "domainCode": "jvm",
                      "domainName": "JVM 原理",
                      "targetDepth": "L3",
                      "focusPoints": ["gc", "class_loading"],
                      "priority": "high"
                    }
                  ],
                  "projects": [
                    {
                      "projectId": "p_order",
                      "name": "订单系统",
                      "bizGoal": "高并发订单处理",
                      "role": "后端负责人",
                      "techStack": ["Spring Boot", "MySQL"],
                      "responsibilities": ["设计核心链路"],
                      "hardPoints": ["百万 QPS 限流"],
                      "metrics": ["QPS 提升 10x"],
                      "personalContribution": "独立设计限流模块"
                    }
                  ],
                  "focusAreas": ["高并发", "JVM 调优"]
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getTitle()).isEqualTo("Java 后端开发模拟面试");
        assertThat(output.getDomains()).isNotEmpty();
        assertThat(output.getDomains().get(0).getDomainCode()).isEqualTo("jvm");
        assertThat(output.getQuestionMixPlan()).isNotEmpty();
        assertThat(output.getQuestionMixPlan().get("INTRO")).isEqualTo(1);
        assertThat(output.getProjects()).isNotEmpty();
        assertThat(output.getProjects().get(0).getProjectId()).isEqualTo("p_order");
        assertThat(output.getProjects().get(0).getName()).isEqualTo("订单系统");
    }

    @Test
    @DisplayName("正常 JSON → domains 列表所有必要字段均存在")
    void normalOutput_domainFields_shouldBeComplete() {
        String json = """
                {
                  "domains": [
                    {
                      "domainId": 2,
                      "domainCode": "concurrency",
                      "domainName": "并发编程",
                      "targetDepth": "L4",
                      "priority": "high"
                    }
                  ],
                  "questionMixPlan": { "PRINCIPLE": 3 }
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output.getDomains()).hasSize(1);
        PlannerOutput.DomainPlan domain = output.getDomains().get(0);
        assertThat(domain.getDomainId()).isEqualTo(2L);
        assertThat(domain.getDomainCode()).isEqualTo("concurrency");
        assertThat(domain.getTargetDepth()).isEqualTo("L4");
    }

    // ─────────────────────── 路径2：缺少必填字段 ─────────────────────────────

    @Test
    @DisplayName("domains 为 null → 填入空列表兜底，不抛出异常")
    void missingDomains_shouldFallbackToEmptyList() {
        String json = """
                {
                  "title": "无域面试",
                  "questionMixPlan": { "INTRO": 1 }
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("questionMixPlan 为 null → 填入默认配额兜底，不抛出异常")
    void missingQuestionMixPlan_shouldFallbackToDefaultPlan() {
        String json = """
                {
                  "title": "缺配额面试",
                  "domains": []
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getQuestionMixPlan()).isNotNull().isNotEmpty();
        // 兜底配额必须包含 INTRO 和 PRINCIPLE
        assertThat(output.getQuestionMixPlan()).containsKey("INTRO");
        assertThat(output.getQuestionMixPlan()).containsKey("PRINCIPLE");
    }

    @Test
    @DisplayName("完全空 JSON 对象 → 所有必填字段均兜底，不抛出异常")
    void emptyJsonObject_shouldFallbackAllRequiredFields() {
        String json = "{}";

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getQuestionMixPlan()).isNotNull().isNotEmpty();
    }

    // ─────────────────────── 路径3：字段类型漂移 ─────────────────────────────

    @Test
    @DisplayName("domains 为字符串而非数组（类型漂移）→ 捕获异常，返回降级对象")
    void typeDrift_domainsAsString_shouldReturnFallback() {
        // domains 应为数组，此处故意传字符串
        String json = """
                {
                  "title": "类型漂移测试",
                  "domains": "这是一段错误的字符串而不是数组",
                  "questionMixPlan": { "INTRO": 1 }
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        // 降级后返回最小可用对象
        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getQuestionMixPlan()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("questionMixPlan value 为字符串而非数字（类型漂移）→ 捕获异常，返回降级对象")
    void typeDrift_mixPlanValueAsString_shouldReturnFallback() {
        String json = """
                {
                  "questionMixPlan": { "INTRO": "一道" },
                  "domains": []
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getQuestionMixPlan()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("非法 JSON 格式 → 捕获解析异常，返回降级对象")
    void invalidJson_shouldReturnFallback() {
        String json = "{ this is not valid json }";

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getQuestionMixPlan()).isNotNull().isNotEmpty();
    }

    // ─────────────────────── 直接调用 validate() ────────────────────────────

    @Test
    @DisplayName("validate(null) → 返回最小降级对象，不抛出 NullPointerException")
    void validateNull_shouldReturnFallback() {
        PlannerOutput output = validator.validatePlanner(null);

        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getQuestionMixPlan()).isNotNull().isNotEmpty();
    }
}
