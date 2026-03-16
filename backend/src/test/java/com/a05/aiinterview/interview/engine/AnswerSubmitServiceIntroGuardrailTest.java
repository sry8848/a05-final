package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerSubmitServiceIntroGuardrailTest {

    @Test
    void submitAnswer_shouldEnforceFreshGradIntroGuardrails() {
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
        Long userId = 2L;
        Long questionId = 100L;

        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("in_progress");
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setMode("practice");
        session.setCurrentQuestionNo(1);
        session.setContextWindowSize(5);
        session.setStateLedgerJson(Map.of(
                "domain_states", List.of(
                        Map.of(
                                "domain_id", "java_core",
                                "status", "UNASKED",
                                "current_depth", "",
                                "saturated", false,
                                "evidence_refs", List.of()
                        ),
                        Map.of(
                                "domain_id", "mq",
                                "status", "UNASKED",
                                "current_depth", "",
                                "saturated", false,
                                "evidence_refs", List.of()
                        )
                ),
                "question_mix_progress", Map.of("INTRO", 0, "PROJECT_DEEP_DIVE", 0, "PRINCIPLE", 0),
                "asked_total", 0
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainId", 8L,
                                "domainCode", "mq",
                                "domainName", "消息队列",
                                "targetDepth", "L3",
                                "focusPoints", List.of("RabbitMQ 延迟消息")
                        )
                ),
                "questionMixPlan", Map.of("INTRO", 1, "PROJECT_DEEP_DIVE", 4, "PRINCIPLE", 7)
        ));

        InterviewQuestion question = new InterviewQuestion();
        question.setId(questionId);
        question.setSessionId(sessionId);
        question.setQuestionType("INTRO");
        question.setDomainId(null);
        question.setTargetDepth("L1");
        question.setStem("请做一下自我介绍。");
        question.setExpectedPoints(List.of("技术方向", "项目经历"));
        question.setGenerationContextJson(Map.of("domainCode", "intro"));

        when(attemptMapper.selectByAttemptId("attempt-intro-1")).thenReturn(null);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(questionMapper.selectById(questionId)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());

        EvaluationDecisionOutput aiOutput = EvaluationDecisionOutput.builder()
                .passCurrentLevel(true)
                .deepen(true)
                .signal("DEEPEN")
                .reasoning("开场题回答合格，建议继续沿项目深挖。")
                .nextStrategy(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                        .nextDomainId(8L)
                        .nextDomainCode("mq")
                        .nextDomainName("消息队列")
                        .questionType("PROJECT_DEEP_DIVE")
                        .targetDepth("L3")
                        .difficulty("L4")
                        .targetSkill("RabbitMQ 与 Seata 协同机制")
                        .focusPoint("RabbitMQ 与 Seata")
                        .expectedPoints(List.of(
                                "解释 RabbitMQ 延迟消息方案",
                                "对比 Seata AT 和 TCC 方案"
                        ))
                        .build())
                .build();
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder().output(aiOutput).build()
        );

        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(10L)
                        .attemptId("attempt-intro-1")
                        .isFinal(true)
                        .shouldEnd(false)
                        .evaluationSignal("NEXT_DOMAIN")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(questionId);
        request.setAttemptId("attempt-intro-1");
        request.setAnswerText("我是 2027 届毕业生，主要做 Java 后端。");
        request.setIsFinal(true);

        service.submitAnswer(sessionId, userId, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persistedEval = captor.getValue();

        assertThat(persistedEval.isDeepen()).isFalse();
        assertThat(persistedEval.getSignal()).isEqualTo("NEXT_DOMAIN");
        assertThat(persistedEval.getNextStrategy()).isNotNull();
        assertThat(persistedEval.getNextStrategy().getDifficulty()).isEqualTo("L2");
        assertThat(persistedEval.getNextStrategy().getTargetDepth()).isEqualTo("L2");
        assertThat(persistedEval.getNextStrategy().getTargetSkill()).contains("RabbitMQ");
        assertThat(persistedEval.getNextStrategy().getTargetSkill()).doesNotContain("Seata");
        assertThat(persistedEval.getNextStrategy().getExpectedPoints())
                .allMatch(point -> point.contains("RabbitMQ"));
    }
}
