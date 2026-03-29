package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.common.enums.QuestionType;

import java.util.Locale;

/**
 * 统一处理知识域显示例外与题型标签显示。
 */
public final class InterviewDomainDisplaySupport {

    private static final String INTRO_DOMAIN_CODE = "intro";

    private InterviewDomainDisplaySupport() {
    }

    public static String resolveSpecialDomainName(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return "";
        }
        return switch (domainCode.trim().toLowerCase(Locale.ROOT)) {
            case INTRO_DOMAIN_CODE -> QuestionType.INTRO.getDesc();
            default -> "";
        };
    }

    public static DomainIdentity resolveSpecialDomainForQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return new DomainIdentity("", "");
        }
        return switch (questionType.trim().toUpperCase(Locale.ROOT)) {
            case "INTRO" -> domainIdentity(INTRO_DOMAIN_CODE);
            default -> new DomainIdentity("", "");
        };
    }

    public static String resolveQuestionTypeLabel(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "";
        }
        return switch (questionType.trim().toUpperCase(Locale.ROOT)) {
            case "INTRO" -> QuestionType.INTRO.getDesc();
            case "PROJECT_DEEP_DIVE" -> QuestionType.PROJECT_DEEP_DIVE.getDesc();
            case "SCENARIO" -> QuestionType.SCENARIO.getDesc();
            case "PRINCIPLE" -> QuestionType.PRINCIPLE.getDesc();
            case "BEHAVIORAL" -> QuestionType.BEHAVIORAL.getDesc();
            default -> "";
        };
    }

    private static DomainIdentity domainIdentity(String domainCode) {
        return new DomainIdentity(domainCode, resolveSpecialDomainName(domainCode));
    }

    public record DomainIdentity(String domainCode, String domainName) {
    }
}
