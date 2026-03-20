package com.a05.aiinterview.interview.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 后端账本使用的最小变更输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMutation {

    private String questionType;
    private String currentDomainCode;
    private Long currentDomainId;
    private String currentTargetDepth;
    private String decision;
    private String answerVerdict;
    private String domainOutcome;
    private String activeProjectId;
    private String currentFocus;
    private String focusPoint;
    private String questionFamilyId;
    private boolean skipCurrentQuestion;
    private Map<String, Object> statePatch;
}
