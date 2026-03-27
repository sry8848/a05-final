package com.a05.aiinterview.interview.dto;

import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.service.support.InterviewDomainDisplaySupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目 DTO / 快照组装工具。
 */
public final class QuestionDtoAssembler {

    private QuestionDtoAssembler() {
    }

    public static QuestionDto fromQuestion(InterviewQuestion question, InterviewSession session) {
        if (question == null) {
            return null;
        }
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(question.getId());
        dto.setQuestionNo(question.getQuestionNo());
        dto.setQuestionType(question.getQuestionType());
        dto.setDomainCode(question.getDomainCode());
        dto.setDomainName(resolveDomainName(question, session));
        dto.setStem(question.getStem());
        dto.setTargetSkill(question.getTargetSkill());
        dto.setAiResultStatus(resolveAiResultStatus(question));
        dto.setHintAvailable(true);
        return dto;
    }

    public static QuestionDto fromSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(toLong(snapshot.get("questionId")));
        dto.setQuestionNo(toInt(snapshot.get("questionNo")));
        dto.setQuestionType(toStr(snapshot.get("questionType")));
        dto.setDomainCode(toStr(snapshot.get("domainCode")));
        dto.setDomainName(toStr(snapshot.get("domainName")));
        dto.setStem(toStr(snapshot.get("stem")));
        dto.setTargetSkill(toStr(snapshot.get("targetSkill")));
        dto.setAiResultStatus(toStr(snapshot.get("aiResultStatus")));
        Object hintAvailable = snapshot.get("hintAvailable");
        dto.setHintAvailable(hintAvailable instanceof Boolean bool ? bool : true);
        return dto;
    }

    public static Map<String, Object> toSnapshot(InterviewQuestion question, InterviewSession session) {
        return toSnapshot(fromQuestion(question, session));
    }

    public static Map<String, Object> toSnapshot(QuestionDto dto) {
        if (dto == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionId", dto.getQuestionId());
        snapshot.put("questionNo", dto.getQuestionNo());
        snapshot.put("questionType", dto.getQuestionType());
        snapshot.put("domainCode", dto.getDomainCode());
        snapshot.put("domainName", dto.getDomainName());
        snapshot.put("stem", dto.getStem());
        snapshot.put("targetSkill", dto.getTargetSkill());
        snapshot.put("aiResultStatus", dto.getAiResultStatus());
        snapshot.put("hintAvailable", dto.getHintAvailable());
        return snapshot;
    }

    private static String resolveDomainName(InterviewQuestion question, InterviewSession session) {
        String contextDomainName = readContextString(question, "domainName");
        if (hasText(contextDomainName)) {
            return contextDomainName;
        }

        String canonicalDomainCode = firstNonBlank(
                readContextString(question, "domainCode"),
                question.getDomainCode()
        );
        if (hasText(canonicalDomainCode)) {
            String syllabusDomainName = resolveDomainNameFromSyllabusByCode(session, canonicalDomainCode);
            if (hasText(syllabusDomainName)) {
                return syllabusDomainName;
            }
            String specialDomainName = InterviewDomainDisplaySupport.resolveSpecialDomainName(canonicalDomainCode);
            if (hasText(specialDomainName)) {
                return specialDomainName;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String resolveDomainNameFromSyllabusByCode(InterviewSession session, String domainCode) {
        if (session == null || session.getSyllabusJson() == null || !hasText(domainCode)) {
            return "";
        }
        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return "";
        }
        for (Object domainObj : domains) {
            if (!(domainObj instanceof Map<?, ?> domainMap)) {
                continue;
            }
            if (domainCode.equals(toStr(domainMap.get("domainCode")))) {
                return toStr(domainMap.get("domainName"));
            }
        }
        return "";
    }

    private static String resolveAiResultStatus(InterviewQuestion question) {
        return readContextString(question, "aiResultStatus");
    }

    private static String readContextString(InterviewQuestion question, String key) {
        if (question == null || question.getGenerationContextJson() == null) {
            return "";
        }
        return toStr(question.getGenerationContextJson().get(key));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Long l) {
            return l;
        }
        if (val instanceof Integer i) {
            return i.longValue();
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Integer i) {
            return i;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String toStr(Object val) {
        if (val == null) {
            return "";
        }
        return String.valueOf(val);
    }
}
