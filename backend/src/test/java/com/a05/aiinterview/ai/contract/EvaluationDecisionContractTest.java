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
    @DisplayName("合法 CONTINUE 输出应保留新契约字段")
    void continueOutput_shouldKeepNewSchemaFields() {
        String json = """
                {
                  "decisionReason": "上一题回答具备继续追问的信息增益，因此进入项目链路验证真实工程深度。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_ENTER_PROJECT",
                  "nextFocus": "Seata AT 事务边界落地",
                  "targetDomainCode": "",
                  "newCoveredDomains": [
                    {
                      "domainCode": "DOMAIN_SPRING",
                      "domainName": "Spring 框架"
                    }
                  ],
                  "newCoveredPoints": [
                    "Seata AT 模式下全局事务与本地事务的协同边界"
                  ],
                  "retrievalPlans": [
                    {
                      "retrievalNeed": true,
                      "retrievalGoal": "补充 Seata AT 边界细节",
                      "primaryQuery": "Seata AT 边界",
                      "alternateQueries": ["AT 模式 本地事务", "Seata 分支事务注册"],
                      "retrievalType": "domain",
                      "expectedEvidence": ["事务边界", "分支事务注册"],
                      "avoidEvidence": ["通用微服务定义"]
                    }
                  ]
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(output.getFinalDecision()).isEqualTo("S_ENTER_PROJECT");
        assertThat(output.getNextFocus()).isEqualTo("Seata AT 事务边界落地");
        assertThat(output.getTargetDomainCode()).isEmpty();
        assertThat(output.getNewCoveredDomains()).hasSize(1);
        assertThat(output.getNewCoveredDomains().getFirst().getDomainCode()).isEqualTo("DOMAIN_SPRING");
        assertThat(output.getNewCoveredDomains().getFirst().getDomainName()).isEqualTo("Spring 框架");
        assertThat(output.getNewCoveredPoints()).containsExactly("Seata AT 模式下全局事务与本地事务的协同边界");
        assertThat(output.getRetrievalPlans()).hasSize(1);
        assertThat(output.getRetrievalPlans().getFirst().getPrimaryQuery()).isEqualTo("Seata AT 边界");
    }

    @Test
    @DisplayName("合法 WRAPUP 输出应清空下一题规划字段")
    void wrapupOutput_shouldClearNextPlanFields() {
        String json = """
                {
                  "decisionReason": "本场面试已经形成足够能力画像，可以结束。",
                  "interviewAction": "WRAPUP",
                  "finalDecision": "S_WRAPUP",
                  "nextFocus": "不应保留",
                  "targetDomainCode": "DOMAIN_MYSQL",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": [
                    {
                      "retrievalNeed": true,
                      "retrievalGoal": "无效",
                      "primaryQuery": "无效",
                      "alternateQueries": [],
                      "retrievalType": "domain",
                      "expectedEvidence": [],
                      "avoidEvidence": []
                    }
                  ]
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("S_WRAPUP");
        assertThat(output.getNextFocus()).isEmpty();
        assertThat(output.getTargetDomainCode()).isEmpty();
        assertThat(output.getRetrievalPlans()).isEmpty();
    }

    @Test
    @DisplayName("非法策略编码应降级为 WRAPUP")
    void invalidStrategyCode_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "decisionReason": "测试非法策略编码。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_FAKE_CODE",
                  "nextFocus": "缓存一致性",
                  "targetDomainCode": "",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("S_WRAPUP");
    }

    @Test
    @DisplayName("切换知识域缺少 targetDomainCode 应降级为 WRAPUP")
    void switchDomainWithoutTargetDomain_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "decisionReason": "当前域已经形成判断，应切换知识域。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_SWITCH_DOMAIN",
                  "nextFocus": "分布式锁误删防御",
                  "targetDomainCode": "",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("S_WRAPUP");
    }

    @Test
    @DisplayName("进入理论题缺少 targetDomainCode 应降级为 WRAPUP")
    void enterPrincipleWithoutTargetDomain_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "decisionReason": "项目真实性已初步判断，转入理论题补齐知识掌握。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_ENTER_PRINCIPLE",
                  "nextFocus": "AQS 独占锁 state 语义",
                  "targetDomainCode": "",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("S_WRAPUP");
    }

    @Test
    @DisplayName("newCoveredDomains 必须使用 domainCode")
    void newCoveredDomains_shouldUseDomainCode() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "decisionReason": "当前 Redis 题已形成有效判断。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_P_VERIFY",
                  "nextFocus": "缓存穿透过滤策略",
                  "targetDomainCode": "",
                  "newCoveredDomains": [
                    {
                      "domainId": 6,
                      "domainName": "Redis 缓存"
                    }
                  ],
                  "newCoveredPoints": [
                    "缓存穿透基础方案"
                  ],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("S_WRAPUP");
    }
}
