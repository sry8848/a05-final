package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
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
    @DisplayName("buildCurrentQuestionContext should keep domain and focus empty for intro question without explicit binding")
    void buildCurrentQuestionContext_shouldKeepDomainAndFocusEmptyForIntro() {
        AnswerSubmitService service = new AnswerSubmitService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper())
        );

        InterviewSession session = new InterviewSession();
        session.setId(65L);
        session.setTargetRole("JAVA_BACKEND");
        session.setSyllabusJson(Map.of("domains", java.util.List.of()));
        session.setStateLedgerJson(Map.of());

        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setQuestionType("INTRO");
        question.setStem("请先做一个简短的自我介绍。");
        question.setTargetSkill("沟通表达与项目概述");

        EvaluationDecisionInput.CurrentQuestionContext currentQuestion =
                ReflectionTestUtils.invokeMethod(service, "buildCurrentQuestionContext", session, question);

        assertThat(currentQuestion.getQuestionType()).isEqualTo("INTRO");
        assertThat(currentQuestion.getDomainId()).isNull();
        assertThat(currentQuestion.getDomainName()).isBlank();
        assertThat(currentQuestion.getCurrentFocus()).isBlank();
    }

    @Test
    @DisplayName("buildQuotaSummary should read quota_state from ledger instead of replaying question history")
    void buildQuotaSummary_shouldReadQuotaStateFromLedger() {
        AnswerSubmitService service = new AnswerSubmitService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper())
        );

        InterviewSession session = new InterviewSession();
        session.setId(88L);
        session.setStateLedgerJson(Map.of(
                "quota_state", Map.of(
                        "samePointContinue", 4,
                        "sameDomainContinue", 3,
                        "sameProjectPointContinue", 2,
                        "sameProjectContinue", 1,
                        "principleTotal", 5,
                        "projectTotal", 6,
                        "scenarioTotal", 1,
                        "behavioralTotal", 2
                )
        ));

        InterviewQuestion q1 = new InterviewQuestion();
        q1.setId(1L);
        q1.setQuestionType("PRINCIPLE");
        q1.setTargetSkill("缓存击穿");

        InterviewQuestion q2 = new InterviewQuestion();
        q2.setId(2L);
        q2.setQuestionType("PRINCIPLE");
        q2.setTargetSkill("缓存一致性");

        EvaluationDecisionInput.QuotaSummary quotaSummary =
                ReflectionTestUtils.invokeMethod(service, "buildQuotaSummary", session, List.of(q1, q2));

        assertThat(quotaSummary.getSamePointContinue().getCount()).isEqualTo(4);
        assertThat(quotaSummary.getSameDomainContinue().getCount()).isEqualTo(3);
        assertThat(quotaSummary.getSameProjectPointContinue().getCount()).isEqualTo(2);
        assertThat(quotaSummary.getSameProjectContinue().getCount()).isEqualTo(1);
        assertThat(quotaSummary.getPrincipleTotal().getCount()).isEqualTo(5);
        assertThat(quotaSummary.getProjectTotal().getCount()).isEqualTo(6);
        assertThat(quotaSummary.getScenarioTotal().getCount()).isEqualTo(1);
        assertThat(quotaSummary.getBehavioralTotal().getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("normalizeEvaluationOutput should downgrade when decision does not match current question type")
    void normalizeEvaluationOutput_shouldDowngradeWhenDecisionDoesNotMatchCurrentQuestionType() {
        AnswerSubmitService service = new AnswerSubmitService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper())
        );

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .candidateStrategies(List.of("引导还原"))
                .finalDecision("引导还原")
                .nextEntryAction("")
                .nextQuestionType("PROJECT_DEEP_DIVE")
                .nextFocus("订单系统里的缓存一致性")
                .expectedAnswerPoints(List.of("双删"))
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();

        EvaluationDecisionOutput normalized = ReflectionTestUtils.invokeMethod(
                service,
                "normalizeEvaluationOutput",
                currentQuestion,
                output
        );

        assertThat(normalized.getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(normalized.getFinalDecision()).isEqualTo("结束面试");
    }
}
