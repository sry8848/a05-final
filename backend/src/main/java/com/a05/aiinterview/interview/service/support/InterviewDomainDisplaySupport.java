package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.common.enums.QuestionType;

import java.util.Locale;

/**
 * 统一处理不在岗位知识域表中的特殊 domainCode 显示名。
 */
public final class InterviewDomainDisplaySupport {

    private static final String INTRO_DOMAIN_CODE = "intro";
    private static final String BEHAVIORAL_DOMAIN_CODE = "behavioral";
    private static final String PROJECT_DELIVERY_DOMAIN_CODE = "project_delivery";

    private InterviewDomainDisplaySupport() {
    }

    public static String resolveSpecialDomainName(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return "";
        }
        return switch (domainCode.trim().toLowerCase(Locale.ROOT)) {
            case INTRO_DOMAIN_CODE -> QuestionType.INTRO.getDesc();
            case BEHAVIORAL_DOMAIN_CODE -> QuestionType.BEHAVIORAL.getDesc();
            case PROJECT_DELIVERY_DOMAIN_CODE -> "项目落地";
            default -> "";
        };
    }

    public static DomainIdentity resolveSpecialDomainForQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return new DomainIdentity("", "");
        }
        return switch (questionType.trim().toUpperCase(Locale.ROOT)) {
            case "INTRO" -> domainIdentity(INTRO_DOMAIN_CODE);
            case "BEHAVIORAL" -> domainIdentity(BEHAVIORAL_DOMAIN_CODE);
            case "PROJECT_DEEP_DIVE" -> domainIdentity(PROJECT_DELIVERY_DOMAIN_CODE);
            default -> new DomainIdentity("", "");
        };
    }

    private static DomainIdentity domainIdentity(String domainCode) {
        return new DomainIdentity(domainCode, resolveSpecialDomainName(domainCode));
    }

    public record DomainIdentity(String domainCode, String domainName) {
    }
}
