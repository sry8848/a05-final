package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 回答提交主服务。
 * 新版本直接围绕 evaluation-decision 新契约构建输入、落库和返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerSubmitService {

    private static final int RECENT_MEMORY_LIMIT = 6;

    private final AiClient aiClient;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final AnswerSubmitPersistenceService answerSubmitPersistenceService;
    private final ReportGenerationService reportGenerationService;
    private final InterviewDebugTraceService interviewDebugTraceService;
    private final RemainingDomainMenuBuilder remainingDomainMenuBuilder;
    private final AvailableStrategyAssembler availableStrategyAssembler;
    private final DecisionExecutionPlanBuilder decisionExecutionPlanBuilder;
    private final DecisionRepairOrchestrator decisionRepairOrchestrator;
    private final SystemFallbackPlanBuilder systemFallbackPlanBuilder;
    private final PlannerHistoryBuilderService plannerHistoryBuilderService;

    public SubmitAttemptResponse submitAnswer(Long sessionId, Long userId, SubmitAttemptRequest request) {
        log.info("提交回答主链路开始, sessionId={}, questionId={}, attemptId={}",
                sessionId, request.getQuestionId(), request.getAttemptId());

        InterviewAttempt existing = interviewAttemptMapper.selectByAttemptId(request.getAttemptId());
        if (existing != null) {
            return buildIdempotentResponse(existing);
        }

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        validateSession(session, userId, sessionId);

        InterviewQuestion currentQuestion = interviewQuestionMapper.selectById(request.getQuestionId());
        validateQuestion(currentQuestion, sessionId, request.getQuestionId());

        List<InterviewQuestion> allQuestions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)
        );
        List<InterviewAttempt> allAttempts = interviewAttemptMapper.selectBySessionId(sessionId);

        DecisionResolution resolution;
        if (shouldForceEndByMaxQuestions(session)) {
            resolution = buildForcedEndResolution();
        } else {
            EvaluationDecisionInput evalInput = buildEvaluationInput(
                    session, currentQuestion, allQuestions, allAttempts, request.getAnswerText());
            interviewDebugTraceService.recordQuestionStage(
                    sessionId,
                    currentQuestion.getId(),
                    request.getAttemptId(),
                    "evaluationInput",
                    evalInput,
                    Map.of(
                            "questionType", currentQuestion.getQuestionType(),
                            "expectedPointsCount", evalInput.getExpectedPoints() == null ? 0 : evalInput.getExpectedPoints().size()
                    )
            );
            try {
                AiCallResult<EvaluationDecisionOutput> evalCallResult = aiClient.callEvaluationDecision(evalInput);
                resolution = resolveDecision(session, currentQuestion, evalInput, evalCallResult);
            } catch (Exception e) {
                log.error("评估决策 AI 调用失败, sessionId={}, attemptId={}", sessionId, request.getAttemptId(), e);
                throw new RuntimeException("评估决策服务暂时不可用", e);
            }
        }
        interviewDebugTraceService.recordQuestionStage(
                sessionId,
                currentQuestion.getId(),
                request.getAttemptId(),
                "evaluationOutput",
                buildEvaluationDebugPayload(resolution),
                Map.of(
                        "action", resolution.getEffectivePlan().getInterviewAction(),
                        "targetQuestionType", firstNonBlank(resolution.getEffectivePlan().getTargetQuestionType(), ""),
                        "nextFocus", firstNonBlank(resolution.getEffectivePlan().getNextFocus(), ""),
                        "source", resolution.getEffectivePlan().getEffectiveDecisionSource()
                )
        );
        AnswerSubmitPersistenceService.PersistedAttemptResult persisted =
                answerSubmitPersistenceService.persist(sessionId, currentQuestion, request, resolution);

        if ("WRAPUP".equalsIgnoreCase(resolution.getEffectivePlan().getInterviewAction())) {
            reportGenerationService.generateAsync(sessionId);
            InterviewSession endSession = interviewSessionMapper.selectById(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .decision("wrapup")
                    .streamAttemptId(null)
                    .sessionStatus(endSession != null ? endSession.getStatus() : "report_generating")
                    .build();
        }

        InterviewSession updatedSession = interviewSessionMapper.selectById(sessionId);
        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .decision("continue")
                .streamAttemptId(request.getAttemptId())
                .sessionStatus(updatedSession != null ? updatedSession.getStatus() : "in_progress")
                .build();
    }

    private EvaluationDecisionInput buildEvaluationInput(InterviewSession session,
                                                         InterviewQuestion currentQuestion,
                                                         List<InterviewQuestion> allQuestions,
                                                         List<InterviewAttempt> allAttempts,
                                                         String answerText) {
        int maxQuestions = ensureMaxQuestions(session);
        Map<PlannerHistoryBuilderService.ProjectIdentity, List<String>> blockedProjectEntryPoints =
                plannerHistoryBuilderService.buildBlockedProjectEntryPoints(session);
        List<EvaluationDecisionInput.ProjectAndInternshipItem> projectSummary =
                buildProjectAndInternshipSummary(session, blockedProjectEntryPoints);
        List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains = remainingDomainMenuBuilder.build(session);
        Map<String, Object> quotaState = QuotaStateSupport.ensureQuotaState(session.getStateLedgerJson(), allQuestions);
        return EvaluationDecisionInput.builder()
                .interviewId(session.getId())
                .currentQuestionId(currentQuestion.getId())
                .interview(buildInterviewMeta(session))
                .questionIndex(resolveQuestionIndex(session, currentQuestion))
                .maxQuestions(maxQuestions)
                .quotaSnapshot(InterviewPacingSupport.buildQuotaSnapshot(session.getExperienceLevel(), quotaState))
                .projectAndInternshipSummary(projectSummary)
                .remainingTargetDomains(remainingTargetDomains)
                .coveredKnowledgeSummary(buildCoveredKnowledgeSummary(session))
                .crossSessionBlockedKnowledgePoints(plannerHistoryBuilderService.buildCrossSessionBlockedKnowledgePoints(session))
                .availableStrategies(availableStrategyAssembler.assemble(
                        currentQuestion != null ? currentQuestion.getQuestionType() : "",
                        !projectSummary.isEmpty(),
                        remainingTargetDomains,
                        quotaState,
                        session.getExperienceLevel()
                ))
                .currentQuestion(buildCurrentQuestionContext(session, currentQuestion))
                .answerText(answerText)
                .expectedPoints(safeStringList(currentQuestion.getExpectedPoints()))
                .retrievedMaterials(List.of())
                .recentInterviewMemory(buildRecentInterviewMemory(session, allQuestions, allAttempts))
                .build();
    }

    private EvaluationDecisionInput.InterviewMeta buildInterviewMeta(InterviewSession session) {
        return EvaluationDecisionInput.InterviewMeta.builder()
                .positionCode(session.getTargetRole())
                .experienceLevel(session.getExperienceLevel())
                .roundType("")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<EvaluationDecisionInput.ProjectAndInternshipItem> buildProjectAndInternshipSummary(
            InterviewSession session,
            Map<PlannerHistoryBuilderService.ProjectIdentity, List<String>> blockedProjectEntryPoints) {
        if (session.getSyllabusJson() == null) {
            return List.of();
        }
        Object raw = session.getSyllabusJson().get("experienceItems");
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<EvaluationDecisionInput.ProjectAndInternshipItem> result = new ArrayList<>();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            String itemType = asString(item.get("itemType"));
            String itemName = asString(item.get("itemName"));
            result.add(EvaluationDecisionInput.ProjectAndInternshipItem.builder()
                    .itemType(itemType)
                    .itemName(itemName)
                    .resumeDescription(asString(item.get("resumeDescription")))
                    .techHooks(toStringList(item.get("techHooks")))
                    .blockedEntryPoints(resolveBlockedEntryPoints(blockedProjectEntryPoints, itemType, itemName))
                    .build());
        }
        return result;
    }

    private List<String> resolveBlockedEntryPoints(
            Map<PlannerHistoryBuilderService.ProjectIdentity, List<String>> blockedProjectEntryPoints,
            String itemType,
            String itemName) {
        if (blockedProjectEntryPoints == null || blockedProjectEntryPoints.isEmpty()) {
            return List.of();
        }
        List<String> blocked = blockedProjectEntryPoints.get(
                new PlannerHistoryBuilderService.ProjectIdentity(itemType, itemName));
        return blocked == null ? List.of() : blocked;
    }

    private List<String> buildCoveredKnowledgeSummary(InterviewSession session) {
        return toStringList(session.getStateLedgerJson() != null
                ? session.getStateLedgerJson().get("covered_points")
                : null);
    }

    private EvaluationDecisionInput.CurrentQuestionContext buildCurrentQuestionContext(InterviewSession session,
                                                                                       InterviewQuestion currentQuestion) {
        Map<String, Object> ctx = currentQuestion.getGenerationContextJson() != null
                ? currentQuestion.getGenerationContextJson()
                : Map.of();
        return EvaluationDecisionInput.CurrentQuestionContext.builder()
                .stem(currentQuestion.getStem())
                .questionType(currentQuestion.getQuestionType())
                .domainId(currentQuestion.getDomainId())
                .domainCode(resolveDomainCode(currentQuestion))
                .domainName(resolveDomainName(currentQuestion, session))
                .currentFocus(resolvePromptFocusPoint(currentQuestion))
                .relatedItemKey(firstNonBlank(asString(ctx.get("activeItemKey")), asString(session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_item_key") : null)))
                .relatedItemType(firstNonBlank(asString(ctx.get("activeItemType")), asString(session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_item_type") : null)))
                .relatedItemName(firstNonBlank(asString(ctx.get("activeItemName")), asString(session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_item_name") : null)))
                .build();
    }

    private List<EvaluationDecisionInput.RecentInterviewMemoryItem> buildRecentInterviewMemory(
            InterviewSession session,
            List<InterviewQuestion> allQuestions,
            List<InterviewAttempt> allAttempts) {
        Map<Long, InterviewAttempt> latestFinalAttempts = latestFinalAttemptsByQuestion(allAttempts);
        return allQuestions.stream()
                .filter(question -> latestFinalAttempts.containsKey(question.getId()))
                .sorted(Comparator.comparing(InterviewQuestion::getQuestionNo).reversed())
                .limit(RECENT_MEMORY_LIMIT)
                .sorted(Comparator.comparing(InterviewQuestion::getQuestionNo))
                .map(question -> {
                    InterviewAttempt attempt = latestFinalAttempts.get(question.getId());
                    Map<String, Object> evaluationJson = attempt.getEvaluationJson() != null ? attempt.getEvaluationJson() : Map.of();
                    Map<String, Object> ctx = question.getGenerationContextJson() != null ? question.getGenerationContextJson() : Map.of();
                    String answerAssessment = "";
                    if (!"SYSTEM_FALLBACK".equalsIgnoreCase(asString(evaluationJson.get("effectiveDecisionSource")))) {
                        answerAssessment = firstNonBlank(
                                asString(evaluationJson.get("decisionReason")),
                                extractPlanDecisionReason(evaluationJson)
                        );
                    }
                    return EvaluationDecisionInput.RecentInterviewMemoryItem.builder()
                            .questionNo(question.getQuestionNo())
                            .questionType(question.getQuestionType())
                            .domainId(question.getDomainId())
                            .domainName(resolveDomainName(question, session))
                            .focusPoint(resolvePromptFocusPoint(question))
                            .relatedItemKey(asString(ctx.get("activeItemKey")))
                            .relatedItemType(asString(ctx.get("activeItemType")))
                            .relatedItemName(asString(ctx.get("activeItemName")))
                            .questionStem(question.getStem())
                            .answerSummary(summarizeAnswer(attempt.getAnswerText()))
                            .answerAssessment(answerAssessment)
                            .build();
                })
                .toList();
    }

    private Map<Long, InterviewAttempt> latestFinalAttemptsByQuestion(List<InterviewAttempt> allAttempts) {
        return allAttempts.stream()
                .filter(attempt -> attempt != null && Boolean.TRUE.equals(attempt.getIsFinal()))
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        attempt -> attempt,
                        (left, right) -> compareAttempt(left, right) >= 0 ? left : right
                ));
    }

    private int compareAttempt(InterviewAttempt left, InterviewAttempt right) {
        Comparator<InterviewAttempt> comparator = Comparator
                .comparing(InterviewAttempt::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(InterviewAttempt::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(left, right);
    }

    private DecisionResolution buildForcedEndResolution() {
        DecisionExecutionPlan plan = DecisionExecutionPlan.builder()
                .interviewAction("WRAPUP")
                .strategyCode(StrategyCode.S_WRAPUP.code())
                .targetQuestionType("")
                .nextFocus("")
                .targetDomainCode("")
                .targetDomainName("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .decisionReason("已达到当前会话允许的最大题量，结束本场面试。")
                .effectiveDecisionSource(DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI)
                .terminationSource(DecisionExecutionPlan.TerminationSource.MAX_QUESTIONS)
                .terminationReason(DecisionExecutionPlan.TerminationReason.MAX_QUESTIONS)
                .build();
        return DecisionResolution.builder()
                .repairAttempts(0)
                .effectivePlan(plan)
                .effectiveOutput(toEffectiveOutput(plan))
                .validationAudit(Map.of("status", "forced_wrapup"))
                .build();
    }

    private DecisionResolution resolveDecision(InterviewSession session,
                                               InterviewQuestion currentQuestion,
                                               EvaluationDecisionInput evalInput,
                                               AiCallResult<EvaluationDecisionOutput> evalCallResult) {
        EvaluationDecisionOutput rawOutput = extractRawOutput(evalCallResult);
        DecisionValidationResult initialValidation = decisionExecutionPlanBuilder.build(
                currentQuestion,
                rawOutput,
                evalInput.getRemainingTargetDomains(),
                extractStrategyCodes(evalInput),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );
        if (initialValidation.isValid()) {
            return DecisionResolution.builder()
                    .evalInput(evalInput)
                    .rawOutput(rawOutput)
                    .rawResponse(evalCallResult != null ? evalCallResult.getRawResponse() : null)
                    .validationErrors(List.of())
                    .repairAttempts(0)
                    .effectivePlan(initialValidation.getPlan())
                    .effectiveOutput(toEffectiveOutput(initialValidation.getPlan()))
                    .validationAudit(Map.of("status", "valid"))
                    .build();
        }

        DecisionRepairOrchestrator.RepairResult repairResult = decisionRepairOrchestrator.repair(
                evalInput,
                currentQuestion,
                initialValidation.getErrorCodes(),
                evalCallResult != null ? evalCallResult.getRawResponse() : null
        );
        if (repairResult.success()) {
            return DecisionResolution.builder()
                    .evalInput(evalInput)
                    .rawOutput(rawOutput)
                    .rawResponse(evalCallResult != null ? evalCallResult.getRawResponse() : null)
                    .validationErrors(initialValidation.getErrorCodes())
                    .repairInput(repairResult.repairInput())
                    .repairedOutput(repairResult.repairedOutput())
                    .repairAttempts(1)
                    .effectivePlan(repairResult.plan())
                    .effectiveOutput(toEffectiveOutput(repairResult.plan()))
                    .validationAudit(Map.of(
                            "status", "repaired",
                            "errors", initialValidation.getErrorCodes()
                    ))
                    .build();
        }

        Map<String, Object> fallbackState = DecisionFallbackStateSupport.ensureState(session.getStateLedgerJson());
        int rotationIndex = DecisionFallbackStateSupport.toInt(fallbackState.get(DecisionFallbackStateSupport.ROTATION_INDEX));
        Map<String, Object> previewState = DecisionFallbackStateSupport.previewAfterFallback(session.getStateLedgerJson());
        DecisionExecutionPlan fallbackPlan = DecisionFallbackStateSupport.shouldTerminateAfterFallback(previewState)
                ? systemFallbackPlanBuilder.buildSystemErrorPlan(session.getId(), currentQuestion, rotationIndex)
                : systemFallbackPlanBuilder.buildContinuePlan(session.getId(), currentQuestion, rotationIndex);

        return DecisionResolution.builder()
                .evalInput(evalInput)
                .rawOutput(rawOutput)
                .rawResponse(evalCallResult != null ? evalCallResult.getRawResponse() : null)
                .validationErrors(initialValidation.getErrorCodes())
                .repairInput(repairResult.repairInput())
                .repairedOutput(repairResult.repairedOutput())
                .repairErrors(repairResult.errorCodes())
                .repairAttempts(1)
                .effectivePlan(fallbackPlan)
                .effectiveOutput(toEffectiveOutput(fallbackPlan))
                .validationAudit(Map.of(
                        "status", "fallback_continue",
                        "errors", initialValidation.getErrorCodes(),
                        "repairErrors", repairResult.errorCodes()
                ))
                .build();
    }

    private SubmitAttemptResponse buildIdempotentResponse(InterviewAttempt existing) {
        Map<String, Object> evaluationJson = existing.getEvaluationJson() != null ? existing.getEvaluationJson() : Map.of();
        String interviewAction = asString(evaluationJson.get("interviewAction"));
        boolean wrapup = "WRAPUP".equalsIgnoreCase(interviewAction);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .decision(wrapup ? "wrapup" : "continue")
                .streamAttemptId(wrapup ? null : existing.getAttemptId())
                .sessionStatus(wrapup ? "report_generating" : "in_progress")
                .build();
    }

    private Map<String, Object> buildEvaluationDebugPayload(DecisionResolution resolution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (resolution == null) {
            return payload;
        }
        payload.put("rawAiOutput", resolution.getRawOutput());
        payload.put("rawResponse", resolution.getRawResponse());
        payload.put("decisionValidation", resolution.getValidationAudit());
        payload.put("repairAttempts", resolution.getRepairAttempts());
        payload.put("repairInput", resolution.getRepairInput());
        payload.put("repairOutput", resolution.getRepairedOutput());
        payload.put("effectiveDecisionPlan", resolution.getEffectivePlan());
        return payload;
    }

    private boolean shouldForceEndByMaxQuestions(InterviewSession session) {
        int maxQuestions = ensureMaxQuestions(session);
        if (maxQuestions <= 0) {
            return false;
        }
        int currentNo = session.getCurrentQuestionNo() != null ? session.getCurrentQuestionNo() : 0;
        return currentNo >= maxQuestions;
    }

    private void validateSession(InterviewSession session, Long userId, Long sessionId) {
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }
        if (!"in_progress".equals(session.getStatus())) {
            throw new IllegalArgumentException("面试会话状态异常，当前状态: " + session.getStatus());
        }
    }

    private void validateQuestion(InterviewQuestion question, Long sessionId, Long questionId) {
        if (question == null) {
            throw new IllegalArgumentException("题目不存在, questionId=" + questionId);
        }
        if (!question.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("题目不属于该会话");
        }
    }

    private String summarizeAnswer(String answerText) {
        if (answerText == null || answerText.isBlank() || "[skip]".equals(answerText)) {
            return "";
        }
        String normalized = answerText.trim().replace("\r", " ").replace("\n", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String resolveDomainCode(InterviewQuestion question) {
        if (question.getGenerationContextJson() == null) {
            return "";
        }
        Object domainCode = question.getGenerationContextJson().get("domainCode");
        return domainCode instanceof String code ? code : "";
    }

    @SuppressWarnings("unchecked")
    private String resolveDomainName(InterviewQuestion question, InterviewSession session) {
        String domainCode = resolveDomainCode(question);
        if (domainCode.isBlank() && question.getDomainId() == null) {
            return "";
        }
        if (session.getSyllabusJson() != null) {
            Object raw = session.getSyllabusJson().get("domains");
            if (raw instanceof List<?> domains) {
                for (Object domainObj : domains) {
                    if (!(domainObj instanceof Map<?, ?> domain)) {
                        continue;
                    }
                    if (Objects.equals(domainCode, asString(domain.get("domainCode")))) {
                        return asString(domain.get("domainName"));
                    }
                }
            }
        }
        return domainCode;
    }

    private String resolveActiveItemKey(InterviewQuestion question) {
        if (question.getGenerationContextJson() == null) {
            return "";
        }
        return asString(question.getGenerationContextJson().get("activeItemKey"));
    }

    private String resolvePromptFocusPoint(InterviewQuestion question) {
        if (question.getGenerationContextJson() == null) {
            return "";
        }
        return asString(question.getGenerationContextJson().get("focusPoint"));
    }

    private List<String> safeStringList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private int resolveQuestionIndex(InterviewSession session, InterviewQuestion currentQuestion) {
        if (currentQuestion != null && currentQuestion.getQuestionNo() != null && currentQuestion.getQuestionNo() > 0) {
            return currentQuestion.getQuestionNo();
        }
        return session != null && session.getCurrentQuestionNo() != null ? session.getCurrentQuestionNo() : 0;
    }

    private int ensureMaxQuestions(InterviewSession session) {
        Map<String, Object> ledger = session != null ? session.getStateLedgerJson() : null;
        int existing = toInt(ledger != null ? ledger.get(InterviewPacingSupport.MAX_QUESTIONS_KEY) : null);
        if (existing > 0) {
            return existing;
        }
        int computed = InterviewPacingSupport.maxQuestions(session != null ? session.getExperienceLevel() : null);
        Map<String, Object> updatedLedger = new LinkedHashMap<>();
        if (ledger != null) {
            updatedLedger.putAll(ledger);
        }
        updatedLedger.put(InterviewPacingSupport.MAX_QUESTIONS_KEY, computed);
        if (session != null) {
            session.setStateLedgerJson(updatedLedger);
            if (session.getId() != null) {
                InterviewSession update = new InterviewSession();
                update.setId(session.getId());
                update.setStateLedgerJson(updatedLedger);
                update.setUpdatedAt(LocalDateTime.now());
                interviewSessionMapper.updateById(update);
            }
        }
        return computed;
    }

    private List<String> extractStrategyCodes(EvaluationDecisionInput input) {
        if (input == null || input.getAvailableStrategies() == null) {
            return List.of();
        }
        return input.getAvailableStrategies().stream()
                .map(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
    }

    private EvaluationDecisionOutput extractRawOutput(AiCallResult<EvaluationDecisionOutput> callResult) {
        if (callResult == null) {
            return null;
        }
        String rawResponse = callResult.getRawResponse();
        if (rawResponse != null && !rawResponse.isBlank()) {
            try {
                return new ObjectMapper().readValue(rawResponse, EvaluationDecisionOutput.class);
            } catch (Exception ignored) {
                // fall through to validated output
            }
        }
        return callResult.getOutput();
    }

    private String extractPlanDecisionReason(Map<String, Object> evaluationJson) {
        if (evaluationJson == null) {
            return "";
        }
        Object rawPlan = evaluationJson.get("effectiveDecisionPlan");
        if (!(rawPlan instanceof Map<?, ?> plan)) {
            return "";
        }
        Object decisionReason = plan.get("decisionReason");
        return decisionReason == null ? "" : String.valueOf(decisionReason);
    }

    private EvaluationDecisionOutput toEffectiveOutput(DecisionExecutionPlan plan) {
        return EvaluationDecisionOutput.builder()
                .interviewAction(plan.getInterviewAction())
                .decisionReason(firstNonBlank(plan.getDecisionReason(), ""))
                .finalDecision(firstNonBlank(plan.getStrategyCode(), ""))
                .nextFocus(firstNonBlank(plan.getNextFocus(), ""))
                .nextItemType(firstNonBlank(plan.getNextItemType(), ""))
                .nextItemName(firstNonBlank(plan.getNextItemName(), ""))
                .nextProjectPoint(firstNonBlank(plan.getNextProjectPoint(), ""))
                .targetDomainCode(firstNonBlank(plan.getTargetDomainCode(), ""))
                .newCoveredDomains(plan.getNewCoveredDomains() == null ? List.of() : plan.getNewCoveredDomains())
                .newCoveredPoints(plan.getNewCoveredPoints() == null ? List.of() : plan.getNewCoveredPoints())
                .retrievalPlans(plan.getRetrievalPlans() == null ? List.of() : plan.getRetrievalPlans())
                .build();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
