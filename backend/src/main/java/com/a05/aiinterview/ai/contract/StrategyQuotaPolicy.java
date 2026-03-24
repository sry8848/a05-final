package com.a05.aiinterview.ai.contract;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 策略执行后的 quota 更新语义。
 */
public record StrategyQuotaPolicy(
        Set<StrategyLimit> increments,
        Set<StrategyLimit> resets,
        boolean clearSourceContinuousCounters
) {

    public StrategyQuotaPolicy {
        increments = Set.copyOf(increments == null ? Set.of() : new LinkedHashSet<>(increments));
        resets = Set.copyOf(resets == null ? Set.of() : new LinkedHashSet<>(resets));
    }

    public static StrategyQuotaPolicy incrementOnly(StrategyLimit... limits) {
        return new StrategyQuotaPolicy(Set.of(limits), Set.of(), false);
    }

    public static StrategyQuotaPolicy incrementAndReset(Set<StrategyLimit> increments,
                                                        Set<StrategyLimit> resets) {
        return new StrategyQuotaPolicy(increments, resets, false);
    }

    public static StrategyQuotaPolicy enter(Set<StrategyLimit> increments) {
        return new StrategyQuotaPolicy(increments, Set.of(), true);
    }

    public static StrategyQuotaPolicy none() {
        return new StrategyQuotaPolicy(Set.of(), Set.of(), false);
    }
}
