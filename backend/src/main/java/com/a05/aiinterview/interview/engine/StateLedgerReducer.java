package com.a05.aiinterview.interview.engine;

import java.util.Map;

/**
 * 根据旧账本和最小变更决策，计算新账本。
 */
public interface StateLedgerReducer {

    Map<String, Object> reduce(Map<String, Object> oldLedger,
                               LedgerMutation mutation,
                               String attemptId,
                               Long evidenceQuestionId);
}
