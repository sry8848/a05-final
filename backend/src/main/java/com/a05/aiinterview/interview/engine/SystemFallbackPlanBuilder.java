package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.service.support.InterviewDomainDisplaySupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SystemFallbackPlanBuilder {

    public enum FallbackDimension {
        DECISION("一次你在信息不完整时做关键判断并承担结果的真实经历"),
        REFLECTION("一次你做完事情后主动复盘并改进的真实经历"),
        CONFLICT("一次你在协作中遇到分歧并推动结果的真实经历"),
        TRANSFER("一次你把旧经验迁移到新问题上的真实经历");

        private final String focus;

        FallbackDimension(String focus) {
            this.focus = focus;
        }

        public String focus() {
            return focus;
        }
    }

    private static final List<FallbackDimension> DIMENSIONS = List.of(
            FallbackDimension.DECISION,
            FallbackDimension.REFLECTION,
            FallbackDimension.CONFLICT,
            FallbackDimension.TRANSFER
    );

    public DecisionExecutionPlan buildContinuePlan(Long sessionId,
                                                   InterviewQuestion currentQuestion,
                                                   int rotationIndex) {
        FallbackDimension dimension = resolveDimension(sessionId, rotationIndex);
        InterviewDomainDisplaySupport.DomainIdentity behavioralDomain =
                InterviewDomainDisplaySupport.resolveSpecialDomainForQuestionType("BEHAVIORAL");
        String currentType = currentQuestion == null || currentQuestion.getQuestionType() == null
                ? ""
                : currentQuestion.getQuestionType().trim().toUpperCase();
        String strategyCode = "BEHAVIORAL".equals(currentType)
                ? strategyCodeForBehavioral(dimension)
                : StrategyCode.S_ENTER_BEHAVIORAL.code();
        return DecisionExecutionPlan.builder()
                .interviewAction("CONTINUE")
                .strategyCode(strategyCode)
                .targetQuestionType("BEHAVIORAL")
                .nextFocus(dimension.focus())
                .targetDomainCode(behavioralDomain.domainCode())
                .targetDomainName(behavioralDomain.domainName())
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .decisionReason("当前轮决策修复失败，系统降级为行为题继续建立候选人的真实事件画像。")
                .effectiveDecisionSource(DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK)
                .fallbackDimension(dimension.name())
                .fallbackRotationIndex(rotationIndex)
                .build();
    }

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

    public FallbackDimension resolveDimension(Long sessionId, int rotationIndex) {
        int startOffset = Math.floorMod(Long.hashCode(sessionId == null ? 0L : sessionId), DIMENSIONS.size());
        return DIMENSIONS.get(Math.floorMod(startOffset + rotationIndex, DIMENSIONS.size()));
    }

    private String strategyCodeForBehavioral(FallbackDimension dimension) {
        return switch (dimension) {
            case DECISION -> StrategyCode.S_B_NEW_DECISION.code();
            case REFLECTION -> StrategyCode.S_B_NEW_REFLECTION.code();
            case CONFLICT -> StrategyCode.S_B_NEW_CONFLICT.code();
            case TRANSFER -> StrategyCode.S_B_NEW_TRANSFER.code();
        };
    }
}
