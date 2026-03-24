package com.a05.aiinterview.interview.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后端账本使用的新变更输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMutation {

    private String questionType;
    private String currentDomainCode;
    private String currentFocus;
    private String currentItemKey;
    private String currentItemType;
    private String currentItemName;
    private String nextFocus;
    private String nextItemKey;
    private String nextItemType;
    private String nextItemName;
    private String questionFamilyId;
    private boolean skipCurrentQuestion;
    private String interviewAction;
    private List<CoveredDomainByCode> newCoveredDomains;
    private List<String> newCoveredPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoveredDomainByCode {
        private String domainCode;
        private String domainName;
    }
}
