package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AnswerSubmitService evaluation input tests")
class AnswerSubmitServiceEvaluationInputTest {

    @Test
    @DisplayName("buildCurrentQuestionContext should include domainCode for principle question")
    void buildCurrentQuestionContext_shouldIncludeDomainCodeForPrincipleQuestion() {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(65L);
        session.setTargetRole("JAVA_BACKEND");
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "DOMAIN_SPRING",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("Seata AT 模式边界")
                        )
                )
        ));
        session.setStateLedgerJson(Map.of());

        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setQuestionType("PRINCIPLE");
        question.setDomainId(4L);
        question.setStem("请解释 Seata AT 的边界。");
        question.setTargetSkill("Seata AT 模式边界");
        question.setGenerationContextJson(Map.of(
                "domainCode", "DOMAIN_SPRING",
                "focusPoint", "Seata AT模式边界"
        ));

        EvaluationDecisionInput.CurrentQuestionContext currentQuestion =
                ReflectionTestUtils.invokeMethod(service, "buildCurrentQuestionContext", session, question);

        assertThat(currentQuestion.getQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(currentQuestion.getDomainCode()).isEqualTo("DOMAIN_SPRING");
        assertThat(currentQuestion.getDomainName()).isEqualTo("Spring 框架");
        assertThat(currentQuestion.getCurrentFocus()).isEqualTo("Seata AT模式边界");
    }

    @Test
    @DisplayName("remaining domain menu builder should only keep uncovered domains")
    void remainingDomainMenuBuilder_shouldOnlyKeepUncoveredDomains() {
        RemainingDomainMenuBuilder builder = new RemainingDomainMenuBuilder();

        InterviewSession session = new InterviewSession();
        session.setStateLedgerJson(Map.of(
                "domain_states", List.of(
                        Map.of("domainCode", "DOMAIN_SPRING", "status", "COVERED"),
                        Map.of("domainCode", "DOMAIN_REDIS", "status", "UNASKED")
                )
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "DOMAIN_SPRING",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("事务传播")
                        ),
                        Map.of(
                                "domainCode", "DOMAIN_REDIS",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        )
                )
        ));

        List<EvaluationDecisionInput.RemainingTargetDomain> remaining = builder.build(session);

        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getDomainCode()).isEqualTo("DOMAIN_REDIS");
        assertThat(remaining.getFirst().getDomainName()).isEqualTo("Redis 缓存");
    }

    @Test
    @DisplayName("buildEvaluationInput should source remaining domains and strategies from dedicated builders")
    void buildEvaluationInput_shouldSourceRemainingDomainsAndStrategiesFromDedicatedBuilders() {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(77L);
        session.setTargetRole("JAVA_BACKEND");
        session.setStateLedgerJson(Map.of(
                "quota_state", QuotaStateSupport.initialQuotaState(),
                "domain_states", List.of(
                        Map.of("domainCode", "DOMAIN_SPRING", "status", "COVERED"),
                        Map.of("domainCode", "DOMAIN_REDIS", "status", "UNASKED")
                )
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "DOMAIN_SPRING",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("事务传播")
                        ),
                        Map.of(
                                "domainCode", "DOMAIN_REDIS",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        )
                ),
                "experienceItems", List.of(
                        Map.of(
                                "itemType", "PROJECT",
                                "itemName", "Chabst",
                                "resumeDescription", "项目描述",
                                "techHooks", List.of("Redis")
                        )
                )
        ));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setId(900L);
        currentQuestion.setStem("请解释缓存击穿。");
        currentQuestion.setExpectedPoints(List.of("定义"));
        currentQuestion.setGenerationContextJson(Map.of(
                "domainCode", "DOMAIN_REDIS",
                "focusPoint", "缓存击穿"
        ));

        EvaluationDecisionInput input = ReflectionTestUtils.invokeMethod(
                service,
                "buildEvaluationInput",
                session,
                currentQuestion,
                List.of(currentQuestion),
                List.of(),
                "回答"
        );

        assertThat(input.getRemainingTargetDomains())
                .extracting(EvaluationDecisionInput.RemainingTargetDomain::getDomainCode)
                .containsExactly("DOMAIN_REDIS");
        assertThat(input.getAvailableStrategies())
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .contains(
                        StrategyCode.S_P_VERIFY.code(),
                        StrategyCode.S_ENTER_PROJECT.code(),
                        StrategyCode.S_ENTER_SCENARIO.code(),
                        StrategyCode.S_WRAPUP.code()
                );
    }

    @Test
    @DisplayName("buildRecentInterviewMemory should derive answerAssessment from decisionReason")
    void buildRecentInterviewMemory_shouldDeriveAnswerAssessmentFromDecisionReason() {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(98L);
        session.setStateLedgerJson(Map.of());

        InterviewQuestion question = new InterviewQuestion();
        question.setId(1L);
        question.setQuestionNo(1);
        question.setQuestionType("PRINCIPLE");
        question.setStem("题目");
        question.setGenerationContextJson(Map.of());

        com.a05.aiinterview.interview.entity.InterviewAttempt attempt = new com.a05.aiinterview.interview.entity.InterviewAttempt();
        attempt.setQuestionId(1L);
        attempt.setAnswerText("我先解释原理，再补充边界。");
        attempt.setIsFinal(true);
        attempt.setEvaluationJson(Map.of(
                "decisionReason", "回答覆盖了主线原理，但边界条件还需要继续核实。"
        ));

        List<EvaluationDecisionInput.RecentInterviewMemoryItem> memory = ReflectionTestUtils.invokeMethod(
                service,
                "buildRecentInterviewMemory",
                session,
                List.of(question),
                List.of(attempt)
        );

        assertThat(memory).hasSize(1);
        assertThat(memory.getFirst().getAnswerSummary()).isEqualTo("我先解释原理，再补充边界。");
        assertThat(memory.getFirst().getAnswerAssessment()).isEqualTo("回答覆盖了主线原理，但边界条件还需要继续核实。");
    }

    @Test
    @DisplayName("buildRecentInterviewMemory should strip fallback diagnostics")
    void buildRecentInterviewMemory_shouldStripFallbackDiagnostics() {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(99L);
        session.setStateLedgerJson(Map.of());

        InterviewQuestion question = new InterviewQuestion();
        question.setId(1L);
        question.setQuestionNo(1);
        question.setQuestionType("BEHAVIORAL");
        question.setStem("题目");
        question.setGenerationContextJson(Map.of());

        com.a05.aiinterview.interview.entity.InterviewAttempt attempt = new com.a05.aiinterview.interview.entity.InterviewAttempt();
        attempt.setQuestionId(1L);
        attempt.setAnswerText("我当时先调研，再拍板。");
        attempt.setIsFinal(true);
        attempt.setEvaluationJson(Map.of(
                "effectiveDecisionSource", "SYSTEM_FALLBACK",
                "decisionReason", "系统降级为行为题继续建立真实事件画像。"
        ));

        List<EvaluationDecisionInput.RecentInterviewMemoryItem> memory = ReflectionTestUtils.invokeMethod(
                service,
                "buildRecentInterviewMemory",
                session,
                List.of(question),
                List.of(attempt)
        );

        assertThat(memory).hasSize(1);
        assertThat(memory.getFirst().getAnswerAssessment()).isEmpty();
    }

    private AnswerSubmitService buildService() {
        AiClient aiClient = mock(AiClient.class);
        return new AnswerSubmitService(
                aiClient,
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper()),
                new RemainingDomainMenuBuilder(),
                new AvailableStrategyAssembler(),
                new DecisionExecutionPlanBuilder(),
                new DecisionRepairOrchestrator(aiClient, new DecisionExecutionPlanBuilder()),
                new SystemFallbackPlanBuilder()
        );
    }
}
