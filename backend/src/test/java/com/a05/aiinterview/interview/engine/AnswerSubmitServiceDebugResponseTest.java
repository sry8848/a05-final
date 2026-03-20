package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnswerSubmitServiceDebugResponseTest {

    @Test
    void submitAnswer_shouldNotReturnDebugPayloadWhenDisabled() throws Exception {
        TestFixture fixture = new TestFixture();
        fixture.stubWrapupFlow();

        SubmitAttemptResponse response = fixture.submit();

        assertThat(response.getDebug()).isNull();
    }

    @Test
    void submitAnswer_shouldReturnNestedDebugPayloadWhenEnabled() throws Exception {
        TestFixture fixture = new TestFixture();
        setField(fixture.service, "interviewDebugEnabled", true);
        setField(fixture.service, "interviewDebugIncludePrompts", true);
        setField(fixture.service, "interviewDebugMaxTextChars", 1000);
        fixture.stubWrapupFlow();

        SubmitAttemptResponse response = fixture.submit();

        assertThat(response.getDebug()).isNotNull();
        assertThat(response.getDebug().getStateLedger()).isEqualTo(fixture.session.getStateLedgerJson());
        assertThat(response.getDebug().getAiInput()).isNotNull();
        assertThat(response.getDebug().getAiInput().getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(response.getDebug().getAiInput().getSystemPrompt()).isEqualTo("system prompt");
        assertThat(response.getDebug().getAiOutput()).isNotNull();
        assertThat(response.getDebug().getAiOutput().getRawResponse()).isEqualTo("{\"decision\":\"wrapup\"}");
        assertThat(response.getDebug().getAiOutput().getParsedOutput()).containsEntry("decision", "wrapup");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AnswerSubmitService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestFixture {
        private final AiClient aiClient = mock(AiClient.class);
        private final InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        private final InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        private final InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
        private final AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        private final ReportGenerationService reportService = mock(ReportGenerationService.class);
        private final AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                resumeMapper,
                persistenceService,
                reportService,
                new ObjectMapper()
        );

        private final InterviewSession session = new InterviewSession();
        private final InterviewQuestion question = new InterviewQuestion();

        private void stubWrapupFlow() {
            session.setId(1L);
            session.setUserId(9L);
            session.setStatus("in_progress");
            session.setTargetRole("JAVA_BACKEND");
            session.setExperienceLevel("SENIOR");
            session.setMode("professional");
            session.setCurrentQuestionNo(1);
            session.setContextWindowSize(5);
            session.setStateLedgerJson(new HashMap<>(Map.of(
                    "active_project_id", "p_order",
                    "current_focus", "订单超时关闭"
            )));
            session.setSyllabusJson(Map.of("domains", List.of()));

            question.setId(11L);
            question.setSessionId(1L);
            question.setQuestionType("PRINCIPLE");
            question.setDomainId(101L);
            question.setTargetDepth("L2");
            question.setStem("请解释缓存击穿。");
            question.setGenerationContextJson(Map.of("domainCode", "redis"));

            when(attemptMapper.selectByAttemptId("attempt-debug")).thenReturn(null);
            when(sessionMapper.selectById(1L)).thenReturn(session);
            when(questionMapper.selectById(11L)).thenReturn(question);
            when(questionMapper.selectList(any())).thenReturn(List.of(question));
            when(attemptMapper.selectList(any())).thenReturn(List.of());

            EvaluationDecisionOutput evalOutput = EvaluationDecisionOutput.builder()
                    .answerAssessment("证据充分，结束面试")
                    .answerVerdict("STRONG")
                    .decision("wrapup")
                    .targetFocus("综合收束")
                    .targetAngle("role")
                    .difficultyAdjustment("same")
                    .nextQuestionGoal("结束当前面试")
                    .domainOutcome("covered")
                    .build();
            when(aiClient.callEvaluationDecision(any())).thenReturn(
                    AiCallResult.<EvaluationDecisionOutput>builder()
                            .output(evalOutput)
                            .systemPrompt("system prompt")
                            .userPrompt("user prompt")
                            .promptCode("evaluation_decision")
                            .promptVersion("v2")
                            .rawResponse("{\"decision\":\"wrapup\"}")
                            .promptTokens(120)
                            .responseTokens(30)
                            .latencyMs(80L)
                            .build()
            );

            when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                    AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                            .attemptDbId(100L)
                            .attemptId("attempt-debug")
                            .isFinal(true)
                            .shouldEnd(false)
                            .decision("wrapup")
                            .build()
            );
        }

        private SubmitAttemptResponse submit() {
            SubmitAttemptRequest request = new SubmitAttemptRequest();
            request.setQuestionId(11L);
            request.setAttemptId("attempt-debug");
            request.setAnswerText("我会先删缓存，再更新数据库。");
            request.setIsFinal(true);
            return service.submitAnswer(1L, 9L, request);
        }
    }
}
