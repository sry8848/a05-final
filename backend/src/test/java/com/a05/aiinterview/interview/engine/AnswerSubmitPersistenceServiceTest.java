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
                .passCurrentLevel(true)
                .deepen(false)
                .signal("NEXT_DOMAIN")
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
                .passCurrentLevel(false)
                .deepen(false)
                .signal("NEXT_DOMAIN")
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
                .passCurrentLevel(false)
                .deepen(false)
                .signal("NEXT_DOMAIN")
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("skipped");
    }

    @Test
    void persist_shouldStoreAiDecisionAndReducerAuditSnapshot() {
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
                        .newLedger(java.util.Map.of("asked_total", 1))
                        .diff(java.util.Map.of("asked_total", 1, "last_attempt_id", "attempt-4"))
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
                .passCurrentLevel(true)
                .deepen(false)
                .signal("NEXT_DOMAIN")
                .reasoning("intro passed")
                .nextStrategy(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                        .nextDomainId(1L)
                        .nextDomainCode("java_core")
                        .nextDomainName("Java 核心基础")
                        .questionType("PRINCIPLE")
                        .targetDepth("L2")
                        .difficulty("L2")
                        .targetSkill("集合框架")
                        .expectedPoints(List.of("说明 ArrayList 与 LinkedList 区别"))
                        .focusPoint("集合框架")
                        .build())
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewAttempt> attemptCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getEvaluationJson())
                .containsEntry("signal", "NEXT_DOMAIN")
                .containsEntry("passCurrentLevel", true)
                .containsEntry("deepen", false)
                .containsEntry("domainClosureReason", "DEPTH_REACHED")
                .containsKey("ledgerDiff")
                .containsKey("nextStrategy");
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
                .passCurrentLevel(true)
                .deepen(false)
                .signal("NEXT_DOMAIN")
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewAttempt> attemptCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getEvaluationJson())
                .containsEntry("rawAsrText", "ja法后端项目")
                .containsKey("asrCorrectionChanges");
    }
}
