package com.a05.aiinterview.interview.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 决策回退状态支持工具类，负责管理状态账本中的 decision_fallback_state 字段。
 *
 * <p>decision_fallback_state 是状态账本的一个子字段，用于跟踪系统兜底策略的使用情况。
 * 该状态在系统兜底时被更新，用于控制兜底次数和轮询策略。
 *
 * <p>状态字段说明：
 * <ul>
 *   <li>rotationIndex：轮询索引，用于在多个兜底行为维度间轮询选择</li>
 *   <li>consecutiveFallbackCount：连续兜底次数，用于判断是否需要结束面试</li>
 *   <li>totalFallbackCount：总兜底次数，用于限制总的兜底次数</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>第一级验证失败 → 第二级修复 → 第三级兜底</li>
 *   <li>每次兜底时更新状态</li>
 *   <li>AI 决策恢复正常时重置连续计数</li>
 * </ul>
 */
public final class DecisionFallbackStateSupport {

    /**
     * 状态账本中存储回退状态的键名。
     *
     * <p>完整路径：state_ledger_json.decision_fallback_state
     */
    public static final String LEDGER_KEY = "decision_fallback_state";

    /** 回退状态字段：轮询索引（用于行为维度轮询） */
    public static final String ROTATION_INDEX = "rotationIndex";

    /** 回退状态字段：连续回退次数（用于判断是否需要结束） */
    public static final String CONSECUTIVE_FALLBACK_COUNT = "consecutiveFallbackCount";

    /** 回退状态字段：总回退次数（用于限制总的回退次数） */
    public static final String TOTAL_FALLBACK_COUNT = "totalFallbackCount";

    private DecisionFallbackStateSupport() {
    }

