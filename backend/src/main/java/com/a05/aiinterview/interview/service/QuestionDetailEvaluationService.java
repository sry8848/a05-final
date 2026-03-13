package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationInput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 单题详细评估服务。
 * 由 final attempt 提交事务 AFTER_COMMIT 触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionDetailEvaluationService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    private final AiClient aiClient;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 按 attempt 主键触发单题详细评估。
     */
    public void evaluateByAttemptId(Long attemptDbId) {
        if (attemptDbId == null) {
            return;
        }

        InterviewAttempt attempt = interviewAttemptMapper.selectById(attemptDbId);
        if (attempt == null) {
            log.warn("单题详细评估跳过：attempt 不存在, attemptDbId={}", attemptDbId);
            return;
        }

        if (!Boolean.TRUE.equals(attempt.getIsFinal())) {
            // non-final attempt 固定 pending，不触发详细评估。
            patchStatus(attempt.getId(), STATUS_PENDING, null);
            return;
        }

        String currentStatus = normalizeStatus(attempt.getDetailEvaluationStatus());
        if (STATUS_READY.equals(currentStatus)) {
            return;
        }

        // failed 不自动重试；只在显式触发或首次触发路径进入 generating。
        if (!STATUS_PENDING.equals(currentStatus) && !STATUS_FAILED.equals(currentStatus)
                && !STATUS_GENERATING.equals(currentStatus)) {
            patchStatus(attempt.getId(), STATUS_PENDING, null);
            currentStatus = STATUS_PENDING;
        }

        if (!STATUS_GENERATING.equals(currentStatus)) {
            patchStatus(attempt.getId(), STATUS_GENERATING, null);
        }

        try {
            InterviewSession session = interviewSessionMapper.selectById(attempt.getSessionId());
            InterviewQuestion question = interviewQuestionMapper.selectById(attempt.getQuestionId());
            if (session == null || question == null) {
                throw new IllegalStateException("session 或 question 不存在");
            }

            QuestionDetailEvaluationInput input = buildInput(session, question, attempt);
            QuestionDetailEvaluationOutput output = aiClient.callQuestionDetailEvaluation(input).getOutput();

            Map<String, Object> stableJson = objectMapper.convertValue(output, Map.class);
            patchStatus(attempt.getId(), STATUS_READY, stableJson);
            log.info("单题详细评估完成, sessionId={}, questionId={}, attemptId={}",
                    attempt.getSessionId(), attempt.getQuestionId(), attempt.getAttemptId());
        } catch (Exception ex) {
            // 一期 failed 仅用于展示，不自动重试。
            patchStatus(attempt.getId(), STATUS_FAILED, null);
            log.error("单题详细评估失败, sessionId={}, questionId={}, attemptId={}",
                    attempt.getSessionId(), attempt.getQuestionId(), attempt.getAttemptId(), ex);
        }
    }

    private QuestionDetailEvaluationInput buildInput(
            InterviewSession session, InterviewQuestion question, InterviewAttempt attempt) {
        String domainCode = extractDomainCode(question.getGenerationContextJson());
        String domainName = resolveDomainName(session.getSyllabusJson(), domainCode);

        return QuestionDetailEvaluationInput.builder()
                .interviewId(session.getId())
                .questionId(question.getId())
                .positionCode(session.getTargetRole())
                .experienceLevel(session.getExperienceLevel())
                .mode(session.getMode())
                .questionStem(question.getStem())
                .questionType(question.getQuestionType())
                .domainCode(domainCode)
                .domainName(domainName)
                .targetDepth(question.getTargetDepth())
                .answerText(attempt.getAnswerText())
                .expectedPoints(question.getExpectedPoints())
                .recentContext(buildRecentContext(session.getId(), session.getContextWindowSize()))
                .build();
    }

    private List<QuestionDetailEvaluationInput.QaContext> buildRecentContext(Long sessionId, Integer windowSize) {
        int window = windowSize != null && windowSize > 0 ? windowSize : 5;
        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)
        );
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        List<InterviewAttempt> attempts = interviewAttemptMapper.selectBySessionId(sessionId);
        Map<Long, InterviewAttempt> latestFinalByQuestion = (attempts == null ? List.<InterviewAttempt>of() : attempts).stream()
                .filter(a -> a != null && Boolean.TRUE.equals(a.getIsFinal()))
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        a -> a,
                        (left, right) -> compareAttempt(left, right) >= 0 ? left : right
                ));

        int start = Math.max(0, questions.size() - window);
        List<QuestionDetailEvaluationInput.QaContext> contexts = new ArrayList<>();
        for (int i = start; i < questions.size(); i++) {
            InterviewQuestion q = questions.get(i);
            InterviewAttempt matched = latestFinalByQuestion.get(q.getId());
            contexts.add(QuestionDetailEvaluationInput.QaContext.builder()
                    .stem(q.getStem())
                    .answer(matched != null ? matched.getAnswerText() : null)
                    .questionType(q.getQuestionType())
                    .domainCode(extractDomainCode(q.getGenerationContextJson()))
                    .build());
        }
        return contexts;
    }

    private int compareAttempt(InterviewAttempt left, InterviewAttempt right) {
        Comparator<InterviewAttempt> comparator = Comparator
                .comparing(InterviewAttempt::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(InterviewAttempt::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(left, right);
    }

    private void patchStatus(Long attemptId, String status, Map<String, Object> detailJson) {
        InterviewAttempt update = new InterviewAttempt();
        update.setId(attemptId);
        update.setDetailEvaluationStatus(status);
        if (detailJson != null) {
            update.setDetailEvaluationJson(new LinkedHashMap<>(detailJson));
        }
        interviewAttemptMapper.updateById(update);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PENDING;
        }
        return status.trim().toLowerCase();
    }

    private String extractDomainCode(Map<String, Object> generationContextJson) {
        if (generationContextJson == null) {
            return "";
        }
        Object domainCode = generationContextJson.get("domainCode");
        return domainCode instanceof String code ? code : "";
    }

    private String resolveDomainName(Map<String, Object> syllabusJson, String domainCode) {
        if (syllabusJson == null || domainCode == null || domainCode.isBlank()) {
            return domainCode;
        }
        Object domainsObj = syllabusJson.get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return domainCode;
        }
        Optional<String> name = domains.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(map -> domainCode.equals(String.valueOf(map.get("domainCode"))))
                .map(map -> map.get("domainName"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst();
        return name.orElse(domainCode);
    }
}
