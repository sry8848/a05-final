package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.common.enums.DomainStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认状态账本归约器（State Ledger Reducer）。
 *
 * <p>核心设计思想（类似 Redux Reducer）：
 * <ul>
 *   <li>不可变更新：深拷贝旧账本，不会修改原始对象</li>
 *   <li>只维护最小运行态：当前焦点、当前 item、已覆盖点、知识域状态</li>
 *   <li>纯函数：相同输入总是产生相同输出，无副作用</li>
 * </ul>
 *
 * <p>更新内容：
 * <ul>
 *   <li>current_focus / active_item_*：更新当前焦点和激活项</li>
 *   <li>covered_points：合并新覆盖的知识点</li>
 *   <li>covered_domains：合并新覆盖的知识域</li>
 *   <li>recent_question_families：记录最近题目的家族ID，避免重复考察</li>
 *   <li>domain_states：更新知识域状态（unasked → in_progress → covered）</li>
 * </ul>
 */
@Component
public class DefaultStateLedgerReducer implements StateLedgerReducer {

    /**
     * 计算新账本的主方法（纯函数，无副作用）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>深拷贝旧账本（确保不可变更新）</li>
     *   <li>设置 last_attempt_id</li>
     *   <li>更新当前焦点和激活项（current_focus、active_item_*）</li>
     *   <li>合并新覆盖的知识点（covered_points）</li>
     *   <li>合并题目家族ID（recent_question_families）</li>
     *   <li>合并新覆盖的知识域（covered_domains）</li>
     *   <li>更新知识域状态（domain_states）</li>
     *   <li>返回新账本</li>
     * </ol>
     *
     * @param oldLedger 旧账本
     * @param mutation 变更指令
     * @param attemptId 作答记录 ID
     * @param evidenceQuestionId 证据题目 ID
     * @return 新账本
     */
    @Override
    public Map<String, Object> reduce(Map<String, Object> oldLedger,
                                      LedgerMutation mutation,
                                      String attemptId,
                                      Long evidenceQuestionId) {
        // 步骤1：深拷贝旧账本（确保不可变更新，不修改原始对象）
        Map<String, Object> ledger = deepCopyLedger(oldLedger);

        // 步骤2：记录最后一次作答记录 ID
        ledger.put("last_attempt_id", attemptId);

        // 步骤3：更新当前焦点和激活项（next优先，current作为fallback）
        putIfNotBlank(ledger, "current_focus", firstNonBlank(mutation.getNextFocus(), mutation.getCurrentFocus()));
        putIfNotBlank(ledger, "active_item_key", firstNonBlank(mutation.getNextItemKey(), mutation.getCurrentItemKey()));
        putIfNotBlank(ledger, "active_item_type", firstNonBlank(mutation.getNextItemType(), mutation.getCurrentItemType()));
        putIfNotBlank(ledger, "active_item_name", firstNonBlank(mutation.getNextItemName(), mutation.getCurrentItemName()));

        // 步骤4：合并新覆盖的知识点（去重）
        mergeStringListField(ledger, "covered_points", mutation.getNewCoveredPoints());

        // 步骤5：合并题目家族ID（用于避免近亲重复）
        mergeQuestionFamily(ledger, mutation.getQuestionFamilyId());

        // 步骤6：合并新覆盖的知识域
        mergeCoveredDomains(ledger, mutation.getNewCoveredDomains());

        // 步骤7：更新知识域状态（domain_states）
        updateDomainStates(ledger, mutation, evidenceQuestionId);

        // 步骤8：返回新账本
        return ledger;
    }

