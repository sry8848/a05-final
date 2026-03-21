package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.interview.dto.InterviewQuestionReviewDto;
import com.a05.aiinterview.interview.dto.QuestionDtoAssembler;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 单题复盘应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionReviewService {

    private static final String EVALUATION_PENDING = "pending";
    private static final String EVALUATION_GENERATING = "generating";
    private static final String EVALUATION_READY = "ready";
    private static final String EVALUATION_FAILED = "failed";

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final ObjectMapper objectMapper;

    /**
     * 查询单题复盘详情。
     */
    public InterviewQuestionReviewDto getQuestionReview(Long sessionId, Long questionId, Long userId) {
        log.info("查询单题复盘, sessionId={}, questionId={}, userId={}", sessionId, questionId, userId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }

        InterviewQuestion question = interviewQuestionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new IllegalArgumentException("题目不存在或不属于该会话");
        }

        InterviewQuestionReviewDto dto = new InterviewQuestionReviewDto();
        dto.setQuestionId(question.getId());
        dto.setQuestionNo(question.getQuestionNo());
        dto.setQuestionStem(question.getStem());
        dto.setQuestionType(question.getQuestionType());
        dto.setDomainName(QuestionDtoAssembler.fromQuestion(question, session).getDomainName());

        InterviewAttempt latestFinalAttempt = interviewAttemptMapper.selectLatestFinalAttempt(sessionId, questionId);
        if (latestFinalAttempt == null) {
            dto.setAnswerStatus("pending");
            dto.setEvaluationStatus(EVALUATION_PENDING);
            dto.setUserAnswer(null);
            dto.setScore(null);
            dto.setCommentary(null);
            dto.setBackfillFromLocalAllowed(Boolean.TRUE);
            return dto;
        }

        InterviewAttempt attempt = latestFinalAttempt;
        dto.setAnswerStatus("[skip]".equals(attempt.getAnswerText()) ? "skipped" : "answered");
        dto.setUserAnswer(attempt.getAnswerText());
        String evaluationStatus = normalizeEvaluationStatus(attempt.getDetailEvaluationStatus());
        dto.setEvaluationStatus(evaluationStatus);
        dto.setBackfillFromLocalAllowed(!EVALUATION_READY.equals(evaluationStatus));

        if (EVALUATION_READY.equals(evaluationStatus) && attempt.getDetailEvaluationJson() != null) {
            mapReadyDetail(dto, attempt.getDetailEvaluationJson());
        } else {
            clearDetailFields(dto);
        }

        return dto;
    }

    private void mapReadyDetail(InterviewQuestionReviewDto dto, Map<String, Object> detailJson) {
        try {
            QuestionDetailEvaluationOutput output =
                    objectMapper.convertValue(detailJson, QuestionDetailEvaluationOutput.class);
            dto.setScore(output.getScore());
            dto.setCommentary(output.getCommentary());
            dto.setStrengthPoints(output.getStrengthPoints());
            dto.setWeakPoints(output.getWeakPoints());
            dto.setIdealAnswerOutline(output.getIdealAnswerOutline());
            dto.setRewrittenAnswer(output.getRewrittenAnswer());
            if (output.getEvaluatedDomains() != null) {
                dto.setEvaluatedDomains(output.getEvaluatedDomains().stream().map(item -> {
                    InterviewQuestionReviewDto.EvaluatedDomainDto mapped = new InterviewQuestionReviewDto.EvaluatedDomainDto();
                    mapped.setDomainCode(item.getDomainCode());
                    mapped.setDomainName(item.getDomainName());
                    mapped.setScore(item.getScore());
                    mapped.setCommentary(item.getCommentary());
                    return mapped;
                }).toList());
            }
            if (output.getHighlightedSegments() != null) {
                dto.setHighlightedSegments(output.getHighlightedSegments().stream().map(item -> {
                    InterviewQuestionReviewDto.HighlightedSegmentDto mapped = new InterviewQuestionReviewDto.HighlightedSegmentDto();
                    mapped.setSegment(item.getSegment());
                    mapped.setLabel(item.getLabel());
                    mapped.setComment(item.getComment());
                    return mapped;
                }).toList());
            }
        } catch (Exception ex) {
            log.warn("单题复盘 detailEvaluationJson 解析失败，按 pending 字段返回", ex);
            clearDetailFields(dto);
        }
    }

    private void clearDetailFields(InterviewQuestionReviewDto dto) {
        dto.setScore(null);
        dto.setCommentary(null);
        dto.setStrengthPoints(null);
        dto.setWeakPoints(null);
        dto.setEvaluatedDomains(null);
        dto.setHighlightedSegments(null);
        dto.setIdealAnswerOutline(null);
        dto.setRewrittenAnswer(null);
    }

    private String normalizeEvaluationStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return EVALUATION_PENDING;
        }
        String status = rawStatus.trim().toLowerCase();
        if (EVALUATION_READY.equals(status)) {
            return EVALUATION_READY;
        }
        if (EVALUATION_GENERATING.equals(status)) {
            return EVALUATION_GENERATING;
        }
        if (EVALUATION_FAILED.equals(status)) {
            return EVALUATION_FAILED;
        }
        return EVALUATION_PENDING;
    }

}
