package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.interview.engine.QuotaStateSupport;

/**
 * 策略限额键。
 */
public enum StrategyLimit {
    SAME_POINT_CONTINUE(QuotaStateSupport.SAME_POINT_CONTINUE),
    SAME_DOMAIN_CONTINUE(QuotaStateSupport.SAME_DOMAIN_CONTINUE),
    SAME_PROJECT_POINT_CONTINUE(QuotaStateSupport.SAME_PROJECT_POINT_CONTINUE),
    SAME_PROJECT_CONTINUE(QuotaStateSupport.SAME_PROJECT_CONTINUE),
    PRINCIPLE_TOTAL(QuotaStateSupport.PRINCIPLE_TOTAL),
    PROJECT_TOTAL(QuotaStateSupport.PROJECT_TOTAL),
    SCENARIO_TOTAL(QuotaStateSupport.SCENARIO_TOTAL),
    BEHAVIORAL_TOTAL(QuotaStateSupport.BEHAVIORAL_TOTAL);

    private final String ledgerKey;

    StrategyLimit(String ledgerKey) {
        this.ledgerKey = ledgerKey;
    }

    public String ledgerKey() {
        return ledgerKey;
    }
}
