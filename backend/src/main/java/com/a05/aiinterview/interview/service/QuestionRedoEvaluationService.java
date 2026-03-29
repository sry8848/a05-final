package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.interview.entity.QuestionRedoAttempt;
import com.a05.aiinterview.interview.mapper.QuestionRedoAttemptMapper;
import com.a05.aiinterview.interview.service.support.HighlightedAnnotationLocator;
import com.a05.aiinterview.interview.service.support.QuestionDetailEvaluationScoreSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单题重答评估服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionRedoEvaluationService {

    private final AiClient aiClient;
    private final QuestionRedoAttemptMapper questionRedoAttemptMapper;
    private final ObjectMapper objectMapper;

    public void evaluateByRedoAttemptId(Long redoAttemptId) {
        if (redoAttemptId == null) {
            return;
        }

        QuestionRedoAttempt redoAttempt = questionRedoAttemptMapper.selectById(redoAttemptId);
        if (redoAttempt == null) {
            log.warn("单题重答评估跳过：redoAttempt 不存在, redoAttemptId={}", redoAttemptId);
            return;
        }

        String currentStatus = normalizeStatus(redoAttempt.getEvaluationStatus());
        if (QuestionDetailEvaluationService.STATUS_READY.equals(currentStatus)) {
            return;
        }

        if (!QuestionDetailEvaluationService.STATUS_GENERATING.equals(currentStatus)) {
            patchStatus(redoAttemptId, QuestionDetailEvaluationService.STATUS_GENERATING, null);
        }

        try {
            QuestionDetailEvaluationOutput output = QuestionDetailEvaluationScoreSupport.clampToPercentageRange(
                    aiClient.callQuestionDetailEvaluation(QuestionDetailEvaluationInputFactory.fromRedoAttempt(redoAttempt))
                            .getOutput()
            );
            output = HighlightedAnnotationLocator.resolve(redoAttempt.getAnswerText(), output);
            Map<String, Object> stableJson = objectMapper.convertValue(output, Map.class);
            patchStatus(redoAttemptId, QuestionDetailEvaluationService.STATUS_READY, stableJson);
            log.info("单题重答评估完成, redoAttemptId={}, sourceSessionId={}, sourceQuestionId={}",
                    redoAttemptId, redoAttempt.getSourceSessionId(), redoAttempt.getSourceQuestionId());
        } catch (Exception ex) {
            patchStatus(redoAttemptId, QuestionDetailEvaluationService.STATUS_FAILED, null);
            log.error("单题重答评估失败, redoAttemptId={}, sourceSessionId={}, sourceQuestionId={}",
                    redoAttemptId, redoAttempt.getSourceSessionId(), redoAttempt.getSourceQuestionId(), ex);
        }
    }

    private void patchStatus(Long redoAttemptId, String status, Map<String, Object> evaluationJson) {
        QuestionRedoAttempt update = new QuestionRedoAttempt();
        update.setId(redoAttemptId);
        update.setEvaluationStatus(status);
        if (evaluationJson != null) {
            update.setEvaluationJson(new LinkedHashMap<>(evaluationJson));
        }
        questionRedoAttemptMapper.updateById(update);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return QuestionDetailEvaluationService.STATUS_PENDING;
        }
        return status.trim().toLowerCase();
    }
}
