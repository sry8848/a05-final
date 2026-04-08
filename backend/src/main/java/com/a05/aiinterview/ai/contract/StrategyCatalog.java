package com.a05.aiinterview.ai.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 评估决策策略目录。
 * Chunk 2 起作为后端唯一策略真源。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义所有可用的面试策略</li>
 *   <li>作为策略的单一真实来源（Single Source of Truth）</li>
 *   <li>提供策略查询和分类方法</li>
 * </ul>
 *
 * <p>策略分类：
 * <ul>
 *   <li><b>内部策略（Internal/ADVANCE）</b>：在当前题型内深入追问</li>
 *   <li><b>切换策略（SWITCH）</b>：在当前题型内切换焦点或项目</li>
 *   <li><b>进入策略（ENTER）</b>：切换到其他题型</li>
 *   <li><b>结束策略（END/WRAPUP）</b>：结束面试</li>
 * </ul>
 *
 * <p>题型定义：
 * <ul>
 *   <li>INTRO：自我介绍</li>
 *   <li>PRINCIPLE：理论题</li>
 *   <li>PROJECT_DEEP_DIVE：项目深挖题</li>
 *   <li>SCENARIO：场景题</li>
 *   <li>BEHAVIORAL：行为题</li>
 * </ul>
 */
public final class StrategyCatalog {

    /** 题型常量：自我介绍 */
    private static final String INTRO = "INTRO";
    /** 题型常量：理论题 */
    private static final String PRINCIPLE = "PRINCIPLE";
    /** 题型常量：项目深挖题 */
    private static final String PROJECT = "PROJECT_DEEP_DIVE";
    /** 题型常量：场景题 */
    private static final String SCENARIO = "SCENARIO";
    /** 题型常量：行为题 */
    private static final String BEHAVIORAL = "BEHAVIORAL";

    /** 所有题型的集合 */
    private static final Set<String> ALL_CURRENT_TYPES = Set.of(INTRO, PRINCIPLE, PROJECT, SCENARIO, BEHAVIORAL);
    /** 所有策略定义的列表（初始化时构建，不可变） */
    private static final List<StrategyDefinition> DEFINITIONS = buildDefinitions();
    /** 策略编码到策略定义的索引映射（初始化时构建，不可变） */
    private static final Map<String, StrategyDefinition> BY_CODE = buildIndex();

    private StrategyCatalog() {
    }

    public static List<StrategyDefinition> all() {
        return DEFINITIONS;
    }

