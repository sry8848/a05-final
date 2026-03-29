package com.a05.aiinterview.interview.engine;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 会话级面试官画像支持工具。
 * 画像只影响措辞风格，不影响题目内容、焦点或评分。
 */
public final class InterviewerArchetypeSupport {

    public static final String LEDGER_KEY = "interviewer_archetype";
    public static final String DEFAULT_ARCHETYPE = "efficiency";
    private static final List<String> ARCHETYPES = List.of(
            DEFAULT_ARCHETYPE,
            "guiding",
            "stress"
    );

    private InterviewerArchetypeSupport() {
    }

    public static String chooseForSession(Long sessionId) {
        if (sessionId == null) {
            return DEFAULT_ARCHETYPE;
        }
        int index = Math.floorMod(Long.hashCode(sessionId), ARCHETYPES.size());
        return ARCHETYPES.get(index);
    }

    public static String resolveFromLedger(Map<String, Object> ledger) {
        if (ledger == null) {
            return DEFAULT_ARCHETYPE;
        }
        Object raw = ledger.get(LEDGER_KEY);
        if (!(raw instanceof String value)) {
            return DEFAULT_ARCHETYPE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return ARCHETYPES.contains(normalized) ? normalized : DEFAULT_ARCHETYPE;
    }
}
