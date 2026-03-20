package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 回答提交主服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerSubmitService {

    @Value("${interview.debug.enabled:false}")
    private boolean interviewDebugEnabled = false;

    @Value("${interview.debug.include-prompts:false}")
    private boolean interviewDebugIncludePrompts = false;

    @Value("${interview.debug.max-text-chars:1200}")
    private int interviewDebugMaxTextChars = 1200;

    private final AiClient aiClient;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final ResumeMapper resumeMapper;
    private final AnswerSubmitPersistenceService answerSubmitPersistenceService;
    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;

    public SubmitAttemptResponse submitAnswer(Long sessionId, Long userId, SubmitAttemptRequest request) {
        log.info("提交回答主链路开始, sessionId={}, questionId={}, attemptId={}",
                sessionId, request.getQuestionId(), request.getAttemptId());

        InterviewAttempt existing = interviewAttemptMapper.selectByAttemptId(request.getAttemptId());
        if (existing != null) {
            log.info("幂等命中，直接返回历史结果, attemptId={}", request.getAttemptId());
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
        List<InterviewAttempt> allAttempts = interviewAttemptMapper.selectList(
                new LambdaQueryWrapper<InterviewAttempt>()
                        .eq(InterviewAttempt::getSessionId, sessionId)
        );

        boolean forceEndByMaxQuestions = shouldForceEndByMaxQuestions(session);
        EvaluationDecisionOutput evalOutput;
        EvaluationDecisionOutput rawEvalOutput = null;
        AiCallResult<EvaluationDecisionOutput> evalCallResult = null;

        if (forceEndByMaxQuestions) {
            evalOutput = buildForcedEndDecision();
            log.info("达到最大题目数限制，强制结束面试, sessionId={}, currentQuestionNo={}, maxQuestions={}",
                    sessionId, session.getCurrentQuestionNo(), extractMaxQuestions(session.getStateLedgerJson()));
        } else {
            List<EvaluationDecisionInput.QaContext> recentContext =
                    buildRecentContext(allQuestions, allAttempts, session.getContextWindowSize());

            EvaluationDecisionInput evalInput = EvaluationDecisionInput.builder()
                    .interviewId(session.getId())
                    .variantId(null)
                    .positionCode(session.getTargetRole())
                    .experienceLevel(session.getExperienceLevel())
                    .mode(session.getMode())
                    .currentQuestionId(currentQuestion.getId())
                    .currentQuestionType(currentQuestion.getQuestionType())
                    .currentDomainCode(resolveDomainCode(currentQuestion, session))
                    .currentDomainName(resolveDomainName(currentQuestion, session))
                    .currentDomainId(currentQuestion.getDomainId())
                    .currentTargetDepth(currentQuestion.getTargetDepth())
                    .currentQuestionStem(currentQuestion.getStem())
                    .expectedPoints(currentQuestion.getExpectedPoints())
                    .answerText(request.getAnswerText())
                    .resumeText(fetchResumeText(session))
                    .pauseStats(request.getPauseStats())
                    .stateLedger(session.getStateLedgerJson())
                    .syllabusJson(session.getSyllabusJson())
                    .recentContext(recentContext)
                    .build();

            try {
                evalCallResult = aiClient.callEvaluationDecision(evalInput);
                evalOutput = evalCallResult.getOutput();
                rawEvalOutput = copyEvalOutput(evalOutput);
            } catch (Exception e) {
                log.error("评估决策 AI 调用失败, sessionId={}, attemptId={}", sessionId, request.getAttemptId(), e);
                throw new RuntimeException("评估决策服务暂时不可用", e);
            }
        }

        evalOutput = harmonizeEvaluationOutput(evalOutput, currentQuestion, session);
        evalOutput = applyBusinessGuardrails(evalOutput, currentQuestion, session);
        logAiDecisionRewrite(session, currentQuestion, evalCallResult, rawEvalOutput, evalOutput);
        validateFinalDecision(evalOutput, currentQuestion, session);

        AnswerSubmitPersistenceService.PersistedAttemptResult persisted =
                answerSubmitPersistenceService.persist(sessionId, currentQuestion, request, evalOutput);

        if ("wrapup".equalsIgnoreCase(evalOutput.getDecision())) {
            log.info("评估决策=wrapup，触发结束面试并异步生成报告, sessionId={}", sessionId);
            reportGenerationService.generateAsync(sessionId);
            InterviewSession endSession = interviewSessionMapper.selectById(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .decision("wrapup")
                    .streamAttemptId(null)
                    .sessionStatus("report_generating")
                    .debug(buildDebugPayload(endSession.getStateLedgerJson(), evalCallResult, evalOutput))
                    .build();
        }

        InterviewSession updatedSession = interviewSessionMapper.selectById(sessionId);
        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .decision(evalOutput.getDecision())
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .debug(buildDebugPayload(updatedSession.getStateLedgerJson(), evalCallResult, evalOutput))
                .build();
    }

    private void logAiDecisionRewrite(InterviewSession session,
                                      InterviewQuestion currentQuestion,
                                      AiCallResult<EvaluationDecisionOutput> evalCallResult,
                                      EvaluationDecisionOutput rawEvalOutput,
                                      EvaluationDecisionOutput rewrittenEvalOutput) {
        if (!interviewDebugEnabled || evalCallResult == null) {
            return;
        }
        Map<String, Object> rawMap = toDebugMap(rawEvalOutput);
        Map<String, Object> rewrittenMap = toDebugMap(rewrittenEvalOutput);
        List<String> changes = new ArrayList<>(collectDiffs("", rawMap, rewrittenMap));
        changes.sort(Comparator.naturalOrder());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("questionId", currentQuestion != null ? currentQuestion.getId() : null);
        payload.put("promptCode", evalCallResult.getPromptCode());
        payload.put("promptVersion", evalCallResult.getPromptVersion());
        payload.put("latencyMs", evalCallResult.getLatencyMs());
        payload.put("decision", rewrittenEvalOutput != null ? rewrittenEvalOutput.getDecision() : null);
        payload.put("targetFocus", rewrittenEvalOutput != null ? rewrittenEvalOutput.getTargetFocus() : null);
        payload.put("questionType", rewrittenEvalOutput != null ? rewrittenEvalOutput.getQuestionType() : null);
        payload.put("domainOutcome", rewrittenEvalOutput != null ? rewrittenEvalOutput.getDomainOutcome() : null);
        payload.put("changes", changes);
        payload.put("rawOutput", rawMap);
        payload.put("rewrittenOutput", rewrittenMap);
        log.info("[INTERVIEW-DEBUG][submit.eval] {}", clipDebugText(stringifyAsJson(payload)));
    }

    private SubmitAttemptResponse.DebugPayload buildDebugPayload(Map<String, Object> stateLedger,
                                                                AiCallResult<?> result,
                                                                Object parsedOutput) {
        if (!interviewDebugEnabled) {
            return null;
        }
        return SubmitAttemptResponse.DebugPayload.builder()
                .stateLedger(stateLedger)
                .aiInput(buildDebugAiInput(result))
                .aiOutput(buildDebugAiOutput(result, parsedOutput))
                .build();
    }

    private SubmitAttemptResponse.DebugAiInput buildDebugAiInput(AiCallResult<?> result) {
        if (result == null) {
            return null;
        }
        return SubmitAttemptResponse.DebugAiInput.builder()
                .systemPrompt(interviewDebugIncludePrompts ? clipDebugText(result.getSystemPrompt()) : null)
                .userPrompt(interviewDebugIncludePrompts ? clipDebugText(result.getUserPrompt()) : null)
                .promptCode(result.getPromptCode())
                .promptVersion(result.getPromptVersion())
                .build();
    }

    private SubmitAttemptResponse.DebugAiOutput buildDebugAiOutput(AiCallResult<?> result, Object parsedOutput) {
        if (result == null) {
            return null;
        }
        Map<String, Integer> tokenUsage = new LinkedHashMap<>();
        tokenUsage.put("promptTokens", result.getPromptTokens());
        tokenUsage.put("responseTokens", result.getResponseTokens());
        tokenUsage.put("totalTokens", result.getPromptTokens() + result.getResponseTokens());

        Map<String, Object> parsedMap = null;
        if (parsedOutput != null) {
            try {
                parsedMap = objectMapper.convertValue(parsedOutput, Map.class);
            } catch (Exception e) {
                log.warn("解析输出对象失败", e);
            }
        }

        return SubmitAttemptResponse.DebugAiOutput.builder()
                .rawResponse(clipDebugText(result.getRawResponse()))
                .parsedOutput(parsedMap)
                .latencyMs(result.getLatencyMs())
                .tokenUsage(tokenUsage)
                .build();
    }

    private String clipDebugText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (interviewDebugMaxTextChars <= 0 || value.length() <= interviewDebugMaxTextChars) {
            return value;
        }
        return value.substring(0, interviewDebugMaxTextChars) + "...(truncated)";
    }

    private EvaluationDecisionOutput copyEvalOutput(EvaluationDecisionOutput source) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(
                objectMapper.convertValue(source, Map.class),
                EvaluationDecisionOutput.class
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toDebugMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(objectMapper.convertValue(value, Map.class));
    }

    private String stringifyAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> collectDiffs(String path, Object rawValue, Object rewrittenValue) {
        if (Objects.equals(rawValue, rewrittenValue)) {
            return List.of();
        }
        if (rawValue instanceof Map<?, ?> rawMap && rewrittenValue instanceof Map<?, ?> rewrittenMap) {
            Set<String> keys = new LinkedHashSet<>();
            rawMap.keySet().forEach(k -> keys.add(String.valueOf(k)));
            rewrittenMap.keySet().forEach(k -> keys.add(String.valueOf(k)));
            List<String> diffs = new ArrayList<>();
            for (String key : keys) {
                String nextPath = path.isBlank() ? key : path + "." + key;
                diffs.addAll(collectDiffs(nextPath, rawMap.get(key), rewrittenMap.get(key)));
            }
            return diffs;
        }
        if (rawValue instanceof List<?> rawList && rewrittenValue instanceof List<?> rewrittenList) {
            int max = Math.max(rawList.size(), rewrittenList.size());
            List<String> diffs = new ArrayList<>();
            for (int i = 0; i < max; i++) {
                Object left = i < rawList.size() ? rawList.get(i) : null;
                Object right = i < rewrittenList.size() ? rewrittenList.get(i) : null;
                String nextPath = path + "[" + i + "]";
                diffs.addAll(collectDiffs(nextPath, left, right));
            }
            return diffs;
        }
        return List.of(path + ": " + safeString(rawValue) + " -> " + safeString(rewrittenValue));
    }

    private String fetchResumeText(InterviewSession session) {
        if (session.getResumeId() == null) {
            return null;
        }
        try {
            var resume = resumeMapper.selectById(session.getResumeId());
            return resume != null ? resume.getParsedText() : null;
        } catch (Exception e) {
            log.warn("读取简历解析文本失败，评估继续执行, sessionId={}, resumeId={}",
                    session.getId(), session.getResumeId(), e);
            return null;
        }
    }

    private boolean shouldForceEndByMaxQuestions(InterviewSession session) {
        int maxQuestions = extractMaxQuestions(session.getStateLedgerJson());
        if (maxQuestions <= 0) {
            return false;
        }
        int currentNo = session.getCurrentQuestionNo() != null ? session.getCurrentQuestionNo() : 0;
        return currentNo >= maxQuestions;
    }

    private int extractMaxQuestions(Map<String, Object> ledger) {
        if (ledger == null) {
            return 0;
        }
        Object maxObj = ledger.get("max_questions");
        if (maxObj instanceof Number n) {
            return n.intValue();
        }
        if (maxObj != null) {
            try {
                return Integer.parseInt(String.valueOf(maxObj));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private EvaluationDecisionOutput buildForcedEndDecision() {
        return EvaluationDecisionOutput.builder()
                .answerAssessment("Reached max question limit")
                .answerVerdict("PARTIAL")
                .decision("wrapup")
                .targetAngle("implementation")
                .difficultyAdjustment("same")
                .nextQuestionGoal("end interview after reaching max question limit")
                .domainOutcome("covered")
                .reasoning("Reached max_questions limit")
                .build();
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

    private void validateFinalDecision(EvaluationDecisionOutput evalOutput,
                                       InterviewQuestion currentQuestion,
                                       InterviewSession session) {
        if (evalOutput == null || safeString(evalOutput.getDecision()).isBlank()) {
            throw new IllegalArgumentException("评估决策结果缺少 decision");
        }

        String decision = normalizeDecision(evalOutput.getDecision());
        EvaluationDecisionOutput.NextQuestionStrategy strategy = resolveStrategy(evalOutput, currentQuestion, session);
        if (!"wrapup".equals(decision) && strategy == null) {
            throw new IllegalArgumentException("评估决策结果缺少下一题计划");
        }
        if ("wrapup".equals(decision) && strategy != null) {
            throw new IllegalArgumentException("wrapup 不允许携带下一题计划");
        }
        if (strategy == null) {
            return;
        }

        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        String nextDomainCode = strategy.getNextDomainCode();
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        String nextDepth = normalizeDepth(strategy.getTargetDepth());

        if ("followup".equals(decision) || "probe".equals(decision)) {
            if (!Objects.equals(currentDomainCode, nextDomainCode)) {
                throw new IllegalArgumentException("followup/probe 必须在同一知识域继续追问");
            }
            if (depthIndex(nextDepth) < depthIndex(currentDepth)) {
                throw new IllegalArgumentException("followup/probe 下下一题深度不允许降级");
            }
            if (depthIndex(nextDepth) > depthIndex(nextDepth(currentDepth))) {
                throw new IllegalArgumentException("followup/probe 下下一题深度最多提升一级");
            }
        }
        if ("rescue".equals(decision)) {
            if (!Objects.equals(currentDomainCode, nextDomainCode)) {
                throw new IllegalArgumentException("rescue 必须保持同一知识域");
            }
            if (depthIndex(nextDepth) > depthIndex(currentDepth)) {
                throw new IllegalArgumentException("rescue 不允许升层");
            }
        }
        if ("broaden".equals(decision)) {
            if (Objects.equals(currentDomainCode, nextDomainCode)) {
                throw new IllegalArgumentException("broaden 不允许继续指向当前知识域");
            }
            if (isCoveredDomain(session, nextDomainCode)) {
                throw new IllegalArgumentException("broaden 不允许切回已关闭知识域");
            }
        }
    }

    private List<EvaluationDecisionInput.QaContext> buildRecentContext(
            List<InterviewQuestion> questions,
            List<InterviewAttempt> attempts,
            Integer windowSize) {
        if (questions.isEmpty()) {
            return List.of();
        }
        int window = windowSize != null ? windowSize : 5;
        Map<Long, String> answerMap = attempts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsFinal()) && a.getAnswerText() != null)
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        InterviewAttempt::getAnswerText,
                        (a, b) -> b
                ));

        int total = questions.size();
        int cutoff = total - window;
        List<EvaluationDecisionInput.QaContext> ctx = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            InterviewQuestion q = questions.get(i);
            String answer = (i >= cutoff) ? answerMap.get(q.getId()) : null;
            ctx.add(EvaluationDecisionInput.QaContext.builder()
                    .stem(q.getStem())
                    .answer(answer)
                    .questionType(q.getQuestionType())
                    .domainCode(resolveDomainCodeFromGenCtx(q))
                    .build());
        }
        return ctx;
    }

    private SubmitAttemptResponse buildIdempotentResponse(InterviewAttempt existing) {
        String decision = "broaden";
        if (existing.getEvaluationJson() != null) {
            Object decisionObj = existing.getEvaluationJson().get("decision");
            if (decisionObj instanceof String str && !str.isBlank()) {
                decision = str;
            }
        }
        boolean isEnd = "wrapup".equalsIgnoreCase(decision);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .decision(decision)
                .streamAttemptId(isEnd ? null : existing.getAttemptId())
                .sessionStatus(isEnd ? "report_generating" : "in_progress")
                .build();
    }

    private EvaluationDecisionOutput harmonizeEvaluationOutput(EvaluationDecisionOutput evalOutput,
                                                               InterviewQuestion currentQuestion,
                                                               InterviewSession session) {
        if (evalOutput == null) {
            return null;
        }
        if (safeString(evalOutput.getDecision()).isBlank()) {
            throw new IllegalArgumentException("评估决策结果缺少 decision");
        }
        if (safeString(evalOutput.getTargetAngle()).isBlank()) {
            evalOutput.setTargetAngle("implementation");
        }
        if (safeString(evalOutput.getDifficultyAdjustment()).isBlank()) {
            evalOutput.setDifficultyAdjustment("same");
        }
        if (safeString(evalOutput.getQuestionType()).isBlank() && !"wrapup".equals(normalizeDecision(evalOutput.getDecision()))) {
            evalOutput.setQuestionType(resolveQuestionType(currentQuestion.getQuestionType()));
        }
        if (safeString(evalOutput.getFocusPoint()).isBlank()) {
            evalOutput.setFocusPoint(firstNonBlank(safeString(evalOutput.getTargetFocus()), resolveDomainName(currentQuestion, session)));
        }
        if (safeString(evalOutput.getTargetFocus()).isBlank()) {
            evalOutput.setTargetFocus(firstNonBlank(evalOutput.getFocusPoint(), resolveDomainName(currentQuestion, session)));
        }
        return evalOutput;
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildStrategyFromNew(EvaluationDecisionOutput evalOutput,
                                                                               InterviewQuestion currentQuestion,
                                                                               InterviewSession session) {
        String decision = safeString(evalOutput.getDecision()).trim().toLowerCase(Locale.ROOT);
        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        String nextDomainCode = firstNonBlank(
                safeString(evalOutput.getNextDomainCode()),
                "broaden".equals(decision) ? "" : currentDomainCode,
                "INTRO".equalsIgnoreCase(currentQuestion.getQuestionType()) ? "intro" : ""
        );
        String nextDomainName = firstNonBlank(
                safeString(evalOutput.getNextDomainName()),
                "intro".equalsIgnoreCase(nextDomainCode) ? "intro" : resolveDomainName(currentQuestion, session)
        );
        String questionType = resolveQuestionType(firstNonBlank(
                safeString(evalOutput.getQuestionType()),
                "broaden".equals(decision) ? "PRINCIPLE" : currentQuestion.getQuestionType()
        ));
        String focus = firstNonBlank(
                safeString(evalOutput.getFocusPoint()),
                safeString(evalOutput.getTargetFocus()),
                nextDomainName
        );
        String targetDepth = resolveTargetDepthFromNew(evalOutput, currentQuestion, session, nextDomainCode, decision);
        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(evalOutput.getNextDomainId())
                .nextDomainCode(nextDomainCode)
                .nextDomainName(nextDomainName)
                .questionType(questionType)
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .targetSkill(focus)
                .focusPoint(focus)
                .expectedPoints(buildExpectedPointsForFocus(focus))
                .build();
    }

    private String resolveTargetDepthFromNew(EvaluationDecisionOutput evalOutput,
                                             InterviewQuestion currentQuestion,
                                             InterviewSession session,
                                             String nextDomainCode,
                                             String decision) {
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        String adjustment = safeString(evalOutput.getDifficultyAdjustment()).trim().toLowerCase(Locale.ROOT);
        String candidate = switch (adjustment) {
            case "up" -> nextDepth(currentDepth);
            case "down" -> minDepth("L1", currentDepth).equals(currentDepth)
                    ? currentDepth
                    : "L" + Math.max(1, depthIndex(currentDepth) - 1);
            default -> currentDepth;
        };
        if ("broaden".equals(decision)) {
            String startingDepth = resolveStartingDepth(session.getExperienceLevel());
            String targetDomainDepth = resolveDomainTargetDepth(session, nextDomainCode, startingDepth);
            return minDepth(startingDepth, targetDomainDepth);
        }
        String domainTargetDepth = resolveDomainTargetDepth(session, nextDomainCode, candidate);
        return minDepth(candidate, domainTargetDepth);
    }


    private String normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            return "";
        }
        return decision.trim().toLowerCase(Locale.ROOT);
    }

    private EvaluationDecisionOutput.NextQuestionStrategy resolveStrategy(EvaluationDecisionOutput evalOutput,
                                                                          InterviewQuestion currentQuestion,
                                                                          InterviewSession session) {
        if ("wrapup".equals(normalizeDecision(evalOutput.getDecision()))) {
            return null;
        }
        return buildStrategyFromNew(evalOutput, currentQuestion, session);
    }

    private void clearStrategyFromOutput(EvaluationDecisionOutput evalOutput) {
        evalOutput.setNextDomainId(null);
        evalOutput.setNextDomainCode(null);
        evalOutput.setNextDomainName(null);
        evalOutput.setQuestionType(null);
        evalOutput.setFocusPoint(null);
    }

    private void applyStrategyToOutput(EvaluationDecisionOutput evalOutput,
                                       EvaluationDecisionOutput.NextQuestionStrategy strategy) {
        if (strategy == null) {
            clearStrategyFromOutput(evalOutput);
            return;
        }
        evalOutput.setNextDomainId(strategy.getNextDomainId());
        evalOutput.setNextDomainCode(strategy.getNextDomainCode());
        evalOutput.setNextDomainName(strategy.getNextDomainName());
        evalOutput.setQuestionType(resolveQuestionType(strategy.getQuestionType()));
        evalOutput.setFocusPoint(extractSingleFocus(strategy));
        if (safeString(evalOutput.getTargetFocus()).isBlank()) {
            evalOutput.setTargetFocus(firstNonBlank(evalOutput.getFocusPoint(), strategy.getNextDomainName()));
        }
    }

    private String defaultNextQuestionGoal(EvaluationDecisionOutput evalOutput) {
        String focus = firstNonBlank(safeString(evalOutput.getTargetFocus()), safeString(evalOutput.getFocusPoint()), "当前主题");
        return switch (safeString(evalOutput.getDecision()).trim().toLowerCase(Locale.ROOT)) {
            case "followup" -> "continue probing " + focus + " in the current thread";
            case "probe" -> "probe a nearby point around " + focus;
            case "rescue" -> "downgrade and verify the basics of " + focus;
            case "wrapup" -> "wrap up the interview";
            default -> "broaden to another skill area through " + focus;
        };
    }

    private EvaluationDecisionOutput applyBusinessGuardrails(EvaluationDecisionOutput evalOutput,
                                                             InterviewQuestion currentQuestion,
                                                             InterviewSession session) {
        if (evalOutput == null) {
            return null;
        }
        if ("INTRO".equalsIgnoreCase(currentQuestion.getQuestionType())) {
            applyIntroGuardrails(evalOutput, currentQuestion, session);
            return evalOutput;
        }
        enforceDecisionGuardrails(evalOutput, currentQuestion, session);
        return evalOutput;
    }

    private void applyIntroGuardrails(EvaluationDecisionOutput evalOutput,
                                      InterviewQuestion currentQuestion,
                                      InterviewSession session) {
        int introCount = getQuestionTypeCount(session, "INTRO");
        boolean secondIntroOrMore = introCount >= 1;
        String decision = normalizeDecision(evalOutput.getDecision());

        if (secondIntroOrMore) {
            if (!"broaden".equals(decision)) {
                evalOutput.setDecision("broaden");
            }
        } else if ("wrapup".equals(decision) || "followup".equals(decision) || "probe".equals(decision)) {
            evalOutput.setDecision("rescue");
        }

        decision = normalizeDecision(evalOutput.getDecision());
        if ("rescue".equals(decision)) {
            if (hasReachedRescueLimit(session, resolveDomainCode(currentQuestion, session))) {
                evalOutput.setDecision("broaden");
                guardNextDomain(evalOutput, currentQuestion, session);
                return;
            }
            applyStrategyToOutput(evalOutput,
                    buildIntroRetryStrategy(resolveStrategy(evalOutput, currentQuestion, session), secondIntroOrMore));
            return;
        }
        if ("broaden".equals(decision)) {
            guardNextDomain(evalOutput, currentQuestion, session);
            return;
        }
        if ("wrapup".equals(decision)) {
            evalOutput.setDecision("broaden");
            guardNextDomain(evalOutput, currentQuestion, session);
        }
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildIntroRetryStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            boolean secondIntroOrMore) {
        String targetSkill = safeString(base != null ? base.getTargetSkill() : null);
        if (!isGuidedIntroTargetSkill(targetSkill)) {
            targetSkill = secondIntroOrMore
                    ? "引导候选人详细介绍一个做过的项目、技术栈和个人职责"
                    : "引导候选人补充具体的项目经验和使用的技术栈";
        }
        List<String> expectedPoints = base != null ? safeList(base.getExpectedPoints()) : List.of();
        if (expectedPoints.isEmpty()) {
            expectedPoints = List.of(
                    "说明一个做过的项目名称",
                    "说明项目的业务目标和技术栈",
                    "说明你在项目中的具体职责"
            );
        }
        String focus = safeString(base != null ? base.getFocusPoint() : null);
        if (focus.isBlank()) {
            focus = "项目经历";
        }
        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(base != null ? base.getNextDomainId() : null)
                .nextDomainCode("intro")
                .nextDomainName("intro")
                .questionType("INTRO")
                .targetDepth("L1")
                .difficulty("L1")
                .focusPoint(focus)
                .targetSkill(targetSkill)
                .expectedPoints(expectedPoints)
                .build();
    }

    private void enforceDecisionGuardrails(EvaluationDecisionOutput evalOutput,
                                           InterviewQuestion currentQuestion,
                                           InterviewSession session) {
        String decision = normalizeDecision(evalOutput.getDecision());
        if ("wrapup".equals(decision)) {
            return;
        }
        if ("followup".equals(decision) || "probe".equals(decision)) {
            if (shouldStopSameFocusFollowup(session, evalOutput)) {
                evalOutput.setDecision("broaden");
                guardNextDomain(evalOutput, currentQuestion, session);
                return;
            }
            guardDeepen(evalOutput, currentQuestion, session);
            return;
        }
        if ("rescue".equals(decision)) {
            if (hasReachedRescueLimit(session, resolveDomainCode(currentQuestion, session))) {
                evalOutput.setDecision("broaden");
                guardNextDomain(evalOutput, currentQuestion, session);
                return;
            }
            evalOutput.setNextDomainId(currentQuestion.getDomainId());
            evalOutput.setNextDomainCode(resolveDomainCode(currentQuestion, session));
            evalOutput.setNextDomainName(resolveDomainName(currentQuestion, session));
            if (safeString(evalOutput.getQuestionType()).isBlank()) {
                evalOutput.setQuestionType(resolveQuestionType(currentQuestion.getQuestionType()));
            } else {
                evalOutput.setQuestionType(resolveQuestionType(evalOutput.getQuestionType()));
            }
            if (safeString(evalOutput.getFocusPoint()).isBlank()) {
                evalOutput.setFocusPoint(firstNonBlank(
                        safeString(evalOutput.getTargetFocus()),
                        resolveDomainName(currentQuestion, session)
                ));
            }
            if (safeString(evalOutput.getTargetFocus()).isBlank()) {
                evalOutput.setTargetFocus(firstNonBlank(
                        safeString(evalOutput.getFocusPoint()),
                        resolveDomainName(currentQuestion, session)
                ));
            }
            if ("up".equalsIgnoreCase(safeString(evalOutput.getDifficultyAdjustment()))) {
                evalOutput.setDifficultyAdjustment("same");
            }
            return;
        }
        if ("broaden".equals(decision)) {
            guardNextDomain(evalOutput, currentQuestion, session);
        }
    }

    /**
     * 深度追问护栏。
     * 当AI选择 followup 或 probe 时，检查是否应该继续深入：
     * 1. 检查同一知识域的连续追问轮数是否超过限制（最多4轮）
     * 2. 检查当前深度是否已达到目标深度
     * 如果任一条件触发，强制切换到其他知识域。
     */
    private void guardDeepen(EvaluationDecisionOutput evalOutput,
                             InterviewQuestion currentQuestion,
                             InterviewSession session) {
        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        String domainTargetDepth = resolveDomainTargetDepth(session, currentDomainCode, currentDepth);
        
        // 新增：检查同一知识域的连续追问轮数
        int domainFollowupCount = getDomainFollowupCount(session, currentDomainCode);
        if (domainFollowupCount >= 4) {
            log.info("同一知识域连续追问达到上限，强制切换, domainCode={}, followupCount={}", 
                    currentDomainCode, domainFollowupCount);
            evalOutput.setDecision("broaden");
            guardNextDomain(evalOutput, currentQuestion, session);
            return;
        }
        
        // 原有逻辑：检查深度是否已达到目标
        if (depthIndex(currentDepth) >= depthIndex(domainTargetDepth)) {
            evalOutput.setDecision("broaden");
            guardNextDomain(evalOutput, currentQuestion, session);
            return;
        }

        String boundedDepth = minDepth(nextDepth(currentDepth), domainTargetDepth);
        applyStrategyToOutput(evalOutput, buildSameDomainStrategy(
                resolveStrategy(evalOutput, currentQuestion, session),
                currentQuestion,
                session,
                boundedDepth,
                resolveQuestionType(firstNonBlank(
                        safeString(evalOutput.getQuestionType()),
                        currentQuestion.getQuestionType()))
        ));
    }

    /**
     * 获取当前知识域的连续追问轮数。
     */
    private int getDomainFollowupCount(InterviewSession session, String domainCode) {
        if (session.getStateLedgerJson() == null || domainCode == null || domainCode.isBlank()) {
            return 0;
        }
        String currentDomainCode = safeString(session.getStateLedgerJson().get("current_domain_code"));
        if (!domainCode.equals(currentDomainCode)) {
            return 0;
        }
        return toInt(session.getStateLedgerJson().get("current_domain_followup_count"));
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
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

    private void guardNextDomain(EvaluationDecisionOutput evalOutput,
                                 InterviewQuestion currentQuestion,
                                 InterviewSession session) {
        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        EvaluationDecisionOutput.NextQuestionStrategy base = resolveStrategy(evalOutput, currentQuestion, session);
        String requestedCode = base != null ? safeString(base.getNextDomainCode()) : "";
        boolean useFallback = !isValidNextDomain(session, currentDomainCode, requestedCode);
        String nextDomainCode = useFallback
                ? selectNextDomainCode(session, currentDomainCode)
                : requestedCode;
        if (nextDomainCode == null || nextDomainCode.isBlank()) {
            evalOutput.setDecision("wrapup");
            clearStrategyFromOutput(evalOutput);
            return;
        }
        if (useFallback) {
            log.info("评估决策 fallbackReason=invalid_next_domain, fallbackSelectedDomain={}, fallbackSelectorSource=syllabus_order",
                    nextDomainCode);
        }
        evalOutput.setDecision("broaden");
        applyStrategyToOutput(evalOutput, buildNextDomainStrategy(base, session, nextDomainCode));
    }

    private boolean hasReachedRescueLimit(InterviewSession session, String currentDomainCode) {
        return rescueCountForDomain(session, currentDomainCode) >= 1 || sessionRescueCount(session) >= 3;
    }

    /**
     * 判断是否应该停止对同一焦点的追问。
     * 改进：使用相似度判断而非精确匹配，避免AI稍微变换表述就绕过限制。
     * 
     * @param session 面试会话
     * @param evalOutput AI评估决策输出
     * @return true 表示应该停止追问，强制切换到其他话题
     */
    private boolean shouldStopSameFocusFollowup(InterviewSession session, EvaluationDecisionOutput evalOutput) {
        String focus = firstNonBlank(safeString(evalOutput.getFocusPoint()), safeString(evalOutput.getTargetFocus()));
        if (focus.isBlank()) {
            return false;
        }
        String lastFocus = extractLedgerString(session, "last_focus_point");
        int streak = extractLedgerInt(session, "current_focus_streak");
        
        // 改进：使用相似度判断，避免精确匹配被绕过
        // 条件1：完全相等
        // 条件2：包含关系（如"MySQL索引"与"MySQL索引优化"）
        // 条件3：核心关键词重叠（提取关键词判断）
        boolean sameFocus = isSimilarFocus(focus, lastFocus);
        
        // 降低阈值到3轮，提升用户体验
        return sameFocus && streak >= 3;
    }

    /**
     * 判断两个焦点是否相似。
     * 支持完全相等、包含关系、核心关键词重叠三种情况。
     */
    private boolean isSimilarFocus(String focus1, String focus2) {
        if (focus1.isBlank() || focus2.isBlank()) {
            return false;
        }
        
        String f1 = focus1.trim().toLowerCase(Locale.ROOT);
        String f2 = focus2.trim().toLowerCase(Locale.ROOT);
        
        // 条件1：完全相等
        if (f1.equals(f2)) {
            return true;
        }
        
        // 条件2：包含关系（至少3个字符，避免短词误判）
        if (f1.length() >= 3 && f2.length() >= 3) {
            if (f1.contains(f2) || f2.contains(f1)) {
                return true;
            }
        }
        
        // 条件3：核心关键词重叠
        // 提取关键词：去除常见修饰词，保留核心技术名词
        Set<String> keywords1 = extractCoreKeywords(f1);
        Set<String> keywords2 = extractCoreKeywords(f2);
        
        // 如果有至少一个核心关键词重叠，认为是相似焦点
        keywords1.retainAll(keywords2);
        return !keywords1.isEmpty();
    }

    /**
     * 从焦点描述中提取核心关键词。
     * 去除常见修饰词，保留技术名词。
     */
    private Set<String> extractCoreKeywords(String focus) {
        if (focus == null || focus.isBlank()) {
            return Set.of();
        }
        
        // 常见修饰词/连接词，不作为核心关键词
        Set<String> stopWords = Set.of(
                "的", "与", "和", "及", "或", "在", "中", "对", "于", "是",
                "the", "of", "and", "or", "in", "on", "at", "to", "for",
                "如何", "怎么", "什么", "为什么", "哪些", "怎样",
                "实现", "原理", "机制", "方案", "方法", "方式", "策略",
                "问题", "场景", "情况", "案例", "实例"
        );
        
        // 分词：按空格、标点、中文字符边界分割
        String[] tokens = focus.split("[\\s,，。、；;：:！!？?()（）\\[\\]【】\"'\"'《》<>]+");
        
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            // 过滤短词和停用词
            if (normalized.length() >= 2 && !stopWords.contains(normalized)) {
                keywords.add(normalized);
            }
        }
        
        return keywords;
    }

    private String resolveRescueTargetDepth(EvaluationDecisionOutput evalOutput, InterviewQuestion currentQuestion) {
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        String adjustment = safeString(evalOutput.getDifficultyAdjustment()).trim().toLowerCase(Locale.ROOT);
        if ("down".equals(adjustment)) {
            return previousDepth(currentDepth);
        }
        return currentDepth;
    }

    @SuppressWarnings("unchecked")
    private int rescueCountForDomain(InterviewSession session, String domainCode) {
        if (session.getStateLedgerJson() == null || domainCode == null || domainCode.isBlank()) {
            return 0;
        }
        Object raw = session.getStateLedgerJson().get("rescue_counts_by_domain");
        if (!(raw instanceof Map<?, ?> counts)) {
            return 0;
        }
        return toInt(counts.get(domainCode));
    }

    private int sessionRescueCount(InterviewSession session) {
        return extractLedgerInt(session, "rescue_total");
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildRetrySameDomainStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            InterviewQuestion currentQuestion,
            InterviewSession session,
            String targetDepth) {
        return buildSameDomainStrategy(
                base,
                currentQuestion,
                session,
                targetDepth,
                resolveQuestionType(currentQuestion.getQuestionType())
        );
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildSameDomainStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            InterviewQuestion currentQuestion,
            InterviewSession session,
            String targetDepth,
            String questionType) {
        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        String currentDomainName = resolveDomainName(currentQuestion, session);
        String focus = extractSingleFocus(base != null ? base : EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainCode(currentDomainCode)
                .nextDomainName(currentDomainName)
                .focusPoint(currentDomainName)
                .targetSkill(currentDomainName)
                .build());
        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(currentQuestion.getDomainId())
                .nextDomainCode(currentDomainCode)
                .nextDomainName(currentDomainName)
                .questionType(questionType)
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .focusPoint(focus)
                .targetSkill(focus)
                .expectedPoints(buildExpectedPointsForFocus(focus))
                .build();
    }

    /**
     * 构建切换知识域时的下一题策略。
     * 新知识域的起始深度根据候选人经验层级确定：
     * - 应届生（FRESH_GRAD）：从 L1 开始
     * - 有经验者：从 L2 开始
     * 起始深度不会超过考纲中该知识域的目标深度。
     */
    private EvaluationDecisionOutput.NextQuestionStrategy buildNextDomainStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            InterviewSession session,
            String nextDomainCode) {
        boolean reuseBase = base != null && nextDomainCode.equals(safeString(base.getNextDomainCode()));
        Map<String, Object> syllabusDomain = findSyllabusDomain(session, nextDomainCode, reuseBase ? base.getNextDomainId() : null);
        String nextDomainName = syllabusDomain != null ? safeString(syllabusDomain.get("domainName")) : nextDomainCode;
        Long nextDomainId = syllabusDomain != null ? toLong(syllabusDomain.get("domainId")) : (reuseBase ? base.getNextDomainId() : null);

        // 获取考纲中该知识域的目标深度（最大深度限制）
        String domainTargetDepth = resolveDomainTargetDepth(session, nextDomainCode, "L2");
        // 根据候选人经验层级确定新知识域的起始深度
        String startingDepth = resolveStartingDepth(session.getExperienceLevel());
        // 起始深度不能超过考纲目标深度
        String targetDepth = minDepth(startingDepth, domainTargetDepth);

        String questionType = reuseBase ? resolveQuestionType(base.getQuestionType()) : "PRINCIPLE";
        String focus = reuseBase
                ? extractSingleFocus(base)
                : extractFocusFromDomain(syllabusDomain != null ? syllabusDomain : Map.of("domainName", nextDomainName));
        List<String> expectedPoints = reuseBase && base.getExpectedPoints() != null && !base.getExpectedPoints().isEmpty()
                ? base.getExpectedPoints()
                : buildExpectedPointsForFocus(focus);
        String targetSkill = reuseBase ? firstNonBlank(base.getTargetSkill(), focus) : focus;
        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(nextDomainId)
                .nextDomainCode(nextDomainCode)
                .nextDomainName(nextDomainName)
                .questionType(questionType)
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .focusPoint(focus)
                .targetSkill(targetSkill)
                .expectedPoints(expectedPoints)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String selectNextDomainCode(InterviewSession session, String currentDomainCode) {
        if (session.getSyllabusJson() == null) {
            return null;
        }
        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return null;
        }
        Map<String, String> statusByCode = extractDomainStatusMap(session);
        String inProgressFallback = null;
        for (Object domainObj : domains) {
            if (!(domainObj instanceof Map<?, ?> raw)) {
                continue;
            }
            String code = safeString(raw.get("domainCode"));
            String status = safeString(statusByCode.get(code)).toUpperCase(Locale.ROOT);
            if (code.isBlank() || Objects.equals(code, currentDomainCode) || "COVERED".equals(status)) {
                continue;
            }
            if ("UNASKED".equals(status)) {
                return code;
            }
            if ("IN_PROGRESS".equals(status) && inProgressFallback == null) {
                inProgressFallback = code;
            }
        }
        return inProgressFallback;
    }

    private boolean isValidNextDomain(InterviewSession session, String currentDomainCode, String candidateCode) {
        if (candidateCode == null || candidateCode.isBlank()
                || Objects.equals(candidateCode, currentDomainCode)
                || "intro".equalsIgnoreCase(candidateCode)) {
            return false;
        }
        return findSyllabusDomainExact(session, candidateCode) != null && !isCoveredDomain(session, candidateCode);
    }

    @SuppressWarnings("unchecked")
    private boolean isCoveredDomain(InterviewSession session, String candidateCode) {
        if (candidateCode == null || candidateCode.isBlank() || session.getStateLedgerJson() == null) {
            return false;
        }
        Object domainStatesObj = session.getStateLedgerJson().get("domain_states");
        if (!(domainStatesObj instanceof List<?> states)) {
            return false;
        }
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> state = new LinkedHashMap<>((Map<String, Object>) raw);
            if (!Objects.equals(candidateCode, safeString(state.get("domain_id")))) {
                continue;
            }
            return "COVERED".equalsIgnoreCase(safeString(state.get("status")));
        }
        return false;
    }

    private List<String> buildExpectedPointsForFocus(String focus) {
        return List.of(
                "说明" + focus + "的核心概念",
                "比较" + focus + "的常见方案与取舍",
                "结合场景说明" + focus + "的使用边界"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findSyllabusDomain(InterviewSession session,
                                                   String nextDomainCode,
                                                   Long nextDomainId) {
        if (session.getSyllabusJson() == null) {
            return null;
        }
        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return null;
        }
        for (Object item : domains) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> domain = new LinkedHashMap<>();
            raw.forEach((k, v) -> domain.put(String.valueOf(k), v));
            String code = safeString(domain.get("domainCode"));
            Long id = toLong(domain.get("domainId"));
            if (!nextDomainCode.isBlank() && nextDomainCode.equals(code)) {
                return domain;
            }
            if (nextDomainId != null && Objects.equals(nextDomainId, id)) {
                return domain;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findSyllabusDomainExact(InterviewSession session, String domainCode) {
        if (session.getSyllabusJson() == null || domainCode == null || domainCode.isBlank()) {
            return null;
        }
        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return null;
        }
        for (Object item : domains) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> domain = new LinkedHashMap<>();
            raw.forEach((k, v) -> domain.put(String.valueOf(k), v));
            if (domainCode.equals(safeString(domain.get("domainCode")))) {
                return domain;
            }
        }
        return null;
    }

    private void sanitizeSingleFocusStrategy(EvaluationDecisionOutput.NextQuestionStrategy strategy) {
        String focus = extractSingleFocus(strategy);
        strategy.setFocusPoint(focus);
        if (safeString(strategy.getTargetSkill()).isBlank()) {
            strategy.setTargetSkill(focus);
        } else {
            strategy.setTargetSkill(strategy.getTargetSkill().trim());
        }
        if (strategy.getExpectedPoints() == null || strategy.getExpectedPoints().isEmpty()) {
            return;
        }
        List<String> sanitized = strategy.getExpectedPoints().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(point -> !point.isBlank())
                .distinct()
                .toList();
        if (!sanitized.isEmpty()) {
            strategy.setExpectedPoints(sanitized);
        }
    }

    private String extractSingleFocus(EvaluationDecisionOutput.NextQuestionStrategy strategy) {
        String candidate = firstNonBlank(
                safeString(strategy.getFocusPoint()),
                safeString(strategy.getTargetSkill()),
                safeString(strategy.getNextDomainName())
        );
        if (candidate.isBlank()) {
            return "";
        }
        return candidate.trim();
    }

    @SuppressWarnings("unchecked")
    private String extractFocusFromDomain(Map<String, Object> domain) {
        Object focusPointsObj = domain.get("focusPoints");
        if (focusPointsObj instanceof List<?> focusPoints) {
            for (Object fp : focusPoints) {
                String point = safeString(fp).trim();
                if (!point.isBlank()) {
                    return extractSingleFocus(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                            .focusPoint(point)
                            .build());
                }
            }
        }
        return safeString(domain.get("domainName"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String resolveQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "PRINCIPLE";
        }
        return questionType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveDomainCodeFromGenCtx(InterviewQuestion q) {
        if (q.getGenerationContextJson() == null) {
            return "";
        }
        Object code = q.getGenerationContextJson().get("domainCode");
        return code instanceof String s ? s : "";
    }

    private String resolveDomainCode(InterviewQuestion q, InterviewSession session) {
        String fromCtx = resolveDomainCodeFromGenCtx(q);
        if (!fromCtx.isBlank()) {
            return fromCtx;
        }
        return "intro";
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
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

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private boolean isGuidedIntroTargetSkill(String value) {
        String normalized = safeString(value);
        return normalized.contains("引导候选人") && (normalized.contains("项目") || normalized.contains("技术栈"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractDomainStatusMap(InterviewSession session) {
        Map<String, String> result = new LinkedHashMap<>();
        if (session.getStateLedgerJson() == null) {
            return result;
        }
        Object domainStatesObj = session.getStateLedgerJson().get("domain_states");
        if (!(domainStatesObj instanceof List<?> states)) {
            return result;
        }
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> raw)) {
                continue;
            }
            String code = safeString(raw.get("domain_id"));
            if (code.isBlank()) {
                continue;
            }
            result.put(code, safeString(raw.get("status")));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private int getQuestionTypeCount(InterviewSession session, String questionType) {
        if (session.getStateLedgerJson() == null) {
            return 0;
        }
        Object mixObj = session.getStateLedgerJson().get("question_mix_progress");
        if (!(mixObj instanceof Map<?, ?> rawMix)) {
            return 0;
        }
        Object value = rawMix.get(questionType);
        if (value instanceof Number n) {
            return n.intValue();
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

    private String extractLedgerString(InterviewSession session, String key) {
        if (session.getStateLedgerJson() == null || key == null || key.isBlank()) {
            return "";
        }
        return safeString(session.getStateLedgerJson().get(key));
    }

    @SuppressWarnings("unchecked")
    private int extractLedgerInt(InterviewSession session, String key) {
        if (session.getStateLedgerJson() == null || key == null || key.isBlank()) {
            return 0;
        }
        return toInt(session.getStateLedgerJson().get(key));
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
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

    private String normalizeDepth(String depth) {
        if (depth == null || depth.isBlank()) {
            return "L2";
        }
        String normalized = depth.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("L[1-5]") ? normalized : "L2";
    }

    private String resolveDomainTargetDepth(InterviewSession session, String domainCode, String fallbackDepth) {
        Map<String, Object> syllabusDomain = findSyllabusDomainExact(session, domainCode);
        if (syllabusDomain == null) {
            return normalizeDepth(fallbackDepth);
        }
        return normalizeDepth(syllabusDomain.get("targetDepth") instanceof String depth ? depth : fallbackDepth);
    }

    /**
     * 根据候选人经验层级确定新知识域的起始深度。
     * 应届生从 L1 开始，有经验者从 L2 开始。
     *
     * @param experienceLevel 经验层级（FRESH_GRAD / JUNIOR / MID / SENIOR）
     * @return 起始深度（L1 或 L2）
     */
    private String resolveStartingDepth(String experienceLevel) {
        if ("FRESH_GRAD".equalsIgnoreCase(experienceLevel)
                || "INTERN".equalsIgnoreCase(experienceLevel)) {
            return "L1";
        }
        return "L2";
    }

    private String nextDepth(String depth) {
        int next = Math.min(depthIndex(depth) + 1, 5);
        return "L" + next;
    }

    private String previousDepth(String depth) {
        int previous = Math.max(depthIndex(depth) - 1, 1);
        return "L" + previous;
    }

    private String minDepth(String left, String right) {
        return depthIndex(left) <= depthIndex(right) ? normalizeDepth(left) : normalizeDepth(right);
    }

    private int depthIndex(String depth) {
        return normalizeDepth(depth).charAt(1) - '0';
    }

    private String resolveDomainName(InterviewQuestion q, InterviewSession session) {
        String domainCode = resolveDomainCode(q, session);
        if (session.getSyllabusJson() == null) {
            return domainCode;
        }
        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return domainCode;
        }
        for (Object d : domains) {
            if (!(d instanceof Map<?, ?> dm)) {
                continue;
            }
            if (domainCode.equals(dm.get("domainCode"))) {
                Object name = dm.get("domainName");
                if (name instanceof String s) {
                    return s;
                }
            }
        }
        return domainCode;
    }
}
