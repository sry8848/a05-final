package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerSubmitPersistenceServiceTest {

    @Test
    void persist_shouldPublishEventWhenIsFinalTrue() {
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        StateLedgerPatchService patchService = mock(StateLedgerPatchService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AnswerSubmitPersistenceService service = new AnswerSubmitPersistenceService(
                attemptMapper, questionMapper, sessionMapper, patchService, eventPublisher
        );

        doAnswer(invocation -> {
            InterviewAttempt attempt = invocation.getArgument(0);
            attempt.setId(88L);
            return 1;
        }).when(attemptMapper).insert(any(InterviewAttempt.class));

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-1");
        request.setAnswerText("answer");
        request.setIsFinal(true);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setQuestionType("PRINCIPLE");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .answerAssessment("回答基本可用，下一步补一个并发边界点。")
                .answerVerdict("PARTIAL")
                .decision("broaden")
                .targetFocus("线程池参数")
                .targetAngle("implementation")
                .difficultyAdjustment("same")
                .nextQuestionGoal("补一个并发基础点")
                .questionType("PRINCIPLE")
                .focusPoint("线程池参数")
                .domainOutcome("continue")
                .statePatch(Map.of())
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewAttempt> attemptCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getDetailEvaluationStatus()).isEqualTo("pending");
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(patchService).applyReduction(1L, output, question, "attempt-1", 2L, "answer");
    }

    @Test
    void persist_shouldNotPublishEventWhenIsFinalFalse() {
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        StateLedgerPatchService patchService = mock(StateLedgerPatchService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AnswerSubmitPersistenceService service = new AnswerSubmitPersistenceService(
                attemptMapper, questionMapper, sessionMapper, patchService, eventPublisher
        );

        when(attemptMapper.insert(any())).thenReturn(1);
        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-2");
        request.setAnswerText("draft");
        request.setIsFinal(false);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(3L);
        question.setQuestionType("PRINCIPLE");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .answerAssessment("回答较弱，需要降阶补救。")
                .answerVerdict("WEAK")
                .decision("rescue")
                .targetFocus("缓存击穿")
                .targetAngle("implementation")
                .difficultyAdjustment("down")
                .nextQuestionGoal("继续同主题验证基础实现")
                .questionType("PRINCIPLE")
                .focusPoint("缓存击穿")
                .domainOutcome("continue")
                .statePatch(Map.of())
                .build();

        service.persist(1L, question, request, output);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(patchService).applyReduction(1L, output, question, "attempt-2", 3L, "draft");
    }

    @Test
    void persist_shouldMarkQuestionSkippedWhenAnswerIsSkip() {
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        StateLedgerPatchService patchService = mock(StateLedgerPatchService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AnswerSubmitPersistenceService service = new AnswerSubmitPersistenceService(
                attemptMapper, questionMapper, sessionMapper, patchService, eventPublisher
        );

        when(attemptMapper.insert(any())).thenReturn(1);

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-3");
        request.setAnswerText("[skip]");
        request.setIsFinal(true);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(5L);
        question.setQuestionType("PRINCIPLE");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .answerAssessment("当前题跳过，切到下一个更高价值域。")
                .answerVerdict("WEAK")
                .decision("broaden")
                .targetFocus("事务边界")
                .targetAngle("implementation")
                .difficultyAdjustment("same")
                .nextQuestionGoal("补一个 MySQL 事务边界基础题")
                .questionType("PRINCIPLE")
                .focusPoint("事务边界")
                .domainOutcome("circuit_broken")
                .statePatch(Map.of())
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("skipped");
    }

    @Test
    void persist_shouldStoreNewDecisionSnapshotAndReducerAudit() {
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        StateLedgerPatchService patchService = mock(StateLedgerPatchService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AnswerSubmitPersistenceService service = new AnswerSubmitPersistenceService(
                attemptMapper, questionMapper, sessionMapper, patchService, eventPublisher
        );

        when(attemptMapper.insert(any())).thenReturn(1);
        when(patchService.applyReduction(org.mockito.ArgumentMatchers.eq(1L), any(EvaluationDecisionOutput.class), any(InterviewQuestion.class), any(String.class), any(Long.class), any(String.class)))
                .thenReturn(StateLedgerPatchService.ReductionAudit.builder()
                        .newLedger(Map.of("asked_total", 1))
                        .diff(Map.of("asked_total", 1, "last_attempt_id", "attempt-4"))
                        .domainClosureReason("DEPTH_REACHED")
                        .build());

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-4");
        request.setAnswerText("answer");
        request.setIsFinal(true);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(7L);
        question.setQuestionType("INTRO");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .answerAssessment("开场回答有效，已形成项目锚点。")
                .answerVerdict("STRONG")
                .decision("probe")
                .targetFocus("集合框架")
                .targetAngle("implementation")
                .difficultyAdjustment("same")
                .nextQuestionGoal("从项目切到 Java 基础点验证")
                .nextDomainId(1L)
                .nextDomainCode("java_core")
                .nextDomainName("Java 核心基础")
                .questionType("PRINCIPLE")
                .focusPoint("集合框架")
                .domainOutcome("covered")
                .retrievalIntent(EvaluationDecisionOutput.RetrievalIntent.builder()
                        .domainHint("java_core")
                        .focusQuery("集合框架 ArrayList LinkedList")
                        .questionTypeHint("PRINCIPLE")
                        .avoidRecentFamilies(List.of("intro.project.anchor"))
                        .build())
                .statePatch(Map.of(
                        "activeProjectId", "p_order",
                        "coveredPointsAdd", List.of("intro:project_anchor")
                ))
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewAttempt> attemptCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getEvaluationJson())
                .containsEntry("decision", "probe")
                .containsEntry("answerVerdict", "STRONG")
                .containsEntry("domainOutcome", "covered")
                .containsEntry("focusPoint", "集合框架")
                .containsKey("ledgerDiff")
                .containsKey("retrievalIntent")
                .containsKey("statePatch")
                .doesNotContainKey("nextStrategy");
    }

    @Test
    void persist_shouldStoreRawAsrTextAndCorrectionChangesInEvaluationSnapshot() {
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        StateLedgerPatchService patchService = mock(StateLedgerPatchService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AnswerSubmitPersistenceService service = new AnswerSubmitPersistenceService(
                attemptMapper, questionMapper, sessionMapper, patchService, eventPublisher
        );

        when(attemptMapper.insert(any())).thenReturn(1);

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-5");
        request.setAnswerText("Java后端项目");
        request.setRawAsrText("ja法后端项目");
        request.setAsrCorrectionChanges(List.of(Map.of(
                "from", "ja法",
                "to", "Java",
                "reason", "technical_term"
        )));
        request.setIsFinal(true);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(8L);
        question.setQuestionType("PRINCIPLE");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .answerAssessment("回答正常。")
                .answerVerdict("STRONG")
                .decision("broaden")
                .targetFocus("事务边界")
                .targetAngle("implementation")
                .difficultyAdjustment("same")
                .nextQuestionGoal("切到下一个基础域")
                .questionType("PRINCIPLE")
                .focusPoint("事务边界")
                .domainOutcome("continue")
                .statePatch(Map.of())
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewAttempt> attemptCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getEvaluationJson())
                .containsEntry("rawAsrText", "ja法后端项目")
                .containsKey("asrCorrectionChanges");
    }
}
