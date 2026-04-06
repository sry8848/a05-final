package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCatalog;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.contract.StrategyDefinition;
import com.a05.aiinterview.ai.contract.StrategyLimit;
import com.a05.aiinterview.ai.contract.StrategyRequiredContext;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拼装 evaluation_decision 的合法策略池。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据当前面试上下文，动态筛选出 AI 可以选择的合法策略集合</li>
 *   <li>确保 AI 不会做出越界或超额的决策</li>
 *   <li>为 AI 提供清晰的策略描述和适用条件</li>
 * </ul>
 *
 * <p>策略池组成：
 * <ol>
 *   <li>内部策略（Internal）：当前题型内的深入追问</li>
 *   <li>进入策略（Enter）：切换到其他题型</li>
 *   <li>结束策略（Wrapup）：结束面试</li>
 * </ol>
 */
@Component
public class AvailableStrategyAssembler {

    /**
     * 组装当前可用的策略池。
     *
     * <p>组装流程：
     * <ol>
     *   <li>规范化输入参数（题型、配额状态等）</li>
     *   <li>从策略目录中获取初始策略集合：
     *       <ul>
     *         <li>当前题型的所有内部策略</li>
     *         <li>所有进入策略（排除与当前题型相同的）</li>
     *         <li>结束策略</li>
     *       </ul>
     *   </li>
     *   <li>按筛选规则过滤掉不可用的策略</li>
     *   <li>转换为 AI 输入格式并返回</li>
     * </ol>
     *
     * @param currentQuestionType 当前题目的类型（PRINCIPLE/PROJECT/SCENARIO/BEHAVIORAL）
     * @param hasProjectContext 是否有项目上下文（简历中是否有项目经历）
     * @param remainingTargetDomains 剩余待考察的知识域列表
     * @param quotaState 配额使用状态
     * @param experienceLevel 候选人经验级别（JUNIOR/MID/SENIOR）
     * @return 可用策略列表，供 AI 选择
     */
    public List<EvaluationDecisionInput.AvailableStrategy> assemble(String currentQuestionType,
                                                                    boolean hasProjectContext,
                                                                    List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains,
                                                                    Map<String, Object> quotaState,
                                                                    String experienceLevel) {
        String normalizedType = StrategyCatalog.normalizeQuestionType(currentQuestionType);
        Map<String, Object> normalizedQuotaState = quotaState == null
                ? QuotaStateSupport.initialQuotaState()
                : quotaState;
        Set<String> remainingDomainCodes = extractRemainingDomainCodes(remainingTargetDomains);

        List<StrategyDefinition> pool = new ArrayList<>();
        pool.addAll(StrategyCatalog.internalStrategiesFor(normalizedType));
        pool.addAll(StrategyCatalog.enterStrategies().stream()
                .filter(definition -> !normalizedType.equals(definition.targetQuestionType()))
                .toList());
        pool.add(StrategyCatalog.wrapup());

        return pool.stream()
                .filter(definition -> isAvailable(
                        definition,
                        hasProjectContext,
                        remainingDomainCodes,
                        normalizedQuotaState,
                        experienceLevel
                ))
                .map(this::toInput)
                .toList();
    }

    private EvaluationDecisionInput.AvailableStrategy toInput(StrategyDefinition definition) {
        return EvaluationDecisionInput.AvailableStrategy.builder()
                .strategyCode(definition.code().code())
                .label(definition.label())
                .description(definition.description())
                .applicableWhen(definition.applicableWhen())
                .moveType(definition.moveType().name())
                .requiresTargetDomain(definition.requiresTargetDomain())
                .build();
    }

    /**
     * 判断策略在当前上下文中是否可用。
     *
     * <p>筛选规则（按顺序执行，任一规则不满足即返回 false）：
     * <ol>
     *   <li>结束策略总是可用（WRAPUP）</li>
     *   <li>如果策略需要目标知识域，但剩余知识域为空 → 不可用</li>
     *   <li>如果策略需要项目上下文，但没有项目信息 → 不可用</li>
     *   <li>如果是同点连续追问策略，且已达配额上限 → 不可用</li>
     *   <li>检查策略定义的所有 blockingLimits，任一超限 → 不可用</li>
     *   <li>所有检查通过 → 可用</li>
     * </ol>
     *
     * <p>配额限制的意义：
     * <ul>
     *   <li>防止某类题型过度使用（例如不能全是理论题）</li>
     *   <li>防止在同一知识点过度追问（避免候选人厌烦）</li>
     *   <li>根据经验级别动态调整配额（高级别候选人可以接受更多追问）</li>
     * </ul>
     *
     * @param definition 策略定义对象
     * @param hasProjectContext 是否有项目上下文
     * @param remainingDomainCodes 剩余待考察的知识域编码集合
     * @param quotaState 配额使用状态
     * @param experienceLevel 候选人经验级别
     * @return 策略是否可用
     */
    private boolean isAvailable(StrategyDefinition definition,
                                boolean hasProjectContext,
                                Set<String> remainingDomainCodes,
                                Map<String, Object> quotaState,
                                String experienceLevel) {
        // 规则1：结束策略总是可用
        if (definition.wrapup()) {
            return true;
        }
        // 规则2：如果策略需要目标知识域，但剩余知识域为空 → 不可用
        if (definition.requiresTargetDomain() && remainingDomainCodes.isEmpty()) {
            return false;
        }
        // 规则3：如果策略需要项目上下文，但没有项目信息 → 不可用
        if (definition.requiredContext().contains(StrategyRequiredContext.PROJECT_CONTEXT) && !hasProjectContext) {
            return false;
        }
        // 规则4：同点连续追问配额检查
        StrategyCode strategyCode = definition.code();
        if (QuotaStateSupport.isSamePointFollowUp(strategyCode)
                && QuotaStateSupport.toInt(quotaState.get(QuotaStateSupport.SAME_POINT_CONTINUE))
                >= InterviewPacingSupport.maxFor(experienceLevel, StrategyLimit.SAME_POINT_CONTINUE)) {
            return false;
        }
        // 规则5：检查策略定义的所有 blockingLimits
        for (StrategyLimit limit : definition.blockingLimits()) {
            if (QuotaStateSupport.toInt(quotaState.get(limit.ledgerKey()))
                    >= InterviewPacingSupport.maxFor(experienceLevel, limit)) {
                return false;
            }
        }
        // 所有检查通过，策略可用
        return true;
    }

    private Set<String> extractRemainingDomainCodes(List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains) {
        Set<String> codes = new LinkedHashSet<>();
        if (remainingTargetDomains == null) {
            return codes;
        }
        for (EvaluationDecisionInput.RemainingTargetDomain domain : remainingTargetDomains) {
            if (domain == null || domain.getDomainCode() == null || domain.getDomainCode().isBlank()) {
                continue;
            }
            codes.add(domain.getDomainCode().trim());
        }
        return codes;
    }
}
