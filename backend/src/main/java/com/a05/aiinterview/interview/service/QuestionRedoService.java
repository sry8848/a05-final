package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptDto;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptRequest;
import com.a05.aiinterview.interview.dto.QuestionDto;
import com.a05.aiinterview.interview.dto.QuestionDtoAssembler;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.QuestionRedoAttempt;
import com.a05.aiinterview.interview.event.QuestionRedoAttemptPersistedEvent;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.QuestionRedoAttemptMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单题重答应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionRedoService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final QuestionRedoAttemptMapper questionRedoAttemptMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public QuestionRedoAttemptDto createRedoAttempt(Long sessionId, Long questionId, Long userId, QuestionRedoAttemptRequest request) {
        InterviewSession session = requireOwnedSession(sessionId, userId);
        InterviewQuestion question = requireSourceQuestion(sessionId, questionId);

        QuestionRedoAttempt redoAttempt = new QuestionRedoAttempt();
        redoAttempt.setUserId(userId);
        redoAttempt.setSourceSessionId(sessionId);
        redoAttempt.setSourceQuestionId(questionId);
        redoAttempt.setSourceSnapshotJson(buildSourceSnapshot(session, question));
        redoAttempt.setAnswerText(request.getAnswerText().trim());
        redoAttempt.setEvaluationStatus(QuestionDetailEvaluationService.STATUS_PENDING);
        redoAttempt.setCreatedAt(LocalDateTime.now());
        redoAttempt.setUpdatedAt(LocalDateTime.now());
        questionRedoAttemptMapper.insert(redoAttempt);

        eventPublisher.publishEvent(new QuestionRedoAttemptPersistedEvent(
                redoAttempt.getId(),
                sessionId,
                questionId,
                userId
        ));
        log.info("单题重答已创建, redoAttemptId={}, sessionId={}, questionId={}, userId={}",
                redoAttempt.getId(), sessionId, questionId, userId);
        return toDto(redoAttempt);
    }

    public QuestionRedoAttemptDto getLatestRedoAttempt(Long sessionId, Long questionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        requireSourceQuestion(sessionId, questionId);

        QuestionRedoAttempt latest = questionRedoAttemptMapper.selectLatestBySource(userId, sessionId, questionId);
        if (latest == null) {
            return null;
        }
        return toDto(latest);
    }

    private InterviewSession requireOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }
        return session;
    }

    private InterviewQuestion requireSourceQuestion(Long sessionId, Long questionId) {
        InterviewQuestion question = interviewQuestionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new IllegalArgumentException("题目不存在或不属于该会话");
        }
        return question;
    }

    private Map<String, Object> buildSourceSnapshot(InterviewSession session, InterviewQuestion question) {
        QuestionDto questionDto = QuestionDtoAssembler.fromQuestion(question, session);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionId", question.getId());
        snapshot.put("questionNo", question.getQuestionNo());
        snapshot.put("questionStem", question.getStem());
        snapshot.put("questionType", question.getQuestionType());
        String domainCode = QuestionDetailEvaluationInputFactory.extractDomainCode(question.getGenerationContextJson());
        if (domainCode == null || domainCode.isBlank()) {
            domainCode = question.getDomainCode();
        }
        snapshot.put("domainCode", domainCode);
        snapshot.put("domainName", questionDto != null ? questionDto.getDomainName() : "");
        snapshot.put("targetSkill", question.getTargetSkill());
        snapshot.put("expectedPoints", question.getExpectedPoints() != null ? question.getExpectedPoints() : List.of());
        snapshot.put("positionCode", session.getTargetRole());
        snapshot.put("experienceLevel", session.getExperienceLevel());
        snapshot.put("mode", session.getMode());
        return snapshot;
    }

    private QuestionRedoAttemptDto toDto(QuestionRedoAttempt attempt) {
        QuestionRedoAttemptDto dto = new QuestionRedoAttemptDto();
        dto.setRedoAttemptId(attempt.getId());
        dto.setEvaluationStatus(normalizeStatus(attempt.getEvaluationStatus()));
        dto.setAnswerText(attempt.getAnswerText());
        dto.setCreatedAt(attempt.getCreatedAt());

        if (!QuestionDetailEvaluationService.STATUS_READY.equals(dto.getEvaluationStatus())
                || attempt.getEvaluationJson() == null) {
            return dto;
        }

        try {
            QuestionDetailEvaluationOutput output =
                    OBJECT_MAPPER.convertValue(attempt.getEvaluationJson(), QuestionDetailEvaluationOutput.class);
            dto.setScore(output.getScore());
            dto.setCommentary(output.getCommentary());
            dto.setStrengthPoints(output.getStrengthPoints());
            dto.setWeakPoints(output.getWeakPoints());
            dto.setIdealAnswerOutline(output.getIdealAnswerOutline());
            dto.setRewrittenAnswer(output.getRewrittenAnswer());
            if (output.getEvaluatedDomains() != null) {
                dto.setEvaluatedDomains(output.getEvaluatedDomains().stream().map(item -> {
                    QuestionRedoAttemptDto.EvaluatedDomainDto mapped = new QuestionRedoAttemptDto.EvaluatedDomainDto();
                    mapped.setDomainCode(item.getDomainCode());
                    mapped.setDomainName(item.getDomainName());
                    mapped.setScore(item.getScore());
                    mapped.setCommentary(item.getCommentary());
                    return mapped;
                }).toList());
            }
            if (output.getHighlightedSegments() != null) {
                dto.setHighlightedSegments(output.getHighlightedSegments().stream().map(item -> {
                    QuestionRedoAttemptDto.HighlightedSegmentDto mapped = new QuestionRedoAttemptDto.HighlightedSegmentDto();
                    mapped.setSegment(item.getSegment());
                    mapped.setLabel(item.getLabel());
                    mapped.setComment(item.getComment());
                    return mapped;
                }).toList());
            }
        } catch (Exception ex) {
            log.warn("单题重答 evaluationJson 解析失败, redoAttemptId={}", attempt.getId(), ex);
        }
        return dto;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return QuestionDetailEvaluationService.STATUS_PENDING;
        }
        return status.trim().toLowerCase();
    }
}