    public static Optional<StrategyDefinition> find(String code) {
        StrategyCode strategyCode = StrategyCode.fromCode(code);
        return strategyCode == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(strategyCode.code()));
    }

    public static boolean isAllowed(String code) {
        return find(code).isPresent();
    }

    public static boolean requiresTargetDomain(String code) {
        return find(code).map(StrategyDefinition::requiresTargetDomain).orElse(false);
    }

    public static boolean isWrapup(String code) {
        return find(code).map(StrategyDefinition::wrapup).orElse(false);
    }

    public static String targetQuestionType(String code) {
        return find(code).map(StrategyDefinition::targetQuestionType).orElse("");
    }

    /**
     * 获取指定题型的所有内部策略（不包括进入策略和结束策略）。
     *
     * <p>内部策略定义：
     * <ul>
     *   <li>moveType 不是 ENTER（排除进入策略）</li>
     *   <li>不是 wrapup（排除结束策略）</li>
     *   <li>允许在当前题型使用</li>
     * </ul>
     *
     * <p>使用场景：
     * <ul>
     *   <li>当前是理论题时，获取所有理论题内的深入追问策略</li>
     *   <li>当前是项目题时，获取所有项目题内的深入追问策略</li>
     * </ul>
     *
     * @param currentQuestionType 当前题型
     * @return 该题型可用的内部策略列表
     */
    public static List<StrategyDefinition> internalStrategiesFor(String currentQuestionType) {
        String normalizedType = normalizeQuestionType(currentQuestionType);
        return DEFINITIONS.stream()
                .filter(definition -> definition.moveType() != StrategyMoveType.ENTER)
                .filter(definition -> !definition.wrapup())
                .filter(definition -> definition.allowedCurrentQuestionTypes().contains(normalizedType))
                .toList();
    }

    /**
     * 获取所有进入策略（用于切换到其他题型）。
     *
     * <p>进入策略定义：
     * <ul>
     *   <li>moveType = ENTER</li>
     *   <li>用于从当前题型切换到其他题型</li>
     * </ul>
     *
     * <p>例如：
     * <ul>
     *   <li>S_ENTER_PRINCIPLE：从其他题型进入理论题</li>
     *   <li>S_ENTER_PROJECT：从其他题型进入项目题</li>
     * </ul>
     *
     * @return 所有进入策略列表
     */
    public static List<StrategyDefinition> enterStrategies() {
        return DEFINITIONS.stream()
                .filter(definition -> definition.moveType() == StrategyMoveType.ENTER)
                .toList();
    }

    /**
     * 获取结束策略（S_WRAPUP）。
     *
     * <p>结束策略的特点：
     * <ul>
     *   <li>任何时候都可用</li>
     *   <li>没有配额限制</li>
     *   <li>用于结束本场面试</li>
     * </ul>
     *
     * @return 结束策略定义
     */
    public static StrategyDefinition wrapup() {
        return BY_CODE.get(StrategyCode.S_WRAPUP.code());
    }

    /**
     * 规范化题型名称（去空格、转大写）。
     *
     * <p>规范化的意义：
     * <ul>
     *   <li>统一输入格式，避免大小写问题</li>
     *   <li>去除首尾空格，避免匹配失败</li>
     *   <li>使用 Locale.ROOT 确保跨语言环境一致</li>
     * </ul>
     *
     * @param questionType 原始题型名称
     * @return 规范化后的题型名称
     */
    public static String normalizeQuestionType(String questionType) {
        return questionType == null ? "" : questionType.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, StrategyDefinition> buildIndex() {
        Map<String, StrategyDefinition> index = new LinkedHashMap<>();
        for (StrategyDefinition definition : DEFINITIONS) {
            index.put(definition.code().code(), definition);
        }
        return Map.copyOf(index);
    }

    private static List<StrategyDefinition> buildDefinitions() {
        List<StrategyDefinition> definitions = new ArrayList<>();

        definitions.add(definition(
                StrategyCode.S_P_VERIFY, "引导和验证", "在当前理论点内做低成本澄清，验证候选人是否真的理解。",
                "当前理论点仍有低成本增量信息。",
                StrategyMoveType.ADVANCE, Set.of(PRINCIPLE), PRINCIPLE, false, Set.of(),
                Set.of(StrategyLimit.SAME_POINT_CONTINUE, StrategyLimit.SAME_DOMAIN_CONTINUE, StrategyLimit.PRINCIPLE_TOTAL),
                StrategyQuotaPolicy.incrementOnly(
                        StrategyLimit.SAME_POINT_CONTINUE,
                        StrategyLimit.SAME_DOMAIN_CONTINUE,
                        StrategyLimit.PRINCIPLE_TOTAL
                ),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_P_DEEP_LINK, "深入强关联点", "沿知识图谱深入与当前点强关联的理论点，验证理解深度。",
                "当前点已有初步掌握，且存在更高价值的强关联点。",
                StrategyMoveType.ADVANCE, Set.of(PRINCIPLE), PRINCIPLE, false, Set.of(),
                Set.of(StrategyLimit.SAME_DOMAIN_CONTINUE, StrategyLimit.PRINCIPLE_TOTAL),
                StrategyQuotaPolicy.incrementAndReset(
                        Set.of(StrategyLimit.SAME_DOMAIN_CONTINUE, StrategyLimit.PRINCIPLE_TOTAL),
                        Set.of(StrategyLimit.SAME_POINT_CONTINUE)
                ),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_P_VARIANT, "变式", "保持同一理论主线，通过变式问题验证理解是否稳定。",
                "候选人掌握基本正确，但需要换角度验证。",
                StrategyMoveType.ADVANCE, Set.of(PRINCIPLE), PRINCIPLE, false, Set.of(),
                Set.of(StrategyLimit.SAME_POINT_CONTINUE, StrategyLimit.SAME_DOMAIN_CONTINUE, StrategyLimit.PRINCIPLE_TOTAL),
                StrategyQuotaPolicy.incrementOnly(
                        StrategyLimit.SAME_POINT_CONTINUE,
                        StrategyLimit.SAME_DOMAIN_CONTINUE,
                        StrategyLimit.PRINCIPLE_TOTAL
                ),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_P_SAME_DOMAIN_SHIFT, "同域平移", "停留在当前知识域内，平移到另一个可判分知识点。",
                "当前点已接近榨干，但当前域仍有高价值信息。",
                StrategyMoveType.ADVANCE, Set.of(PRINCIPLE), PRINCIPLE, false, Set.of(),
                Set.of(StrategyLimit.SAME_DOMAIN_CONTINUE, StrategyLimit.PRINCIPLE_TOTAL),
                StrategyQuotaPolicy.incrementAndReset(
                        Set.of(StrategyLimit.SAME_DOMAIN_CONTINUE, StrategyLimit.PRINCIPLE_TOTAL),
                        Set.of(StrategyLimit.SAME_POINT_CONTINUE)
                ),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_SWITCH_DOMAIN, "切换知识域", "结束当前理论域，切到主考纲中另一个剩余待考察域。",
                "当前理论域已形成初步判断，继续停留收益很低。",
                StrategyMoveType.SWITCH, Set.of(PRINCIPLE), PRINCIPLE, true, Set.of(),
                Set.of(StrategyLimit.PRINCIPLE_TOTAL),
                StrategyQuotaPolicy.incrementAndReset(
                        Set.of(StrategyLimit.PRINCIPLE_TOTAL),
                        Set.of(StrategyLimit.SAME_POINT_CONTINUE, StrategyLimit.SAME_DOMAIN_CONTINUE)
                ),
                false
        ));

        Set<StrategyRequiredContext> projectContext = Set.of(StrategyRequiredContext.PROJECT_CONTEXT);
        Set<StrategyLimit> projectLineLimits = Set.of(
                StrategyLimit.SAME_PROJECT_POINT_CONTINUE,
                StrategyLimit.SAME_PROJECT_CONTINUE,
                StrategyLimit.PROJECT_TOTAL
        );
        StrategyQuotaPolicy projectLinePolicy = StrategyQuotaPolicy.incrementOnly(
                StrategyLimit.SAME_PROJECT_POINT_CONTINUE,
                StrategyLimit.SAME_PROJECT_CONTINUE,
                StrategyLimit.PROJECT_TOTAL
        );
        definitions.add(definition(
                StrategyCode.S_J_RECONSTRUCT, "引导还原", "让候选人回到真实链路，具体还原项目中如何实现。",
                "项目主线尚未说清，需验证真实性。",
                StrategyMoveType.ADVANCE, Set.of(PROJECT), PROJECT, false, projectContext,
                projectLineLimits, projectLinePolicy, false
        ));
        definitions.add(definition(
                StrategyCode.S_J_RESPONSIBILITY, "责任定位", "追问具体职责、边界和亲手完成的部分。",
                "需要确认候选人在项目中的真实责任边界。",
                StrategyMoveType.ADVANCE, Set.of(PROJECT), PROJECT, false, projectContext,
                projectLineLimits, projectLinePolicy, false
        ));
        definitions.add(definition(
                StrategyCode.S_J_PRESSURE, "压测", "在真实工程链路中增加压力条件，考察边界判断与应对能力。",
                "当前点已具备继续加压的高信息增益。",
                StrategyMoveType.ADVANCE, Set.of(PROJECT), PROJECT, false, projectContext,
                projectLineLimits, projectLinePolicy, false
        ));
        definitions.add(definition(
                StrategyCode.S_J_TRADEOFF, "做权衡", "围绕真实实现方案追问取舍依据与替代方案。",
                "候选人已说明方案，需要验证决策质量。",
                StrategyMoveType.ADVANCE, Set.of(PROJECT), PROJECT, false, projectContext,
                projectLineLimits, projectLinePolicy, false
        ));
        definitions.add(definition(
                StrategyCode.S_J_GUARDRAILS, "兜底与观测", "追问故障兜底、监控和可观测性设计。",
                "当前链路已足够具体，可继续验证工程完备性。",
                StrategyMoveType.ADVANCE, Set.of(PROJECT), PROJECT, false, projectContext,
                projectLineLimits, projectLinePolicy, false
        ));
        definitions.add(definition(
                StrategyCode.S_J_EVOLUTION, "演进与复盘", "追问方案迭代、踩坑与后续演进。",
                "项目已讲清主链路，需要看成长性与复盘能力。",
                StrategyMoveType.ADVANCE, Set.of(PROJECT), PROJECT, false, projectContext,
                projectLineLimits, projectLinePolicy, false
        ));
        definitions.add(definition(
                StrategyCode.S_J_SWITCH_POINT, "切换项目要点", "继续停留在项目题，但切到另一个值得验证的实现要点。",
                "当前项目仍有高信息密度，但当前要点已接近榨干。",
                StrategyMoveType.SWITCH, Set.of(PROJECT), PROJECT, false, projectContext,
                Set.of(StrategyLimit.SAME_PROJECT_CONTINUE, StrategyLimit.PROJECT_TOTAL),
                StrategyQuotaPolicy.incrementAndReset(
                        Set.of(StrategyLimit.SAME_PROJECT_CONTINUE, StrategyLimit.PROJECT_TOTAL),
                        Set.of(StrategyLimit.SAME_PROJECT_POINT_CONTINUE)
                ),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_J_SWITCH_PROJECT, "切换项目", "切到另一段真实项目或实习经历建立工程画像。",
                "当前项目信息增益下降，但仍需项目向验证。",
                StrategyMoveType.SWITCH, Set.of(PROJECT), PROJECT, false, projectContext,
                Set.of(StrategyLimit.PROJECT_TOTAL),
                StrategyQuotaPolicy.incrementAndReset(
                        Set.of(StrategyLimit.PROJECT_TOTAL),
                        Set.of(StrategyLimit.SAME_PROJECT_POINT_CONTINUE, StrategyLimit.SAME_PROJECT_CONTINUE)
                ),
                false
        ));

        definitions.addAll(scenarioDefinitions());
        definitions.addAll(behavioralDefinitions());

        definitions.add(definition(
                StrategyCode.S_ENTER_PRINCIPLE, "进入理论题", "下一题切入理论知识验证，补齐可判分知识掌握。",
                "当前最有信息增益的方向是回到明确知识域。",
                StrategyMoveType.ENTER, ALL_CURRENT_TYPES, PRINCIPLE, true, Set.of(),
                Set.of(StrategyLimit.PRINCIPLE_TOTAL),
                StrategyQuotaPolicy.enter(Set.of(StrategyLimit.PRINCIPLE_TOTAL)),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_ENTER_PROJECT, "进入项目题", "下一题准备建立真实工程画像，回到候选人的项目主线。",
                "项目仍然是当前信息密度最高的入口。",
                StrategyMoveType.ENTER, ALL_CURRENT_TYPES, PROJECT, false, projectContext,
                Set.of(StrategyLimit.PROJECT_TOTAL),
                StrategyQuotaPolicy.enter(Set.of(StrategyLimit.PROJECT_TOTAL)),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_ENTER_SCENARIO, "进入场景题", "下一题进入带约束的真实场景，验证应对能力。",
                "当前语境适合施加场景化约束。",
                StrategyMoveType.ENTER, ALL_CURRENT_TYPES, SCENARIO, false, Set.of(),
                Set.of(StrategyLimit.SCENARIO_TOTAL),
                StrategyQuotaPolicy.enter(Set.of(StrategyLimit.SCENARIO_TOTAL)),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_ENTER_BEHAVIORAL, "进入行为题", "下一题切到真实协作与复盘事件，补齐软素质判断。",
                "当前更需要真实事件来建立行为画像。",
                StrategyMoveType.ENTER, ALL_CURRENT_TYPES, BEHAVIORAL, false, Set.of(),
                Set.of(StrategyLimit.BEHAVIORAL_TOTAL),
                StrategyQuotaPolicy.enter(Set.of(StrategyLimit.BEHAVIORAL_TOTAL)),
                false
        ));
        definitions.add(definition(
                StrategyCode.S_WRAPUP, "结束面试", "结束本场面试。",
                "当前已形成足够能力画像，或没有继续追问价值。",
                StrategyMoveType.END, ALL_CURRENT_TYPES, "", false, Set.of(),
                Set.of(), StrategyQuotaPolicy.none(), true
        ));
        return List.copyOf(definitions);
    }

    private static List<StrategyDefinition> scenarioDefinitions() {
        List<StrategyDefinition> definitions = new ArrayList<>();
        Set<String> allowed = Set.of(SCENARIO);
        Set<StrategyLimit> blocking = Set.of(StrategyLimit.SCENARIO_TOTAL);
        StrategyQuotaPolicy policy = StrategyQuotaPolicy.incrementOnly(StrategyLimit.SCENARIO_TOTAL);

        definitions.add(definition(StrategyCode.S_S_FOLLOW_DIAGNOSE, "追问当前场景的定位判断", "围绕当前场景继续追问如何定位问题。", "当前场景的定位过程仍有高信息增益。", StrategyMoveType.ADVANCE, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_FOLLOW_RESPONSE, "追问当前场景的应对动作", "围绕当前场景继续追问如何处置和恢复。", "当前场景已经建立，需要验证候选人的处理顺序。", StrategyMoveType.ADVANCE, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_FOLLOW_TRADEOFF, "追问当前场景的方案权衡", "围绕当前场景继续追问如何做方案取舍。", "当前场景已足够清楚，可继续验证权衡能力。", StrategyMoveType.ADVANCE, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_FOLLOW_GUARDRAILS, "追问当前场景的兜底与防复发", "围绕当前场景继续追问监控、兜底与防复发。", "当前场景已建立，需要验证后续守护能力。", StrategyMoveType.ADVANCE, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_NEW_DIAGNOSE, "换一道新的场景定位题", "继续停留在场景题，但切到新的定位判断切片。", "还需要更多场景判断样本。", StrategyMoveType.SWITCH, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_NEW_RESPONSE, "换一道新的场景处置题", "继续停留在场景题，但切到新的处置恢复切片。", "还需要更多故障应对样本。", StrategyMoveType.SWITCH, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_NEW_TRADEOFF, "换一道新的场景权衡题", "继续停留在场景题，但切到新的方案权衡切片。", "还需要更多权衡决策样本。", StrategyMoveType.SWITCH, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_S_NEW_GUARDRAILS, "换一道新的场景兜底题", "继续停留在场景题，但切到新的兜底与观测切片。", "还需要更多工程守护样本。", StrategyMoveType.SWITCH, allowed, SCENARIO, false, Set.of(), blocking, policy, false));
        return definitions;
    }

    private static List<StrategyDefinition> behavioralDefinitions() {
        List<StrategyDefinition> definitions = new ArrayList<>();
        Set<String> allowed = Set.of(BEHAVIORAL);
        Set<StrategyLimit> blocking = Set.of(StrategyLimit.BEHAVIORAL_TOTAL);
        StrategyQuotaPolicy policy = StrategyQuotaPolicy.incrementOnly(StrategyLimit.BEHAVIORAL_TOTAL);

        definitions.add(definition(StrategyCode.S_B_FOLLOW_DECISION, "追问当前行为题的决策过程", "围绕当前真实事件继续追问关键判断过程。", "当前事件已建立，但决策依据仍不清楚。", StrategyMoveType.ADVANCE, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_FOLLOW_REFLECTION, "追问当前行为题的复盘成长", "围绕当前真实事件继续追问复盘、成长和后续变化。", "当前事件经过清楚，可继续验证复盘能力。", StrategyMoveType.ADVANCE, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_FOLLOW_CONFLICT, "追问当前行为题的协作冲突", "围绕当前真实事件继续追问冲突处理与推进方式。", "当前事件里存在协作张力，值得继续验证。", StrategyMoveType.ADVANCE, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_FOLLOW_TRANSFER, "追问当前行为题的迁移能力", "围绕当前真实事件继续追问经验抽象和迁移。", "当前事件已足够具体，可继续验证抽象能力。", StrategyMoveType.ADVANCE, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_NEW_DECISION, "换一道新的行为决策题", "继续停留在行为题，但切到新的决策事件样本。", "还需要更多行为决策样本。", StrategyMoveType.SWITCH, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_NEW_REFLECTION, "换一道新的行为复盘题", "继续停留在行为题，但切到新的复盘成长事件样本。", "还需要更多复盘样本。", StrategyMoveType.SWITCH, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_NEW_CONFLICT, "换一道新的行为冲突题", "继续停留在行为题，但切到新的协作冲突事件样本。", "还需要更多协作样本。", StrategyMoveType.SWITCH, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        definitions.add(definition(StrategyCode.S_B_NEW_TRANSFER, "换一道新的行为迁移题", "继续停留在行为题，但切到新的经验迁移事件样本。", "还需要更多迁移能力样本。", StrategyMoveType.SWITCH, allowed, BEHAVIORAL, false, Set.of(), blocking, policy, false));
        return definitions;
    }

    private static StrategyDefinition definition(StrategyCode code,
                                                 String label,
                                                 String description,
                                                 String applicableWhen,
                                                 StrategyMoveType moveType,
                                                 Set<String> allowedCurrentQuestionTypes,
                                                 String targetQuestionType,
                                                 boolean requiresTargetDomain,
                                                 Set<StrategyRequiredContext> requiredContext,
                                                 Set<StrategyLimit> blockingLimits,
                                                 StrategyQuotaPolicy quotaUpdatePolicy,
                                                 boolean wrapup) {
        return new StrategyDefinition(
                code,
                label,
                description,
                applicableWhen,
                moveType,
                normalizeTypes(allowedCurrentQuestionTypes),
                targetQuestionType,
                requiresTargetDomain,
                requiredContext,
                blockingLimits,
                quotaUpdatePolicy,
                wrapup
        );
    }

    private static Set<String> normalizeTypes(Set<String> types) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            normalized.add(normalizeQuestionType(type));
        }
        return Set.copyOf(normalized);
    }
}
