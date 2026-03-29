package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.event.FinalAttemptPersistedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QuestionDetailEvaluationTriggerTest {

    @Test
    void onFinalAttemptPersisted_shouldDelegateToService() {
        QuestionDetailEvaluationService service = mock(QuestionDetailEvaluationService.class);
        QuestionDetailEvaluationTrigger trigger = new QuestionDetailEvaluationTrigger(service);

        trigger.onFinalAttemptPersisted(new FinalAttemptPersistedEvent(1L, 2L, 3L, "attempt-1"));

        verify(service).evaluateByAttemptId(3L);
    }

    @Test
    void onFinalAttemptPersisted_shouldBeAfterCommitAndAsync() throws Exception {
        Method method = QuestionDetailEvaluationTrigger.class
                .getMethod("onFinalAttemptPersisted", FinalAttemptPersistedEvent.class);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
        Async async = method.getAnnotation(Async.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
        org.junit.jupiter.api.Assertions.assertNotNull(async);
    }
}

