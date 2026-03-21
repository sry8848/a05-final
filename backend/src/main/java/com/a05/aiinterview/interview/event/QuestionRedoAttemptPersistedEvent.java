package com.a05.aiinterview.interview.event;

/**
 * 单题重答持久化完成事件。
 */
public record QuestionRedoAttemptPersistedEvent(
        Long redoAttemptId,
        Long sourceSessionId,
        Long sourceQuestionId,
        Long userId
) {
}
