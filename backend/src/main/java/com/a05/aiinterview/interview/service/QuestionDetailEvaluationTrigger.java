package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.event.FinalAttemptPersistedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * final attempt 提交后触发单题详细评估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionDetailEvaluationTrigger {

    private final QuestionDetailEvaluationService questionDetailEvaluationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinalAttemptPersisted(FinalAttemptPersistedEvent event) {
        if (event == null || event.attemptDbId() == null) {
            return;
        }
        log.info("收到 final attempt 提交后事件，触发单题详细评估, sessionId={}, questionId={}, attemptId={}",
                event.sessionId(), event.questionId(), event.attemptId());
        questionDetailEvaluationService.evaluateByAttemptId(event.attemptDbId());
    }
}

