package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.service.support.InterviewDomainDisplaySupport;
import org.springframework.stereotype.Component;

import java.util.List;

/**
     * 系统兜底计划构建器，容错机制的第三级（最后防线）：AI 决策和修复都失败时使用。
     *
     * <p>核心设计思想：
     * <ul>
     *   <li>降级到行为题（BEHAVIORAL）模式，这是最安全、不依赖外部知识域的题型</li>
     *   <li>使用轮询机制选择行为维度，保证不同会话的多样性</li>
     *   <li>在状态账本中记录兜底次数，达到上限后结束面试</li>
     * </ul>
     *
     * <p>行为维度（FallbackDimension）：
     * <ul>
     *   <li>DECISION：决策能力 - 在信息不完整时做关键判断</li>
     *   <li>REFLECTION：复盘能力 - 主动复盘并改进</li>
     *   <li>CONFLICT：冲突处理 - 协作中遇到分歧并推动结果</li>
     *   <li>TRANSFER：经验迁移 - 把旧经验迁移到新问题</li>
     * </ul>
     *
     * <p>两种兜底模式：
     * <ol>
     *   <li>继续模式（buildContinuePlan）：出一道行为题继续面试</li>
     *   <li>结束模式（buildSystemErrorPlan）：兜底次数过多，结束面试</li>
     * </ol>
     */
    @Component
    public class SystemFallbackPlanBuilder {

    /**
     * 兜底行为维度枚举，定义不同的行为考察方向。
     *
     * <p>每个维度对应一个经典的STAR法则中的"T"（行动），用于考察候选人的软技能。
     *
     * @see <a href="https://en.wikipedia.org/wiki/Situation,_task,_action,_result">STAR法则</a>
     */
    public enum FallbackDimension {
        /**
         * 决策能力维度。
         *
         * <p>考察候选人在信息不完整时做关键判断并承担结果的能力。
         * <p>典型问题：你在XX情况下是如何做决策的？
         */
        DECISION("一次你在信息不完整时做关键判断并承担结果的真实经历"),

        /**
         * 复盘能力维度。
         *
         * <p>考察候选人主动复盘并改进的能力。
         * <p>典型问题：你是如何从XX中学习和改进的？
         */
        REFLECTION("一次你做完事情后主动复盘并改进的真实经历"),

        /**
         * 冲突处理维度。
         *
         * <p>考察候选人在协作中处理分歧并推动结果的能力。
         * <p>典型问题：你是如何处理团队分歧的？
         */
        CONFLICT("一次你在协作中遇到分歧并推动结果的真实经历"),

        /**
         * 经验迁移维度。
         *
         * <p>考察候选人将旧经验迁移到新问题的能力。
         * <p>典型问题：你是如何把XX经验应用到新场景的？
         */
        TRANSFER("一次你把旧经验迁移到新问题上的真实经历");

        /** 该维度对应的考察焦点（用于 nextFocus 字段） */
        private final String focus;

        FallbackDimension(String focus) {
            this.focus = focus;
        }

        public String focus() {
            return focus;
        }
    }

    /**
     * 行为维度列表，用于轮询选择。
     *
     * <p>轮询顺序：DECISION → REFLECTION → CONFLICT → TRANSFER → DECISION → ...
     * <p>每个维度对应一种经典的软技能考察方向。
     */
    private static final List<FallbackDimension> DIMENSIONS = List.of(
            FallbackDimension.DECISION,
            FallbackDimension.REFLECTION,
            FallbackDimension.CONFLICT,
            FallbackDimension.TRANSFER
    );

    /**
     * 构建继续模式的兜底执行计划：出一道行为题继续面试。
     *
     * <p>策略选择逻辑：
     * <ul>
     *   <li>如果当前题已经是行为题（BEHAVIORAL），选择对应维度的新行为策略</li>
     *   <li>否则，使用 S_ENTER_BEHAVIORAL 策略进入行为题模式</li>
     * </ul>
     *
     * <p>维度选择逻辑：
     * <ul>
     *   <li>使用 sessionId 的 hashCode 作为起始偏移量（保证同一会话的一致性）</li>
     *   <li>加上 rotationIndex 进行轮询（保证多次兜底的多样性）</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param currentQuestion 当前题目
     * @param rotationIndex 兜底轮询索引（从状态账本读取）
     * @return 继续模式的执行计划
     */
    public DecisionExecutionPlan buildContinuePlan(Long sessionId,
                                                   InterviewQuestion currentQuestion,
                                                   int rotationIndex) {
        // 根据会话ID和轮询索引选择行为维度
        FallbackDimension dimension = resolveDimension(sessionId, rotationIndex);

        // 确定策略编码
        String currentType = currentQuestion == null || currentQuestion.getQuestionType() == null
                ? ""
                : currentQuestion.getQuestionType().trim().toUpperCase();
        String strategyCode = "BEHAVIORAL".equals(currentType)
                ? strategyCodeForBehavioral(dimension)  // 已经在行为题模式，继续选择维度
                : StrategyCode.S_ENTER_BEHAVIORAL.code();  // 进入行为题模式

        return DecisionExecutionPlan.builder()
                .interviewAction("CONTINUE")
                .strategyCode(strategyCode)
                .targetQuestionType("BEHAVIORAL")
                .nextFocus(dimension.focus())
                .targetDomainCode("")
                .targetDomainName("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .decisionReason("当前轮决策修复失败，系统降级为行为题继续建立候选人的真实事件画像。")
                .effectiveDecisionSource(DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK)
                .fallbackDimension(dimension.name())
                .fallbackRotationIndex(rotationIndex)
                .build();
    }

    /**
     * 构建结束模式的兜底执行计划：兜底次数过多，结束面试。
     *
     * <p>触发条件：
     * <ul>
     *   <li>连续兜底次数 >= 2（consecutiveFallbackCount >= 2）</li>
     *   <li>或者 总兜底次数 >= 4（totalFallbackCount >= 4）</li>
     * </ul>
     *
     * <p>结束原因：
     * <ul>
     *   <li>terminationSource：SYSTEM_ERROR（系统错误）</li>
     *   <li>terminationReason：SYSTEM_DECISION_ERROR（系统决策错误）</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param currentQuestion 当前题目
     * @param rotationIndex 兜底轮询索引
     * @return 结束模式的执行计划
     */
    public DecisionExecutionPlan buildSystemErrorPlan(Long sessionId,
                                                      InterviewQuestion currentQuestion,
                                                      int rotationIndex) {
        FallbackDimension dimension = resolveDimension(sessionId, rotationIndex);
        return DecisionExecutionPlan.builder()
                .interviewAction("WRAPUP")
                .strategyCode(StrategyCode.S_WRAPUP.code())
                .targetQuestionType("")
                .nextFocus("")
                .targetDomainCode("")
                .targetDomainName("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .decisionReason("当前轮决策修复失败且系统兜底次数已达上限，结束本场面试。")
                .effectiveDecisionSource(DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK)
                .terminationSource(DecisionExecutionPlan.TerminationSource.SYSTEM_ERROR)
                .terminationReason(DecisionExecutionPlan.TerminationReason.SYSTEM_DECISION_ERROR)
                .fallbackDimension(dimension.name())
                .fallbackRotationIndex(rotationIndex)
                .build();
    }

    /**
     * 根据会话ID和轮询索引解析兜底行为维度。
     *
     * <p>选择算法：
     * <ol>
     *   <li>计算起始偏移量：sessionId.hashCode() % DIMENSIONS.size()</li>
     *   <li>加上 rotationIndex 进行轮询：(startOffset + rotationIndex) % DIMENSIONS.size()</li>
     *   <li>使用 Math.floorMod 保证结果为正数</li>
     * </ol>
     *
     * <p>一致性保证：
     * <ul>
     *   <li>同一会话（相同 sessionId）始终从相同的起始偏移量开始</li>
     *   <li>这确保了同一会话的多次兜底能均匀分布在4个维度上</li>
     * </ul>
     *
     * <p>多样性保证：
     * <ul>
     *   <li>rotationIndex 递增时，会轮询到不同的维度</li>
     *   <li>不同会话（不同 sessionId）会有不同的起始偏移量</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param rotationIndex 兜底轮询索引
     * @return 选择的行为维度
     */
    public FallbackDimension resolveDimension(Long sessionId, int rotationIndex) {
        int startOffset = Math.floorMod(Long.hashCode(sessionId == null ? 0L : sessionId), DIMENSIONS.size());
        return DIMENSIONS.get(Math.floorMod(startOffset + rotationIndex, DIMENSIONS.size()));
    }

    /**
     * 根据行为维度获取对应的策略编码。
     *
     * <p>用于在已经是行为题模式时，继续选择不同维度的行为策略。
     * <p>策略编码对应关系：
     * <ul>
     *   <li>DECISION → S_B_NEW_DECISION（新决策行为）</li>
     *   <li>REFLECTION → S_B_NEW_REFLECTION（新复盘行为）</li>
     *   <li>CONFLICT → S_B_NEW_CONFLICT（新冲突行为）</li>
     *   <li>TRANSFER → S_B_NEW_TRANSFER（新迁移行为）</li>
     * </ul>
     *
     * @param dimension 行为维度
     * @return 对应的策略编码
     */
    private String strategyCodeForBehavioral(FallbackDimension dimension) {
        return switch (dimension) {
            case DECISION -> StrategyCode.S_B_NEW_DECISION.code();
            case REFLECTION -> StrategyCode.S_B_NEW_REFLECTION.code();
            case CONFLICT -> StrategyCode.S_B_NEW_CONFLICT.code();
            case TRANSFER -> StrategyCode.S_B_NEW_TRANSFER.code();
        };
    }
}
