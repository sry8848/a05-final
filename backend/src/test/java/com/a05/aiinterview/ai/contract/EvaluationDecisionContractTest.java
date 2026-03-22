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
    @DisplayName("CONTINUE 输出应保留新契约字段")
    void continueOutput_shouldKeepNewSchemaFields() {
        String json = """
                {
                  "interviewAction": "CONTINUE",
                  "answerSummary": "候选人解释了缓存击穿的常见方案。",
                  "answerAssessment": "基础概念正确，但工程取舍还不够具体。",
                  "decisionReason": "继续追问仍有信息增益。",
                  "candidateStrategies": ["引导和验证", "变式"],
                  "finalDecision": "引导和验证",
                  "nextEntryAction": "",
                  "nextQuestionType": "PRINCIPLE",
                  "nextFocus": "缓存击穿在高并发场景下的取舍",
                  "expectedAnswerPoints": ["互斥锁", "逻辑过期", "热点 key 隔离"],
                  "newCoveredDomains": [{"domainId": 6, "domainName": "Redis"}],
                  "newCoveredPoints": ["Redis / 缓存击穿基础方案"],
                  "retrievalPlans": [
                    {
                      "retrievalNeed": true,
                      "retrievalGoal": "补充缓存击穿案例",
                      "primaryQuery": "缓存击穿 高并发 取舍",
                      "alternateQueries": ["缓存击穿 互斥锁 逻辑过期"],
                      "retrievalType": "domain",
                      "expectedEvidence": ["典型方案", "适用边界"],
                      "avoidEvidence": ["重复定义题"]
                    }
                  ]
                }
                """;

        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision(json);

        assertThat(output.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(output.getNextQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(output.getNextFocus()).isEqualTo("缓存击穿在高并发场景下的取舍");
        assertThat(output.getExpectedAnswerPoints()).containsExactly("互斥锁", "逻辑过期", "热点 key 隔离");
        assertThat(output.getCandidateStrategies()).containsExactly("引导和验证", "变式");
        assertThat(output.getFinalDecision()).isEqualTo("引导和验证");
        assertThat(output.getNextEntryAction()).isEmpty();
        assertThat(output.getNewCoveredDomains()).hasSize(1);
        assertThat(output.getNewCoveredDomains().getFirst().getDomainName()).isEqualTo("Redis");
        assertThat(output.getRetrievalPlans()).hasSize(1);
        assertThat(output.getRetrievalPlans().getFirst().getPrimaryQuery()).contains("缓存击穿");
    }

    @Test
    @DisplayName("WRAPUP 输出应清空下一题相关字段")
    void wrapupOutput_shouldClearNextQuestionFields() {
        String json = """
                {
                  "interviewAction": "WRAPUP",
                  "answerSummary": "本场面试已获取足够信息。",
                  "answerAssessment": "可以结束本场面试。",
                  "decisionReason": "继续追问收益很低。",
                  "candidateStrategies": ["结束面试"],
                  "finalDecision": "结束面试",
                  "nextEntryAction": "直接从另一个项目切入",
                  "nextQuestionType": "PROJECT_DEEP_DIVE",
                  "nextFocus": "订单超时关闭",
                  "expectedAnswerPoints": ["任务调度"],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": [
                    {
                      "retrievalNeed": true,
                      "retrievalGoal": "无效数据",
                      "primaryQuery": "should be removed",
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
        assertThat(output.getNextQuestionType()).isEmpty();
        assertThat(output.getNextFocus()).isEmpty();
        assertThat(output.getNextEntryAction()).isEmpty();
        assertThat(output.getExpectedAnswerPoints()).isEmpty();
        assertThat(output.getRetrievalPlans()).isEmpty();
    }

    @Test
    @DisplayName("非法 nextQuestionType 应降级为 WRAPUP")
    void invalidNextQuestionType_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "interviewAction": "CONTINUE",
                  "answerSummary": "回答一般。",
                  "answerAssessment": "可以继续，但输出题型非法。",
                  "decisionReason": "测试非法题型。",
                  "candidateStrategies": ["引导和验证"],
                  "finalDecision": "引导和验证",
                  "nextEntryAction": "",
                  "nextQuestionType": "INTRO",
                  "nextFocus": "自我介绍补充",
                  "expectedAnswerPoints": [],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getAnswerAssessment()).contains("降级");
        assertThat(output.getCandidateStrategies()).containsExactly("结束面试");
    }

    @Test
    @DisplayName("缺少 answerAssessment 时应降级为 WRAPUP")
    void missingAnswerAssessment_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "interviewAction": "CONTINUE",
                  "answerSummary": "只给了摘要",
                  "candidateStrategies": ["引导和验证"],
                  "finalDecision": "引导和验证",
                  "nextEntryAction": "",
                  "nextQuestionType": "PRINCIPLE",
                  "nextFocus": "集合框架",
                  "expectedAnswerPoints": [],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getExpectedAnswerPoints()).isEmpty();
        assertThat(output.getNewCoveredPoints()).isEmpty();
        assertThat(output.getRetrievalPlans()).isEmpty();
    }

    @Test
    @DisplayName("缺少或非法 interviewAction 但下一题计划完整时应推断为 CONTINUE")
    void missingInterviewAction_withCompleteNextPlan_shouldInferContinue() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "interviewAction": "PROJECT",
                  "answerSummary": "候选人自我介绍提到了 Redis 和秒杀项目。",
                  "answerAssessment": "项目轮廓清楚，但缺少真实实现细节。",
                  "decisionReason": "应继续追问项目真实性。",
                  "candidateStrategies": ["引导还原", "责任定位"],
                  "finalDecision": "引导还原",
                  "nextEntryAction": "",
                  "nextQuestionType": "PROJECT_DEEP_DIVE",
                  "nextFocus": "Redisson 分布式锁在秒杀里的具体实现",
                  "expectedAnswerPoints": ["锁 key 设计", "异常释放", "压测验证"],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("CONTINUE");
        assertThat(output.getNextQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(output.getNextFocus()).isEqualTo("Redisson 分布式锁在秒杀里的具体实现");
        assertThat(output.getExpectedAnswerPoints()).containsExactly("锁 key 设计", "异常释放", "压测验证");
    }

    @Test
    @DisplayName("退出当前题类时缺少 nextEntryAction 应降级为 WRAPUP")
    void exitDecisionWithoutNextEntryAction_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "interviewAction": "CONTINUE",
                  "answerSummary": "当前理论题已经形成基本判断。",
                  "answerAssessment": "继续停留在理论题上的收益下降。",
                  "decisionReason": "应退出当前题类并转入项目题。",
                  "candidateStrategies": ["退出当前题类"],
                  "finalDecision": "退出当前题类",
                  "nextEntryAction": "",
                  "nextQuestionType": "PROJECT_DEEP_DIVE",
                  "nextFocus": "订单系统里的缓存一致性设计",
                  "expectedAnswerPoints": ["缓存更新时机"],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("结束面试");
    }

    @Test
    @DisplayName("非退出动作却填写 nextEntryAction 应降级为 WRAPUP")
    void nonExitDecisionWithNextEntryAction_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "interviewAction": "CONTINUE",
                  "answerSummary": "当前理论题还值得继续。",
                  "answerAssessment": "可继续做低成本澄清。",
                  "decisionReason": "继续引导可以形成更稳定判断。",
                  "candidateStrategies": ["引导和验证"],
                  "finalDecision": "引导和验证",
                  "nextEntryAction": "从项目里切一个点进入",
                  "nextQuestionType": "PRINCIPLE",
                  "nextFocus": "线程池拒绝策略",
                  "expectedAnswerPoints": ["CallerRunsPolicy"],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getFinalDecision()).isEqualTo("结束面试");
    }

    @Test
    @DisplayName("非法 finalDecision 动作名应降级为 WRAPUP")
    void invalidFinalDecision_shouldFallbackToWrapup() {
        EvaluationDecisionOutput output = validator.parseAndValidateEvaluationDecision("""
                {
                  "interviewAction": "CONTINUE",
                  "answerSummary": "回答一般。",
                  "answerAssessment": "输出了非法动作名。",
                  "decisionReason": "测试非法动作。",
                  "candidateStrategies": ["继续提问"],
                  "finalDecision": "继续提问",
                  "nextEntryAction": "",
                  "nextQuestionType": "PRINCIPLE",
                  "nextFocus": "缓存一致性",
                  "expectedAnswerPoints": ["双删"],
                  "newCoveredDomains": [],
                  "newCoveredPoints": [],
                  "retrievalPlans": []
                }
                """);

        assertThat(output.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(output.getCandidateStrategies()).containsExactly("结束面试");
    }
}
