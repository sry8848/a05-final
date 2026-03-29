package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResolution {
    private EvaluationDecisionInput evalInput;
    private EvaluationDecisionOutput rawOutput;
    private String rawResponse;
    private List<String> validationErrors;
    private EvaluationDecisionInput repairInput;
    private EvaluationDecisionOutput repairedOutput;
    private String repairRawResponse;
    private List<String> repairErrors;
    private int repairAttempts;
    private DecisionExecutionPlan effectivePlan;
    private EvaluationDecisionOutput effectiveOutput;
    private Map<String, Object> validationAudit;
}
