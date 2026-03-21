package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Planner 契约测试")
class PlannerContractTest {

    private AiOutputContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputContractValidator(new ObjectMapper());
    }

    @Test
    @DisplayName("正常 JSON -> 解析成功，domains 和 experienceItems 关键字段完整")
    void normalOutput_shouldParseSuccessfully() {
        String json = """
                {
                  "planningReasoning": "结合 JD、简历和候选人关注点，优先保留高价值知识域与真实项目入口。",
                  "domains": [
                    {
                      "domainCode": "concurrency",
                      "domainName": "并发编程",
                      "focusPoints": ["线程池拒绝策略", "AQS 独占锁获取流程"]
                    }
                  ],
                  "experienceItems": [
                    {
                      "itemType": "PROJECT",
                      "itemName": "订单系统",
                      "resumeDescription": "负责订单核心链路和异步通知。",
                      "techHooks": ["支付回调幂等处理", "线程池异步通知削峰"]
                    }
                  ]
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getPlanningReasoning()).contains("JD");
        assertThat(output.getDomains()).hasSize(1);
        assertThat(output.getDomains().get(0).getDomainCode()).isEqualTo("concurrency");
        assertThat(output.getExperienceItems()).hasSize(1);
        assertThat(output.getExperienceItems().get(0).getItemType()).isEqualTo("PROJECT");
        assertThat(output.getExperienceItems().get(0).getItemName()).isEqualTo("订单系统");
    }

    @Test
    @DisplayName("experienceItems 为 null -> 填入空列表兜底，不抛出异常")
    void missingExperienceItems_shouldFallbackToEmptyList() {
        String json = """
                {
                  "planningReasoning": "仅规划知识域。",
                  "domains": [
                    {
                      "domainCode": "mysql",
                      "domainName": "MySQL",
                      "focusPoints": ["事务隔离级别"]
                    }
                  ]
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getExperienceItems()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("domains 为 null -> 填入空列表兜底，不抛出异常")
    void missingDomains_shouldFallbackToEmptyList() {
        String json = """
                {
                  "planningReasoning": "仅规划项目入口。",
                  "experienceItems": [
                    {
                      "itemType": "INTERNSHIP",
                      "itemName": "电商实习",
                      "resumeDescription": "参与订单履约链路。",
                      "techHooks": ["库存扣减一致性"]
                    }
                  ]
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("完全空 JSON -> 返回最小可用对象")
    void emptyJsonObject_shouldFallbackAllRequiredFields() {
        PlannerOutput output = validator.parseAndValidatePlanner("{}");

        assertThat(output).isNotNull();
        assertThat(output.getPlanningReasoning()).isNotBlank();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getExperienceItems()).isNotNull();
    }

    @Test
    @DisplayName("字段类型漂移 -> 捕获异常，返回最小降级对象")
    void typeDrift_shouldReturnFallback() {
        String json = """
                {
                  "planningReasoning": "类型漂移测试",
                  "domains": "not-an-array",
                  "experienceItems": []
                }
                """;

        PlannerOutput output = validator.parseAndValidatePlanner(json);

        assertThat(output).isNotNull();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getExperienceItems()).isNotNull();
    }

    @Test
    @DisplayName("validate(null) -> 返回最小降级对象")
    void validateNull_shouldReturnFallback() {
        PlannerOutput output = validator.validatePlanner(null);

        assertThat(output).isNotNull();
        assertThat(output.getPlanningReasoning()).isNotBlank();
        assertThat(output.getDomains()).isNotNull();
        assertThat(output.getExperienceItems()).isNotNull();
    }
}
