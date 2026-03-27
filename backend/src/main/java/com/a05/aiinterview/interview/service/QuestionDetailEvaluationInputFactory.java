package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationInput;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.QuestionRedoAttempt;
import com.a05.aiinterview.interview.service.support.InterviewDomainDisplaySupport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单题详细评估输入组装工具。
 * 统一 interview attempt 与 question redo attempt 两条链路的 prompt 入参。
 */
public final class QuestionDetailEvaluationInputFactory {

    private QuestionDetailEvaluationInputFactory() {
    }

    public static QuestionDetailEvaluationInput fromInterviewAttempt(
            InterviewSession session,
            InterviewQuestion question,
            InterviewAttempt attempt,
            List<QuestionDetailEvaluationInput.QaContext> recentContext) {
        String domainCode = extractDomainCode(question.getGenerationContextJson());
        String domainName = firstNonBlank(
                extractDomainName(question.getGenerationContextJson()),
                resolveDomainName(session == null ? null : session.getSyllabusJson(), domainCode),
                InterviewDomainDisplaySupport.resolveQuestionTypeLabel(question.getQuestionType())
        );
        return QuestionDetailEvaluationInput.builder()
                .interviewId(session.getId())
                .questionId(question.getId())
                .positionCode(session.getPositionCode())
                .experienceLevel(session.getExperienceLevel())
                .mode(session.getMode())
                .questionStem(question.getStem())
                .questionType(question.getQuestionType())
                .domainCode(domainCode)
                .domainName(domainName)
                .answerText(attempt.getAnswerText())
                .expectedPoints(question.getExpectedPoints())
                .recentContext(recentContext != null ? recentContext : List.of())
                .build();
    }

    public static QuestionDetailEvaluationInput fromRedoAttempt(QuestionRedoAttempt attempt) {
        Map<String, Object> snapshot = attempt.getSourceSnapshotJson() != null ? attempt.getSourceSnapshotJson() : Map.of();
        return QuestionDetailEvaluationInput.builder()
                .interviewId(attempt.getSourceSessionId())
                .questionId(attempt.getSourceQuestionId())
                .positionCode(asString(snapshot.get("positionCode")))
                .experienceLevel(asString(snapshot.get("experienceLevel")))
                .mode(asString(snapshot.get("mode")))
                .questionStem(asString(snapshot.get("questionStem")))
                .questionType(asString(snapshot.get("questionType")))
                .domainCode(asString(snapshot.get("domainCode")))
                .domainName(asString(snapshot.get("domainName")))
                .answerText(attempt.getAnswerText())
                .expectedPoints(asStringList(snapshot.get("expectedPoints")))
                .recentContext(List.of())
                .build();
    }

    public static String extractDomainCode(Map<String, Object> generationContextJson) {
        return asString(generationContextJson == null ? null : generationContextJson.get("domainCode"));
    }

    public static String extractDomainName(Map<String, Object> generationContextJson) {
        return asString(generationContextJson == null ? null : generationContextJson.get("domainName"));
    }

    @SuppressWarnings("unchecked")
    public static String resolveDomainName(Map<String, Object> syllabusJson, String domainCode) {
        if (syllabusJson == null || domainCode == null || domainCode.isBlank()) {
            return "";
        }
        Object domainsObj = syllabusJson.get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return "";
        }
        Optional<String> name = domains.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(map -> domainCode.equals(String.valueOf(map.get("domainCode"))))
                .map(map -> map.get("domainName"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst();
        return name.orElse(InterviewDomainDisplaySupport.resolveSpecialDomainName(domainCode));
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .map(QuestionDetailEvaluationInputFactory::asString)
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String asString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
