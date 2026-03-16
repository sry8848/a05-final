package com.a05.aiinterview.interview.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后端状态机使用的最小账本变更输入。
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
    private boolean passCurrentLevel;
    private boolean deepen;
    private String signal;
    private String activeProjectId;
    private boolean skipCurrentQuestion;
}