    /**
     * 创建初始的回退状态。
     *
     * <p>初始值：
     * <ul>
     *   <li>rotationIndex = 0</li>
     *   <li>consecutiveFallbackCount = 0</li>
     *   <li>totalFallbackCount = 0</li>
     * </ul>
     *
     * @return 初始回退状态 Map
     */
    public static Map<String, Object> initialState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(ROTATION_INDEX, 0);
        state.put(CONSECUTIVE_FALLBACK_COUNT, 0);
        state.put(TOTAL_FALLBACK_COUNT, 0);
        return state;
    }

    /**
     * 确保状态账本中存在有效的回退状态。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>如果账本为空，返回初始状态</li>
     *   <li>如果账本中没有 decision_fallback_state，返回初始状态</li>
     *   <li>如果存在，从账本中提取并返回</li>
     * </ol>
     *
     * <p>安全性保证：
     * <ul>
     *   <li>使用新创建的 Map 副本，避免修改原账本</li>
     *   <li>只提取存在的字段，缺失的字段使用初始值</li>
     * </ul>
     *
     * @param ledger 状态账本
     * @return 有效的回退状态
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> ensureState(Map<String, Object> ledger) {
        Map<String, Object> state = initialState();
        if (ledger == null) {
            return state;
        }
        Object raw = ledger.get(LEDGER_KEY);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>((Map<String, Object>) map);
            overwrite(state, copied, ROTATION_INDEX);
            overwrite(state, copied, CONSECUTIVE_FALLBACK_COUNT);
            overwrite(state, copied, TOTAL_FALLBACK_COUNT);
        }
        return state;
    }

    /**
     * 预览执行兜底后的状态（不修改原账本）。
     *
     * <p>用于在决定是否结束面试之前，预览如果执行兜底会是什么状态。
     *
     * <p>预览变化：
     * <ul>
     *   <li>rotationIndex += 1（轮询索引递增）</li>
     *   <li>consecutiveFallbackCount += 1（连续兜底次数+1）</li>
     *   <li>totalFallbackCount += 1（总兜底次数+1）</li>
     * </ul>
     *
     * @param ledger 当前状态账本
     * @return 预览后的回退状态
     */
    public static Map<String, Object> previewAfterFallback(Map<String, Object> ledger) {
        Map<String, Object> state = ensureState(ledger);
        state.put(ROTATION_INDEX, toInt(state.get(ROTATION_INDEX)) + 1);
        state.put(CONSECUTIVE_FALLBACK_COUNT, toInt(state.get(CONSECUTIVE_FALLBACK_COUNT)) + 1);
        state.put(TOTAL_FALLBACK_COUNT, toInt(state.get(TOTAL_FALLBACK_COUNT)) + 1);
        return state;
    }

    /**
     * 应用执行计划到状态账本。
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>如果计划为 null，只确保状态存在</li>
     *   <li>如果计划来自系统兜底（SYSTEM_FALLBACK）：更新所有计数</li>
     *   <li>如果计划是正常继续（CONTINUE）：重置连续计数（AI 恢复正常了！）</li>
     * </ul>
     *
     * <p>计数重置逻辑：
     * <ul>
     *   <li>只有当 AI 决策恢复正常（CONTINUE）时，才重置 consecutiveFallbackCount</li>
     *   <li>这样可以确保：连续多次兜底 → 必须结束面试</li>
     * </ul>
     *
     * @param ledger 状态账本（会被直接修改）
     * @param plan 执行计划
     */
    public static void applyPlan(Map<String, Object> ledger, DecisionExecutionPlan plan) {
        Map<String, Object> state = ensureState(ledger);
        if (plan == null) {
            ledger.put(LEDGER_KEY, state);
            return;
        }
        if (plan.getEffectiveDecisionSource() == DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK) {
            state.put(ROTATION_INDEX, toInt(state.get(ROTATION_INDEX)) + 1);
            state.put(CONSECUTIVE_FALLBACK_COUNT, toInt(state.get(CONSECUTIVE_FALLBACK_COUNT)) + 1);
            state.put(TOTAL_FALLBACK_COUNT, toInt(state.get(TOTAL_FALLBACK_COUNT)) + 1);
        } else if ("CONTINUE".equalsIgnoreCase(plan.getInterviewAction())) {
            state.put(CONSECUTIVE_FALLBACK_COUNT, 0);
        }
        ledger.put(LEDGER_KEY, state);
    }

    /**
     * 判断在预览状态下，执行兜底后是否应该结束面试。
     *
     * <p>结束条件（满足任意一个）：
     * <ul>
     *   <li>连续兜底次数 >= 2</li>
     *   <li>总兜底次数 >= 4</li>
     * </ul>
     *
     * <p>设计意图：
     * <ul>
     *   <li>连续兜底 >= 2：说明 AI 连续两次都无法给出有效决策，应该结束</li>
     *   <li>总兜底 >= 4：限制整个面试中兜底的总次数</li>
     * </ul>
     *
     * @param previewState 预览状态（通常是 previewAfterFallback 的结果）
     * @return 是否应该结束面试
     */
    public static boolean shouldTerminateAfterFallback(Map<String, Object> previewState) {
        return toInt(previewState.get(CONSECUTIVE_FALLBACK_COUNT)) >= 2
                || toInt(previewState.get(TOTAL_FALLBACK_COUNT)) >= 4;
    }

    /**
     * 安全地将源 Map 中的值覆盖到目标 Map。
     *
     * <p>只有当源 Map 包含该键时，才会覆盖目标 Map 中的值。
     * 这确保了缺失的字段不会被意外覆盖。
     *
     * @param target 目标 Map（会被修改）
     * @param source 源 Map
     * @param key 键名
     */
    private static void overwrite(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, toInt(source.get(key)));
        }
    }

    /**
     * 安全地将对象转换为 int。
     *
     * <p>转换优先级：
     * <ol>
     *   <li>如果是 Number 类型，直接调用 intValue()</li>
     *   <li>如果是 null，返回 0</li>
     *   <li>其他情况，尝试解析为字符串再转为 int</li>
     * </ol>
     *
     * @param value 待转换的值
     * @return 转换后的 int 值，失败时返回 0
     */
    public static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
