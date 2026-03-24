package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.interview.engine.QuotaStateSupport;

/**
 * 策略限额键。
 */
public enum StrategyLimit {
    SAME_POINT_CONTINUE(QuotaStateSupport.SAME_POINT_CONTINUE, 20),
    SAME_DOMAIN_CONTINUE(QuotaStateSupport.SAME_DOMAIN_CONTINUE, 20),
    SAME_PROJECT_POINT_CONTINUE(QuotaStateSupport.SAME_PROJECT_POINT_CONTINUE, 20),
    SAME_PROJECT_CONTINUE(QuotaStateSupport.SAME_PROJECT_CONTINUE, 20),
    PRINCIPLE_TOTAL(QuotaStateSupport.PRINCIPLE_TOTAL, 20),
    PROJECT_TOTAL(QuotaStateSupport.PROJECT_TOTAL, 20),
    SCENARIO_TOTAL(QuotaStateSupport.SCENARIO_TOTAL, 20),
    BEHAVIORAL_TOTAL(QuotaStateSupport.BEHAVIORAL_TOTAL, 20);

    private final String ledgerKey;
    private final int maxCount;

    StrategyLimit(String ledgerKey, int maxCount) {
        this.ledgerKey = ledgerKey;
        this.maxCount = maxCount;
    }

    public String ledgerKey() {
        return ledgerKey;
    }

    public int maxCount() {
        return maxCount;
    }
}
