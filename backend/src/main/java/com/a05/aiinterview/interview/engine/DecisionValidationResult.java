package com.a05.aiinterview.interview.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionValidationResult {
    private boolean valid;
    private DecisionExecutionPlan plan;
    private List<String> errorCodes;

    public static DecisionValidationResult success(DecisionExecutionPlan plan) {
        return DecisionValidationResult.builder()
                .valid(true)
                .plan(plan)
                .errorCodes(List.of())
                .build();
    }

    public static DecisionValidationResult failure(List<String> errorCodes) {
        return DecisionValidationResult.builder()
                .valid(false)
                .plan(null)
                .errorCodes(errorCodes == null ? List.of() : List.copyOf(errorCodes))
                .build();
    }
}
