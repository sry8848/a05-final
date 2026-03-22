package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.contract.EvaluationDecisionActionCatalog;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    private static final int MAX_QUOTA_COUNT = 20;
    private static final int RECENT_MEMORY_LIMIT = 6;

    private final AiClient aiClient;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final AnswerSubmitPersistenceService answerSubmitPersistenceService;
    private final ReportGenerationService reportGenerationService;
    private final InterviewDebugTraceService interviewDebugTraceService;

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

        EvaluationDecisionOutput evalOutput;
        AiCallResult<EvaluationDecisionOutput> evalCallResult = null;
        if (shouldForceEndByMaxQuestions(session)) {
                evalOutput = buildForcedEndDecision();
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
                evalCallResult = aiClient.callEvaluationDecision(evalInput);
                evalOutput = evalCallResult.getOutput();
            } catch (Exception e) {
                log.error("评估决策 AI 调用失败, sessionId={}, attemptId={}", sessionId, request.getAttemptId(), e);
                throw new RuntimeException("评估决策服务暂时不可用", e);
            }
        }

        EvaluationDecisionOutput normalized = normalizeEvaluationOutput(currentQuestion, evalOutput);
        interviewDebugTraceService.recordQuestionStage(
                sessionId,
                currentQuestion.getId(),
                request.getAttemptId(),
                "evaluationOutput",
                buildEvaluationDebugPayload(evalCallResult, normalized),
                Map.of(
                        "action", normalized.getInterviewAction(),
                        "nextQuestionType", normalized.getNextQuestionType(),
                        "nextFocus", firstNonBlank(normalized.getNextFocus(), "")
                )
        );
        AnswerSubmitPersistenceService.PersistedAttemptResult persisted =
                answerSubmitPersistenceService.persist(sessionId, currentQuestion, request, normalized);

        if ("WRAPUP".equalsIgnoreCase(normalized.getInterviewAction())) {
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
        return EvaluationDecisionInput.builder()
                .interviewId(session.getId())
                .currentQuestionId(currentQuestion.getId())
                .interview(buildInterviewMeta(session))
                .projectAndInternshipSummary(buildProjectAndInternshipSummary(session))
                .interviewGoalSummary(buildInterviewGoalSummary(session))
                .coveredKnowledgeSummary(buildCoveredKnowledgeSummary(session))
                .quotaSummary(buildQuotaSummary(session, allQuestions))
                .currentQuestion(buildCurrentQuestionContext(session, currentQuestion))
                .answerText(answerText)
                .expectedPoints(safeStringList(currentQuestion.getExpectedPoints()))
                .possibleFutureDirections(buildPossibleFutureDirections(session))
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
    private List<EvaluationDecisionInput.ProjectAndInternshipItem> buildProjectAndInternshipSummary(InterviewSession session) {
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
            result.add(EvaluationDecisionInput.ProjectAndInternshipItem.builder()
                    .itemType(asString(item.get("itemType")))
                    .itemName(asString(item.get("itemName")))
                    .resumeDescription(asString(item.get("resumeDescription")))
                    .techHooks(toStringList(item.get("techHooks")))
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private EvaluationDecisionInput.InterviewGoalSummary buildInterviewGoalSummary(InterviewSession session) {
        List<EvaluationDecisionInput.GoalDomainItem> domains = new ArrayList<>();
        Map<String, String> statusByCode = extractDomainStatusMap(session.getStateLedgerJson());
        Object raw = session.getSyllabusJson() != null ? session.getSyllabusJson().get("domains") : null;
        if (raw instanceof List<?> domainList) {
            for (Object domainObj : domainList) {
                if (!(domainObj instanceof Map<?, ?> domain)) {
                    continue;
                }
                String domainCode = asString(domain.get("domainCode"));
                String internalStatus = statusByCode.getOrDefault(domainCode, "UNASKED");
                String promptStatus = "COVERED".equalsIgnoreCase(internalStatus) ? "COVERED" : "UNASKED";
                domains.add(EvaluationDecisionInput.GoalDomainItem.builder()
                        .domainId(toLong(domain.get("domainId")))
                        .domainCode(domainCode)
                        .domainName(asString(domain.get("domainName")))
                        .focusPoints(toStringList(domain.get("focusPoints")))
                        .status(promptStatus)
                        .build());
            }
        }
        return EvaluationDecisionInput.InterviewGoalSummary.builder()
                .domains(domains)
                .build();
    }

    private List<String> buildCoveredKnowledgeSummary(InterviewSession session) {
        return toStringList(session.getStateLedgerJson() != null
                ? session.getStateLedgerJson().get("covered_points")
                : null);
    }

    private EvaluationDecisionInput.QuotaSummary buildQuotaSummary(InterviewSession session,
                                                                   List<InterviewQuestion> allQuestions) {
        Map<String, Object> quotaState = QuotaStateSupport.ensureQuotaState(
                session.getStateLedgerJson(),
                allQuestions
        );
        return EvaluationDecisionInput.QuotaSummary.builder()
                .samePointContinue(limitCounter(quotaState.get(QuotaStateSupport.SAME_POINT_CONTINUE)))
                .sameDomainContinue(limitCounter(quotaState.get(QuotaStateSupport.SAME_DOMAIN_CONTINUE)))
                .sameProjectPointContinue(limitCounter(quotaState.get(QuotaStateSupport.SAME_PROJECT_POINT_CONTINUE)))
                .sameProjectContinue(limitCounter(quotaState.get(QuotaStateSupport.SAME_PROJECT_CONTINUE)))
                .principleTotal(limitCounter(quotaState.get(QuotaStateSupport.PRINCIPLE_TOTAL)))
                .projectTotal(limitCounter(quotaState.get(QuotaStateSupport.PROJECT_TOTAL)))
                .scenarioTotal(limitCounter(quotaState.get(QuotaStateSupport.SCENARIO_TOTAL)))
                .behavioralTotal(limitCounter(quotaState.get(QuotaStateSupport.BEHAVIORAL_TOTAL)))
                .build();
    }

    private EvaluationDecisionInput.LimitCounter limitCounter(Object count) {
        return EvaluationDecisionInput.LimitCounter.builder()
                .count(QuotaStateSupport.toInt(count))
                .maxCount(MAX_QUOTA_COUNT)
                .build();
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
                .domainName(resolveDomainName(currentQuestion, session))
                .currentFocus(resolvePromptFocusPoint(currentQuestion))
                .relatedItemKey(firstNonBlank(asString(ctx.get("activeItemKey")), asString(session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_item_key") : null)))
                .relatedItemType(firstNonBlank(asString(ctx.get("activeItemType")), asString(session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_item_type") : null)))
                .relatedItemName(firstNonBlank(asString(ctx.get("activeItemName")), asString(session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_item_name") : null)))
                .build();
    }

    private List<String> buildPossibleFutureDirections(InterviewSession session) {
        List<String> directions = new ArrayList<>();
        directions.add("PRINCIPLE: 平移到同知识域的另一个可判分知识点");
        directions.add("PRINCIPLE: 切到尚未覆盖的知识域");
        if (!buildProjectAndInternshipSummary(session).isEmpty()) {
            directions.add("PROJECT_DEEP_DIVE: 回到真实项目中的一个具体实现细节");
        }
        directions.add("SCENARIO: 基于当前技术点给一个真实线上场景");
        directions.add("BEHAVIORAL: 追问真实协作事件和复盘");
        return directions;
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
                            .answerAssessment(asString(evaluationJson.get("answerAssessment")))
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

    private EvaluationDecisionOutput buildForcedEndDecision() {
        return EvaluationDecisionOutput.builder()
                .interviewAction("WRAPUP")
                .answerSummary("")
                .answerAssessment("已达到最大题目数限制")
                .decisionReason("已达到当前会话允许的最大题量，结束本场面试。")
                .candidateStrategies(List.of("结束面试"))
                .finalDecision("结束面试")
                .nextEntryAction("")
                .nextQuestionType("")
                .nextFocus("")
                .expectedAnswerPoints(List.of())
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();
    }

    private EvaluationDecisionOutput buildContractFallbackDecision(String answerAssessment, String decisionReason) {
        return EvaluationDecisionOutput.builder()
                .interviewAction("WRAPUP")
                .answerSummary("")
                .answerAssessment(answerAssessment)
                .decisionReason(decisionReason)
                .candidateStrategies(List.of("结束面试"))
                .finalDecision("结束面试")
                .nextEntryAction("")
                .nextQuestionType("")
                .nextFocus("")
                .expectedAnswerPoints(List.of())
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();
    }

    private EvaluationDecisionOutput normalizeEvaluationOutput(InterviewQuestion currentQuestion,
                                                               EvaluationDecisionOutput output) {
        if (output == null) {
            return buildContractFallbackDecision("AI 评估决策解析失败，已降级为结束面试。", "");
        }
        if (output.getInterviewAction() == null || output.getInterviewAction().isBlank()) {
            output.setInterviewAction("WRAPUP");
        }
        output.setInterviewAction(output.getInterviewAction().trim().toUpperCase(Locale.ROOT));
        if (output.getCandidateStrategies() == null) {
            output.setCandidateStrategies(List.of());
        }
        if (output.getNextEntryAction() == null) {
            output.setNextEntryAction("");
        } else {
            output.setNextEntryAction(output.getNextEntryAction().trim());
        }
        if (output.getExpectedAnswerPoints() == null) {
            output.setExpectedAnswerPoints(List.of());
        }
        if (output.getNewCoveredDomains() == null) {
            output.setNewCoveredDomains(List.of());
        }
        if (output.getNewCoveredPoints() == null) {
            output.setNewCoveredPoints(List.of());
        }
        if (output.getRetrievalPlans() == null) {
            output.setRetrievalPlans(List.of());
        }
        if ("CONTINUE".equalsIgnoreCase(output.getInterviewAction())
                && !EvaluationDecisionActionCatalog.isValidContinueDecision(
                currentQuestion != null ? currentQuestion.getQuestionType() : "",
                output.getFinalDecision(),
                output.getNextQuestionType())) {
            log.warn("评估决策动作与当前题型不匹配，但保留 CONTINUE 继续链路, questionType={}, finalDecision={}, nextQuestionType={}",
                    currentQuestion != null ? currentQuestion.getQuestionType() : "",
                    output.getFinalDecision(),
                    output.getNextQuestionType());
        }
        return output;
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

    private Map<String, Object> buildEvaluationDebugPayload(AiCallResult<EvaluationDecisionOutput> result,
                                                            EvaluationDecisionOutput parsedOutput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result != null) {
            payload.put("promptCode", result.getPromptCode());
            payload.put("promptVersion", result.getPromptVersion());
            payload.put("promptTokens", result.getPromptTokens());
            payload.put("responseTokens", result.getResponseTokens());
            payload.put("totalTokens", result.getPromptTokens() + result.getResponseTokens());
            payload.put("latencyMs", result.getLatencyMs());
            payload.put("systemPrompt", result.getSystemPrompt());
            payload.put("userPrompt", result.getUserPrompt());
            payload.put("rawResponse", result.getRawResponse());
        } else {
            payload.put("source", "forced");
        }
        payload.put("parsedOutput", parsedOutput);
        return payload;
    }

    private boolean shouldForceEndByMaxQuestions(InterviewSession session) {
        int maxQuestions = toInt(session.getStateLedgerJson() != null
                ? session.getStateLedgerJson().get("max_questions")
                : null);
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

    @SuppressWarnings("unchecked")
    private Map<String, String> extractDomainStatusMap(Map<String, Object> stateLedgerJson) {
        Map<String, String> result = new LinkedHashMap<>();
        if (stateLedgerJson == null) {
            return result;
        }
        Object raw = stateLedgerJson.get("domain_states");
        if (!(raw instanceof List<?> states)) {
            return result;
        }
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> state)) {
                continue;
            }
            result.put(asString(state.get("domainCode")), asString(state.get("status")));
        }
        return result;
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
        if (question.getDomainId() == null) {
            return "";
        }
        String domainCode = resolveDomainCode(question);
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
