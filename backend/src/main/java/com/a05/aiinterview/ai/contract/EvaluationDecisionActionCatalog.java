package com.a05.aiinterview.ai.contract;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评估决策标准动作目录与组合校验。
 */
public final class EvaluationDecisionActionCatalog {

    public static final String EXIT_CURRENT_TYPE = "退出当前题类";
    public static final String END_INTERVIEW = "结束面试";

    public static final Set<String> PRINCIPLE_ACTIONS = Set.of(
            "引导和验证",
            "深入到强关联点",
            "平移到同知识域知识点",
            "变式",
            "切换知识域",
            EXIT_CURRENT_TYPE
    );

    public static final Set<String> PRACTICAL_ACTIONS = Set.of(
            "收敛并项目外扩",
            "收敛并落回具体知识点",
            "引导还原",
            "真实情景",
            "责任定位",
            "压测",
            "做权衡",
            "兜底与观测",
            "演进与复盘",
            "切换项目要点",
            "切换项目",
            EXIT_CURRENT_TYPE
    );

    public static final Set<String> BEHAVIORAL_ACTIONS = Set.of(
            "引入",
            "要真实事件",
            "问决策过程",
            "问复盘成长",
            "问迁移能力",
            "问协作冲突",
            EXIT_CURRENT_TYPE
    );

    private static final Set<String> ALL_ALLOWED_ACTIONS = buildAllAllowedActions();
    private static final Set<String> CANONICAL_NEXT_TYPES = Set.of(
            "PRINCIPLE",
            "PROJECT_DEEP_DIVE",
            "SCENARIO",
            "BEHAVIORAL"
    );

    private EvaluationDecisionActionCatalog() {
    }

    public static boolean isAllowedAction(String action) {
        return ALL_ALLOWED_ACTIONS.contains(trim(action));
    }

    public static boolean isExitAction(String action) {
        return EXIT_CURRENT_TYPE.equals(trim(action));
    }

    public static boolean isBehavioralAction(String action) {
        return BEHAVIORAL_ACTIONS.contains(trim(action));
    }

    public static boolean isPrincipleAction(String action) {
        return PRINCIPLE_ACTIONS.contains(trim(action));
    }

    public static boolean isPracticalAction(String action) {
        return PRACTICAL_ACTIONS.contains(trim(action));
    }

    public static boolean isValidContinueDecision(String currentQuestionType,
                                                  String finalDecision,
                                                  String nextQuestionType) {
        String currentType = normalizeQuestionType(currentQuestionType);
        String decision = trim(finalDecision);
        String nextType = normalizeQuestionType(nextQuestionType);

        if (decision.isEmpty()
                || END_INTERVIEW.equals(decision)
                || nextType.isEmpty()
                || !isAllowedAction(decision)) {
            return false;
        }

        return switch (currentType) {
            case "INTRO" -> isExitAction(decision) && CANONICAL_NEXT_TYPES.contains(nextType);
            case "PRINCIPLE" -> validatePrincipleDecision(decision, nextType);
            case "PROJECT_DEEP_DIVE" -> validateProjectDecision(decision, nextType, "PROJECT_DEEP_DIVE");
            case "SCENARIO" -> validateProjectDecision(decision, nextType, "SCENARIO");
            case "BEHAVIORAL" -> validateBehavioralDecision(decision, nextType);
            default -> false;
        };
    }

    private static boolean validatePrincipleDecision(String decision, String nextType) {
        if (!PRINCIPLE_ACTIONS.contains(decision)) {
            return false;
        }
        if (isExitAction(decision)) {
            return Set.of("PROJECT_DEEP_DIVE", "SCENARIO", "BEHAVIORAL").contains(nextType);
        }
        return "PRINCIPLE".equals(nextType);
    }

    private static boolean validateProjectDecision(String decision, String nextType, String currentType) {
        if (!PRACTICAL_ACTIONS.contains(decision)) {
            return false;
        }
        if (isExitAction(decision)) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>(List.of("PRINCIPLE", "PROJECT_DEEP_DIVE", "SCENARIO", "BEHAVIORAL"));
            allowed.remove(currentType);
            return allowed.contains(nextType);
        }
        if ("收敛并落回具体知识点".equals(decision)) {
            return "PRINCIPLE".equals(nextType);
        }
        return "PROJECT_DEEP_DIVE".equals(nextType);
    }

    private static boolean validateBehavioralDecision(String decision, String nextType) {
        if (!BEHAVIORAL_ACTIONS.contains(decision)) {
            return false;
        }
        if (isExitAction(decision)) {
            return Set.of("PRINCIPLE", "PROJECT_DEEP_DIVE", "SCENARIO").contains(nextType);
        }
        return "BEHAVIORAL".equals(nextType);
    }

    private static Set<String> buildAllAllowedActions() {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        actions.addAll(PRINCIPLE_ACTIONS);
        actions.addAll(PRACTICAL_ACTIONS);
        actions.addAll(BEHAVIORAL_ACTIONS);
        actions.add(END_INTERVIEW);
        return Set.copyOf(actions);
    }

    private static String normalizeQuestionType(String questionType) {
        return trim(questionType).toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
