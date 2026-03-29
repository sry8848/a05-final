package com.a05.aiinterview.interview.event;

/**
 * final attempt 持久化完成事件。
 * 仅在事务提交后用于触发单题详细评估。
 */
public record FinalAttemptPersistedEvent(
        Long sessionId,
        Long questionId,
        Long attemptDbId,
        String attemptId
) {
}

