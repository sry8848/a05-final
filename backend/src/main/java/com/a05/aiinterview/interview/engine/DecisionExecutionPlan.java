package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionExecutionPlan {

    private String interviewAction;
    private String strategyCode;
    private String targetQuestionType;
    private String nextFocus;
    private String nextItemType;
    private String nextItemName;
    private String nextProjectPoint;
    private String targetDomainCode;
    private String targetDomainName;
    private List<EvaluationDecisionOutput.CoveredDomain> newCoveredDomains;
    private List<String> newCoveredPoints;
    private List<EvaluationDecisionOutput.RetrievalPlan> retrievalPlans;
    private String decisionReason;
    private EffectiveDecisionSource effectiveDecisionSource;
    private TerminationSource terminationSource;
    private TerminationReason terminationReason;
    private String fallbackDimension;
    private Integer fallbackRotationIndex;

    public enum EffectiveDecisionSource {
        RAW_AI,
        REPAIRED_AI,
        SYSTEM_FALLBACK
    }

    public enum TerminationSource {
        AI_WRAPUP,
        SYSTEM_ERROR,
        MAX_QUESTIONS
    }

    public enum TerminationReason {
        NORMAL_WRAPUP,
        SYSTEM_DECISION_ERROR,
        MAX_QUESTIONS
    }
}
