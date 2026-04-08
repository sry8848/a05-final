package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.event.FinalAttemptPersistedEvent;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回答提交持久化事务服务，负责在数据库层面保存用户回答并更新状态。
 *
 * <p>核心职责（事务内原子操作）：
 * <ol>
 *   <li>调用 StateLedgerPatchService 应用状态账本变更</li>
 *   <li>保存 InterviewAttempt 回答记录到数据库</li>
 *   <li>更新 InterviewQuestion 状态为 answered/skipped</li>
 *   <li>如果决策为结束，更新 InterviewSession 状态为 report_generating</li>
 *   <li>如果是最终回答，发布 FinalAttemptPersistedEvent 事件</li>
 * </ol>
 *
 * <p>事务特性：使用 @Transactional 注解，确保所有操作要么全部成功，要么全部回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerSubmitPersistenceService {

    /** 详细评价状态：待处理 */
    public static final String DETAIL_STATUS_PENDING = "pending";

    private final InterviewAttemptMapper interviewAttemptMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final StateLedgerPatchService stateLedgerPatchService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 持久化用户回答的主方法，在单个事务内完成所有相关数据更新。
     *
     * <p>处理流程：
     * <ol>
     *   <li>更新状态账本：调用 StateLedgerPatchService 计算并应用新账本</li>
     *   <li>保存回答记录：构建并插入 InterviewAttempt</li>
     *   <li>更新题目状态：将题目标记为 answered 或 skipped</li>
     *   <li>结束会话：如果决策是 WRAPUP，将会话标记为 report_generating</li>
     *   <li>发布事件：如果是最终回答，发布 FinalAttemptPersistedEvent</li>
     * </ol>
     *
     * @param sessionId 面试会话 ID
     * @param currentQuestion 当前题目
     * @param request 提交回答请求
     * @param resolution 决策解析结果（包含AI决策和容错信息）
     * @return 持久化结果，包含回答记录ID、是否最终、是否结束等信息
     */
    @Transactional(rollbackFor = Exception.class)
    public PersistedAttemptResult persist(Long sessionId,
                                          InterviewQuestion currentQuestion,
                                          SubmitAttemptRequest request,
                                          DecisionResolution resolution) {
        // 步骤1：先更新状态账本，计算新的状态并持久化
        EvaluationDecisionOutput evalOutput = resolution.getEffectiveOutput();
        StateLedgerPatchService.ReductionAudit reductionAudit = stateLedgerPatchService.applyReduction(
                sessionId,
                evalOutput,
                resolution.getEffectivePlan(),
                currentQuestion,
                request.getAttemptId(),
                currentQuestion.getId(),
                request.getAnswerText()
        );

        // 步骤2：保存回答记录（InterviewAttempt）
        InterviewAttempt attempt = saveAttempt(sessionId, currentQuestion.getId(), request, resolution, reductionAudit);

        // 步骤3：更新题目状态（answered/skipped）
        markQuestionStatus(currentQuestion.getId(), request.getAnswerText());

        // 步骤4：如果决策是结束，更新会话状态为报告生成中
        boolean shouldEnd = "WRAPUP".equalsIgnoreCase(resolution.getEffectivePlan().getInterviewAction());
        if (shouldEnd) {
            log.info("决策结束面试，更新会话状态为报告生成中, sessionId={}", sessionId);
            markSessionFinishing(sessionId);
        }

        // 步骤5：如果是最终回答，发布事件（用于触发后续处理）
        boolean isFinal = Boolean.TRUE.equals(request.getIsFinal());
        if (isFinal) {
            log.info("检测到最终回答，发布持久化事件, sessionId={}, attemptId={}", sessionId, request.getAttemptId());
            eventPublisher.publishEvent(new FinalAttemptPersistedEvent(
                    sessionId,
                    currentQuestion.getId(),
                    attempt.getId(),
                    attempt.getAttemptId()
            ));
        }

        // 步骤6：返回持久化结果
        return PersistedAttemptResult.builder()
                .attemptDbId(attempt.getId())
                .attemptId(attempt.getAttemptId())
                .isFinal(isFinal)
                .shouldEnd(shouldEnd)
                .decision(resolution.getEffectivePlan().getInterviewAction())
                .build();
    }

    /**
     * 保存回答记录到数据库，构建完整的 evaluationJson 用于后续调试和审计。
     *
     * <p>evaluationJson 包含的关键信息：
     * <ul>
     *   <li>AI决策：interviewAction、finalDecision、nextFocus 等</li>
     *   <li>容错信息：decisionValidation、decisionRepairAudit（验证和修复的审计）</li>
     *   <li>状态账本：ledgerDiff、reducedLedger（账本变更前后）</li>
     *   <li>ASR信息：rawAsrText、asrCorrectionChanges（语音转文字）</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param questionId 题目 ID
     * @param request 提交回答请求
     * @param resolution 决策解析结果
     * @param reductionAudit 状态账本变更审计
     * @return 保存后的回答记录实体
     */
    private InterviewAttempt saveAttempt(Long sessionId,
                                         Long questionId,
                                         SubmitAttemptRequest request,
                                         DecisionResolution resolution,
                                         StateLedgerPatchService.ReductionAudit reductionAudit) {
        EvaluationDecisionOutput evalOutput = resolution.getEffectiveOutput();
        Map<String, Object> evaluationJson = new LinkedHashMap<>();

        // 基础AI决策字段
        evaluationJson.put("interviewAction", evalOutput.getInterviewAction());
        evaluationJson.put("decisionReason", evalOutput.getDecisionReason());
        evaluationJson.put("finalDecision", evalOutput.getFinalDecision());
        evaluationJson.put("nextFocus", evalOutput.getNextFocus());
        evaluationJson.put("nextItemType", evalOutput.getNextItemType());
        evaluationJson.put("nextItemName", evalOutput.getNextItemName());
        evaluationJson.put("nextProjectPoint", evalOutput.getNextProjectPoint());
        evaluationJson.put("newCoveredDomains", evalOutput.getNewCoveredDomains());
        evaluationJson.put("newCoveredPoints", evalOutput.getNewCoveredPoints());
        evaluationJson.put("retrievalPlans", evalOutput.getRetrievalPlans());

        // 容错和审计信息（验证、修复）
        evaluationJson.put("rawAiOutput", resolution.getRawOutput());
        evaluationJson.put("decisionValidation", resolution.getValidationAudit());
        evaluationJson.put("decisionRepairAudit", buildRepairAudit(resolution));
        evaluationJson.put("effectiveDecisionPlan", resolution.getEffectivePlan());
        evaluationJson.put("effectiveDecisionSource", resolution.getEffectivePlan() != null
                ? String.valueOf(resolution.getEffectivePlan().getEffectiveDecisionSource())
                : "");
        evaluationJson.put("terminationSource", resolution.getEffectivePlan() != null && resolution.getEffectivePlan().getTerminationSource() != null
                ? String.valueOf(resolution.getEffectivePlan().getTerminationSource())
                : "");
        evaluationJson.put("terminationReason", resolution.getEffectivePlan() != null && resolution.getEffectivePlan().getTerminationReason() != null
                ? String.valueOf(resolution.getEffectivePlan().getTerminationReason())
                : "");
        evaluationJson.put("repairAttempts", resolution.getRepairAttempts());

        // 状态账本变更信息（diff、新账本、知识域关闭原因）
        if (reductionAudit != null) {
            evaluationJson.put("ledgerDiff", reductionAudit.getDiff());
            evaluationJson.put("reducedLedger", reductionAudit.getNewLedger());
            evaluationJson.put("domainClosureReason", reductionAudit.getDomainClosureReason());
        }

        // ASR语音转文字信息（如果有）
        if (request.getRawAsrText() != null && !request.getRawAsrText().isBlank()) {
            evaluationJson.put("rawAsrText", request.getRawAsrText());
        }
        if (request.getAsrCorrectionChanges() != null && !request.getAsrCorrectionChanges().isEmpty()) {
            evaluationJson.put("asrCorrectionChanges", request.getAsrCorrectionChanges());
        }

        // 构建并插入 InterviewAttempt 记录
        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setAttemptId(request.getAttemptId());
        attempt.setAnswerText(request.getAnswerText());
        attempt.setIsFinal(Boolean.TRUE.equals(request.getIsFinal()));
        attempt.setEvaluationJson(evaluationJson);
        attempt.setDetailEvaluationStatus(DETAIL_STATUS_PENDING);
        attempt.setCreatedAt(LocalDateTime.now());
        interviewAttemptMapper.insert(attempt);
        return attempt;
    }

    /**
     * 构建决策修复的审计信息，用于调试和问题排查。
     *
     * @param resolution 决策解析结果
     * @return 修复审计Map，包含尝试次数、输入、输出、错误等
     */
    private Map<String, Object> buildRepairAudit(DecisionResolution resolution) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("repairAttempts", resolution.getRepairAttempts());
        audit.put("repairInput", resolution.getRepairInput());
        audit.put("repairOutput", resolution.getRepairedOutput());
        audit.put("repairErrors", resolution.getRepairErrors());
        return audit;
    }

    /**
     * 更新题目的状态，根据回答内容标记为 answered 或 skipped。
     *
     * @param questionId 题目 ID
     * @param answerText 用户回答文本，如果是 "[skip]" 则标记为跳过
     */
    private void markQuestionStatus(Long questionId, String answerText) {
        InterviewQuestion update = new InterviewQuestion();
        update.setId(questionId);
        update.setStatus("[skip]".equals(answerText) ? "skipped" : "answered");
        update.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.updateById(update);
    }

    /**
     * 将会话状态标记为报告生成中，同时记录结束时间。
     *
     * @param sessionId 会话 ID
     */
    private void markSessionFinishing(Long sessionId) {
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStatus("report_generating");
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);
    }

    /**
     * 持久化结果对象，用于返回给上层调用者。
     */
    @Data
    @Builder
    public static class PersistedAttemptResult {
        /** 回答记录数据库ID */
        private Long attemptDbId;
        /** 回答记录业务ID（attemptId） */
        private String attemptId;
        /** 是否为最终回答 */
        private boolean isFinal;
        /** 是否应该结束面试 */
        private boolean shouldEnd;
        /** 决策类型（CONTINUE/WRAPUP） */
        private String decision;
    }
}