    /**
     * 合并题目家族ID到 recent_question_families 列表（去重）。
     *
     * <p>题目家族ID的作用：
     * <ul>
     *   <li>避免考察"近亲"题目（相似题型+相似焦点）</li>
     *   <li>例如：连续问两个"HashMap原理"相关的题目属于近亲重复</li>
     *   <li>使用 LinkedHashSet 保证顺序并去重</li>
     * </ul>
     *
     * <p>家族ID的格式：{题型}.{焦点}，例如 "PRINCIPLE.HashMap扩容机制"
     *
     * @param ledger 账本对象（会被直接修改）
     * @param questionFamilyId 题目家族ID
     */
    private void mergeQuestionFamily(Map<String, Object> ledger, String questionFamilyId) {
        if (questionFamilyId == null || questionFamilyId.isBlank()) {
            return;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>(toStringList(ledger.get("recent_question_families")));
        values.add(questionFamilyId.trim());
        ledger.put("recent_question_families", new ArrayList<>(values));
    }

    /**
     * 合并新覆盖的知识域到 covered_domains 列表（按 domainCode 去重）。
     *
     * <p>知识域覆盖的意义：
     * <ul>
     *   <li>表示该知识域已形成初步判断，无需继续深入</li>
     *   <li>用于后续策略筛选时避免重复考察已覆盖的域</li>
     *   <li>按 domainCode 去重，确保同一知识域不会被重复记录</li>
     * </ul>
     *
     * <p>数据结构：
     * <pre>{@code
     * [
     *   {"domainCode": "JAVA_COLLECTIONS", "domainName": "Java集合框架"},
     *   {"domainCode": "CONCURRENT", "domainName": "并发编程"}
     * ]
     * }</pre>
     *
     * @param ledger 账本对象（会被直接修改）
     * @param coveredDomains 新覆盖的知识域列表
     */
    private void mergeCoveredDomains(Map<String, Object> ledger,
                                     List<LedgerMutation.CoveredDomainByCode> coveredDomains) {
        if (coveredDomains == null || coveredDomains.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> values = toCoveredDomainMap(ledger.get("covered_domains"));
        for (LedgerMutation.CoveredDomainByCode coveredDomain : coveredDomains) {
            if (coveredDomain == null || coveredDomain.getDomainCode() == null || coveredDomain.getDomainCode().isBlank()) {
                continue;
            }
            values.put(coveredDomain.getDomainCode().trim(), buildCoveredDomainEntry(
                    coveredDomain.getDomainCode(),
                    coveredDomain.getDomainName()
            ));
        }
        ledger.put("covered_domains", new ArrayList<>(values.values()));
    }

    /**
     * 更新知识域状态（domain_states 数组）。
     *
     * <p>更新逻辑：
     * <ul>
     *   <li>如果是当前知识域且不是跳过，状态变为 in_progress</li>
     *   <li>如果知识域被标记为新覆盖，状态变为 covered，saturated=true</li>
     *   <li>添加证据题目ID到 evidenceRefs</li>
     * </ul>
     *
     * @param ledger 账本
     * @param mutation 变更指令
     * @param evidenceQuestionId 证据题目ID
     */
    @SuppressWarnings("unchecked")
    private void updateDomainStates(Map<String, Object> ledger,
                                    LedgerMutation mutation,
                                    Long evidenceQuestionId) {
        Object rawStates = ledger.get("domain_states");
        if (!(rawStates instanceof List<?> states)) {
            return;
        }
        List<Map<String, Object>> copiedStates = new ArrayList<>();
        for (Object stateObj : states) {
            if (stateObj instanceof Map<?, ?> state) {
                copiedStates.add(new LinkedHashMap<>((Map<String, Object>) state));
            }
        }

        for (Map<String, Object> state : copiedStates) {
            String domainCode = asString(state.get("domainCode"));

            // 情况1：当前知识域，添加证据，状态变为 in_progress（如果不是跳过）
            if (Objects.equals(domainCode, mutation.getCurrentDomainCode()) && evidenceQuestionId != null) {
                LinkedHashSet<Long> refs = new LinkedHashSet<>(toLongList(state.get("evidenceRefs")));
                refs.add(evidenceQuestionId);
                state.put("evidenceRefs", new ArrayList<>(refs));
                if (!isCovered(state) && !mutation.isSkipCurrentQuestion()) {
                    state.put("status", DomainStatus.IN_PROGRESS.getValue());
                    state.put("saturated", false);
                }
            }

            // 情况2：知识域被标记为新覆盖，状态变为 covered，saturated=true
            if (mutation.getNewCoveredDomains() == null) {
                continue;
            }
            for (LedgerMutation.CoveredDomainByCode coveredDomain : mutation.getNewCoveredDomains()) {
                if (coveredDomain == null) {
                    continue;
                }
                if (Objects.equals(domainCode, coveredDomain.getDomainCode())) {
                    state.put("status", DomainStatus.COVERED.getValue());
                    state.put("saturated", true);
                }
            }
        }

        ledger.put("domain_states", copiedStates);
    }

    private boolean isCovered(Map<String, Object> state) {
        return DomainStatus.COVERED.getValue().equalsIgnoreCase(asString(state.get("status")));
    }

    private void mergeStringListField(Map<String, Object> ledger, String key, List<String> additions) {
        LinkedHashSet<String> values = new LinkedHashSet<>(toStringList(ledger.get(key)));
        if (additions != null) {
            additions.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(values::add);
        }
        ledger.put(key, new ArrayList<>(values));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyLedger(Map<String, Object> ledger) {
        Map<String, Object> copy = ledger == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ledger);
        copy.put("covered_domains", new ArrayList<>(toCoveredDomainMap(copy.get("covered_domains")).values()));
        copy.put("covered_points", new ArrayList<>(toStringList(copy.get("covered_points"))));
        copy.put("recent_question_families", new ArrayList<>(toStringList(copy.get("recent_question_families"))));
        copy.put("quota_state", new LinkedHashMap<>(QuotaStateSupport.ensureQuotaState(copy, List.of())));
        copy.put("decision_fallback_state", new LinkedHashMap<>(DecisionFallbackStateSupport.ensureState(copy)));
        Object domainStates = copy.get("domain_states");
        if (domainStates instanceof List<?> rawDomainStates) {
            List<Map<String, Object>> cloned = new ArrayList<>();
            for (Object item : rawDomainStates) {
                if (item instanceof Map<?, ?> map) {
                    cloned.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            copy.put("domain_states", cloned);
        } else {
            copy.put("domain_states", new ArrayList<>());
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> toCoveredDomainMap(Object value) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!(value instanceof List<?> rawList)) {
            return result;
        }
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) rawMap);
                String domainCode = asString(map.get("domainCode")).trim();
                if (domainCode.isBlank()) {
                    continue;
                }
                result.put(domainCode, buildCoveredDomainEntry(domainCode, asString(map.get("domainName"))));
            }
        }
        return result;
    }

    private Map<String, Object> buildCoveredDomainEntry(String domainCode, String domainName) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("domainCode", domainCode == null ? "" : domainCode.trim());
        entry.put("domainName", domainName == null ? "" : domainName.trim());
        return entry;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : rawList) {
            Long longValue = toLong(item);
            if (longValue != null) {
                result.add(longValue);
            }
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int toInt(Object value) {
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
