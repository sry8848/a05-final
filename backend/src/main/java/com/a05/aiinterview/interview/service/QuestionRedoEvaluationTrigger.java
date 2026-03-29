package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.event.QuestionRedoAttemptPersistedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 单题重答提交后触发详细评估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRedoEvaluationTrigger {

    private final QuestionRedoEvaluationService questionRedoEvaluationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestionRedoAttemptPersisted(QuestionRedoAttemptPersistedEvent event) {
        if (event == null || event.redoAttemptId() == null) {
            return;
        }
        log.info("收到单题重答提交后事件，触发详细评估, redoAttemptId={}, sessionId={}, questionId={}",
                event.redoAttemptId(), event.sourceSessionId(), event.sourceQuestionId());
        questionRedoEvaluationService.evaluateByRedoAttemptId(event.redoAttemptId());
    }
}
