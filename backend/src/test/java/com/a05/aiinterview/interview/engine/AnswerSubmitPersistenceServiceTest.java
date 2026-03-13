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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .signal("NEXT_DOMAIN")
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewAttempt> attemptCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertEquals("pending", attemptCaptor.getValue().getDetailEvaluationStatus());
        verify(eventPublisher).publishEvent(any(Object.class));
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

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .signal("NEXT_DOMAIN")
                .build();

        service.persist(1L, question, request, output);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
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

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .signal("NEXT_DOMAIN")
                .build();

        service.persist(1L, question, request, output);

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionMapper).updateById(captor.capture());
        assertEquals("skipped", captor.getValue().getStatus());
    }
}
