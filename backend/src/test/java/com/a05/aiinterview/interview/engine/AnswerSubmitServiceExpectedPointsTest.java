package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.entity.Resume;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerSubmitServiceExpectedPointsTest {

    @Test
    void submitAnswer_shouldPassQuestionExpectedPointsToEvaluationInput() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                resumeMapper,
                persistenceService,
                reportService,
                new ObjectMapper()
        );

        Long sessionId = 1L;
        Long userId = 9L;
        Long questionId = 11L;

        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("in_progress");
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        session.setResumeId(7001L);
        session.setCurrentQuestionNo(1);
        session.setContextWindowSize(5);
        session.setStateLedgerJson(new HashMap<>());
        session.setSyllabusJson(Map.of("domains", List.of()));

        InterviewQuestion question = new InterviewQuestion();
        question.setId(questionId);
        question.setSessionId(sessionId);
        question.setQuestionType("PRINCIPLE");
        question.setDomainId(101L);
        question.setTargetDepth("L3");
        question.setStem("请解释线程池参数。");
        question.setExpectedPoints(List.of("corePoolSize", "拒绝策略"));
        question.setGenerationContextJson(Map.of("domainCode", "concurrency"));

        when(attemptMapper.selectByAttemptId("attempt-1")).thenReturn(null);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(questionMapper.selectById(questionId)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        Resume resume = new Resume();
        resume.setId(7001L);
        resume.setParsedText("候选人主做高并发服务，熟悉线程池和锁优化。");
        when(resumeMapper.selectById(7001L)).thenReturn(resume);

        EvaluationDecisionOutput evalOutput = EvaluationDecisionOutput.builder()
                .answerAssessment("当前轮证据足够，结束面试。")
                .answerVerdict("STRONG")
                .decision("wrapup")
                .targetFocus("综合收束")
                .targetAngle("role")
                .difficultyAdjustment("same")
                .nextQuestionGoal("结束当前面试")
                .domainOutcome("covered")
                .build();
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder().output(evalOutput).build()
        );

        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(100L)
                        .attemptId("attempt-1")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("wrapup")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(questionId);
        request.setAttemptId("attempt-1");
        request.setAnswerText("我会先说明核心参数，再给出调优方案。");
        request.setIsFinal(true);

        service.submitAnswer(sessionId, userId, request);

        ArgumentCaptor<EvaluationDecisionInput> captor = ArgumentCaptor.forClass(EvaluationDecisionInput.class);
        verify(aiClient).callEvaluationDecision(captor.capture());
        assertThat(captor.getValue().getExpectedPoints()).containsExactly("corePoolSize", "拒绝策略");
        assertThat(captor.getValue().getResumeText()).isEqualTo("候选人主做高并发服务，熟悉线程池和锁优化。");
    }
}
