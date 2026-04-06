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
                  "answerUnderstanding": "候选人已经给出实质回答，但边界理解还不稳定。",
                  "planningIntent": "当前应继续进入项目链路核实真实工程深度。",
                  "decisionReason": "上一题回答具备继续追问的信息增益，因此进入项目链路验证真实工程深度。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_ENTER_PROJECT",
                  "nextFocus": "Seata AT 事务边界落地",
                  "targetDomainCode": "",
                  "newCoveredDomains": [
                    {
                      "domainCode": "spring",
                      "domainName": "Spring 框架"
                    }
                  ],
                  "newCoveredPoints": [
                    "Seata AT 模式下全局事务与本地事务的协同边界"
                  ],
                  "retrievalPlans": [
                    {
                      "queryText": "Seata AT 模式 本地事务边界 分支事务注册",
                      "keywordHints": ["Seata", "AT", "分支事务注册"],
                      "difficultyHint": "L4"
                    }
                  ]
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(output.getAnswerUnderstanding()).isEqualTo("候选人已经给出实质回答，但边界理解还不稳定。");
        assertThat(output.getPlanningIntent()).isEqualTo("当前应继续进入项目链路核实真实工程深度。");
        assertThat(output.getFinalDecision()).isEqualTo("S_ENTER_PROJECT");
        assertThat(output.getNextFocus()).isEqualTo("Seata AT 事务边界落地");
        assertThat(output.getTargetDomainCode()).isEmpty();
        assertThat(output.getNextItemType()).isEmpty();
        assertThat(output.getNextItemName()).isEmpty();
        assertThat(output.getNextProjectPoint()).isEmpty();
        assertThat(output.getNewCoveredDomains()).hasSize(1);
        assertThat(output.getNewCoveredDomains().getFirst().getDomainCode()).isEqualTo("spring");
        assertThat(output.getNewCoveredDomains().getFirst().getDomainName()).isEqualTo("Spring 框架");
        assertThat(output.getNewCoveredPoints()).containsExactly("Seata AT 模式下全局事务与本地事务的协同边界");
        assertThat(output.getRetrievalPlans()).hasSize(1);
        assertThat(output.getRetrievalPlans().getFirst().getQueryText()).contains("分支事务注册");
        assertThat(output.getRetrievalPlans().getFirst().getKeywordHints()).containsExactly("Seata", "AT", "分支事务注册");
        assertThat(output.getRetrievalPlans().getFirst().getDifficultyHint()).isEqualTo("L4");
    }

    @Test
    @DisplayName("retrieval plan should allow empty keyword hints")
    void retrievalPlan_shouldAllowEmptyKeywordHints() {
        String json = """
                {
                  "answerUnderstanding": "候选人回答较泛，但仍有继续追问价值。",
                  "planningIntent": "继续围绕当前焦点做事实补充。",
                  "decisionReason": "当前回答还有澄清空间，因此继续。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_ENTER_PROJECT",
                  "nextFocus": "Seata 事务边界落地",
                  "targetDomainCode": "",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": [
                    {
                      "queryText": "Seata 事务边界落地",
                      "keywordHints": [],
                      "difficultyHint": "L3"
                    }
                  ]
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(output.getRetrievalPlans()).hasSize(1);
        assertThat(output.getRetrievalPlans().getFirst().getQueryText()).isEqualTo("Seata 事务边界落地");
        assertThat(output.getRetrievalPlans().getFirst().getKeywordHints()).isEmpty();
        assertThat(output.getRetrievalPlans().getFirst().getDifficultyHint()).isEqualTo("L3");
    }

    @Test
    @DisplayName("合法 WRAPUP 输出应清空下一题规划字段")
    void wrapupOutput_shouldClearNextPlanFields() {
        String json = """
                {
                  "answerUnderstanding": "本场已经形成足够判断。",
                  "planningIntent": "无需继续扩展面试范围。",
                  "decisionReason": "本场面试已经形成足够能力画像，可以结束。",
                  "interviewAction": "WRAPUP",
                  "finalDecision": "S_WRAPUP",
                  "nextFocus": "不应保留",
                  "targetDomainCode": "mysql",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": [
                    {
                      "queryText": "无效",
                      "keywordHints": [],
                      "difficultyHint": ""
                    }
                  ]
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getAnswerUnderstanding()).isEqualTo("本场已经形成足够判断。");
        assertThat(output.getPlanningIntent()).isEqualTo("无需继续扩展面试范围。");
        assertThat(output.getFinalDecision()).isEqualTo("S_WRAPUP");
        assertThat(output.getNextFocus()).isEmpty();
        assertThat(output.getNextItemType()).isEmpty();
        assertThat(output.getNextItemName()).isEmpty();
        assertThat(output.getNextProjectPoint()).isEmpty();
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
    @DisplayName("newCoveredDomains 缺少合法编码应降级为 WRAPUP")
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

    @Test
    @DisplayName("project fields should survive legal continue output")
    void projectFields_shouldSurviveLegalContinueOutput() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "answerUnderstanding": "候选人已经进入真实项目语境。",
                  "planningIntent": "下一步应继续项目主线验证工程真实性。",
                  "decisionReason": "当前应继续进入项目主线核实真实工程深度。",
                  "interviewAction": "CONTINUE",
                  "finalDecision": "S_ENTER_PROJECT",
                  "nextFocus": "延迟消息与并发控制",
                  "nextItemType": "PROJECT",
                  "nextItemName": "Chabst",
                  "nextProjectPoint": "RabbitMQ 延迟消息处理超时订单",
                  "targetDomainCode": "",
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(output.getAnswerUnderstanding()).isEqualTo("候选人已经进入真实项目语境。");
        assertThat(output.getPlanningIntent()).isEqualTo("下一步应继续项目主线验证工程真实性。");
        assertThat(output.getNextItemType()).isEqualTo("PROJECT");
        assertThat(output.getNextItemName()).isEqualTo("Chabst");
        assertThat(output.getNextProjectPoint()).isEqualTo("RabbitMQ 延迟消息处理超时订单");
    }
}
