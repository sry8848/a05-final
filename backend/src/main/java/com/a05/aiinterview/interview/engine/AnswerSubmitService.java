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

        evalOutput = applyBusinessGuardrails(evalOutput, currentQuestion, session);
        logAiDecisionRewrite(session, currentQuestion, evalCallResult, rawEvalOutput, evalOutput);
        validateFinalDecision(evalOutput, currentQuestion, session);

        AnswerSubmitPersistenceService.PersistedAttemptResult persisted =
                answerSubmitPersistenceService.persist(sessionId, currentQuestion, request, evalOutput);

        if ("END".equals(evalOutput.getSignal())) {
            log.info("评估决策信号=END，触发结束面试并异步生成报告, sessionId={}", sessionId);
            reportGenerationService.generateAsync(sessionId);
            InterviewSession endSession = interviewSessionMapper.selectById(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .evaluationSignal("END")
                    .streamAttemptId(null)
                    .sessionStatus("report_generating")
                    .stateLedger(endSession.getStateLedgerJson())
                    .aiInput(buildDebugAiInput(evalCallResult))
                    .aiOutput(buildDebugAiOutput(evalCallResult, evalOutput))
                    .build();
        }

        InterviewSession updatedSession = interviewSessionMapper.selectById(sessionId);
        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .evaluationSignal(evalOutput.getSignal())
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .stateLedger(updatedSession.getStateLedgerJson())
                .aiInput(buildDebugAiInput(evalCallResult))
                .aiOutput(buildDebugAiOutput(evalCallResult, evalOutput))
                .build();
    }

    private void logAiDecisionRewrite(InterviewSession session,
                                      InterviewQuestion currentQuestion,
                                      AiCallResult<EvaluationDecisionOutput> evalCallResult,
                                      EvaluationDecisionOutput rawEvalOutput,
                                      EvaluationDecisionOutput rewrittenEvalOutput) {
        if (evalCallResult == null) {
            return;
        }
        Map<String, Object> rawMap = toDebugMap(rawEvalOutput);
        Map<String, Object> rewrittenMap = toDebugMap(rewrittenEvalOutput);
        List<String> changes = new ArrayList<>(collectDiffs("", rawMap, rewrittenMap));
        changes.sort(Comparator.naturalOrder());

        log.info("评估决策结果对比, sessionId={}, questionId={}, promptCode={}, version={}, latencyMs={}",
                session.getId(),
                currentQuestion != null ? currentQuestion.getId() : null,
                evalCallResult.getPromptCode(),
                evalCallResult.getPromptVersion(),
                evalCallResult.getLatencyMs());
        log.info("AI 原始结构化输出={}", stringifyAsJson(rawMap));
        log.info("后端改写后输出={}", stringifyAsJson(rewrittenMap));
        if (changes.isEmpty()) {
            log.info("评估决策改写差异=未改写");
        } else {
            log.info("评估决策改写差异={}", changes);
        }
    }

    private SubmitAttemptResponse.DebugAiInput buildDebugAiInput(AiCallResult<?> result) {
        if (result == null) {
            return null;
        }
        return SubmitAttemptResponse.DebugAiInput.builder()
                .systemPrompt(result.getSystemPrompt())
                .userPrompt(result.getUserPrompt())
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
                .rawResponse(result.getRawResponse())
                .parsedOutput(parsedMap)
                .latencyMs(result.getLatencyMs())
                .tokenUsage(tokenUsage)
                .build();
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
                .passCurrentLevel(true)
                .deepen(false)
                .signal("END")
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
        if (evalOutput == null || evalOutput.getSignal() == null || evalOutput.getSignal().isBlank()) {
            throw new IllegalArgumentException("评估决策结果缺少 signal");
        }
        String signal = evalOutput.getSignal();
        if (!"END".equals(signal) && evalOutput.getNextStrategy() == null) {
            throw new IllegalArgumentException("评估决策结果缺少 nextStrategy");
        }
        if ("END".equals(signal) && evalOutput.getNextStrategy() != null) {
            throw new IllegalArgumentException("END 信号不允许携带 nextStrategy");
        }
        if (evalOutput.getNextStrategy() == null) {
            return;
        }

        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        String nextDomainCode = evalOutput.getNextStrategy().getNextDomainCode();
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        String nextDepth = normalizeDepth(evalOutput.getNextStrategy().getTargetDepth());

        if ("DEEPEN".equals(signal)) {
            if (!Objects.equals(currentDomainCode, nextDomainCode)) {
                throw new IllegalArgumentException("DEEPEN 信号必须在同一知识域继续追问");
            }
            if (depthIndex(nextDepth) < depthIndex(currentDepth)) {
                throw new IllegalArgumentException("DEEPEN 信号下下一题深度不允许降级");
            }
            if (depthIndex(nextDepth) > depthIndex(nextDepth(currentDepth))) {
                throw new IllegalArgumentException("DEEPEN 信号下下一题深度最多提升一级");
            }
        }
        if ("RETRY_SAME_DOMAIN".equals(signal)) {
            if (!Objects.equals(currentDomainCode, nextDomainCode)) {
                throw new IllegalArgumentException("RETRY_SAME_DOMAIN 必须保持同一知识域");
            }
            if (!Objects.equals(currentDepth, nextDepth)) {
                throw new IllegalArgumentException("RETRY_SAME_DOMAIN 不允许升降层");
            }
        }
        if ("NEXT_DOMAIN".equals(signal)) {
            if (Objects.equals(currentDomainCode, nextDomainCode)) {
                throw new IllegalArgumentException("NEXT_DOMAIN 不允许继续指向当前知识域");
            }
            if (isCoveredDomain(session, nextDomainCode)) {
                throw new IllegalArgumentException("NEXT_DOMAIN 不允许切回已关闭知识域");
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
        String signal = "NEXT_DOMAIN";
        if (existing.getEvaluationJson() != null) {
            Object s = existing.getEvaluationJson().get("signal");
            if (s instanceof String str) {
                signal = str;
            }
        }
        boolean isEnd = "END".equals(signal);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .evaluationSignal(signal)
                .streamAttemptId(isEnd ? null : existing.getAttemptId())
                .sessionStatus(isEnd ? "report_generating" : "in_progress")
                .build();
    }

    private EvaluationDecisionOutput applyBusinessGuardrails(EvaluationDecisionOutput evalOutput,
                                                             InterviewQuestion currentQuestion,
                                                             InterviewSession session) {
        if (evalOutput == null) {
            return null;
        }
        if (evalOutput.getNextStrategy() != null) {
            sanitizeSingleFocusStrategy(evalOutput.getNextStrategy());
        }
        if ("INTRO".equalsIgnoreCase(currentQuestion.getQuestionType())) {
            applyIntroGuardrails(evalOutput, currentQuestion, session);
            return evalOutput;
        }
        enforceSignalGuardrails(evalOutput, currentQuestion, session);
        return evalOutput;
    }

    private void applyIntroGuardrails(EvaluationDecisionOutput evalOutput,
                                      InterviewQuestion currentQuestion,
                                      InterviewSession session) {
        int introCount = getQuestionTypeCount(session, "INTRO");
        boolean secondIntroOrMore = introCount >= 1;
        String signal = safeString(evalOutput.getSignal()).trim().toUpperCase(Locale.ROOT);

        if (secondIntroOrMore) {
            if ("END".equals(signal) || "RETRY_SAME_DOMAIN".equals(signal) || "DEEPEN".equals(signal)) {
                evalOutput.setSignal("NEXT_DOMAIN");
                evalOutput.setDeepen(false);
            }
        } else if ("END".equals(signal) || "DEEPEN".equals(signal)) {
            evalOutput.setSignal("RETRY_SAME_DOMAIN");
            evalOutput.setPassCurrentLevel(false);
            evalOutput.setDeepen(false);
        }

        signal = safeString(evalOutput.getSignal()).trim().toUpperCase(Locale.ROOT);
        if ("RETRY_SAME_DOMAIN".equals(signal)) {
            evalOutput.setPassCurrentLevel(false);
            evalOutput.setDeepen(false);
            evalOutput.setNextStrategy(buildIntroRetryStrategy(evalOutput.getNextStrategy(), secondIntroOrMore));
            return;
        }
        if ("NEXT_DOMAIN".equals(signal)) {
            evalOutput.setDeepen(false);
            guardNextDomain(evalOutput, currentQuestion, session);
            return;
        }
        if ("END".equals(signal)) {
            evalOutput.setSignal("NEXT_DOMAIN");
            evalOutput.setDeepen(false);
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
                .nextDomainId(null)
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

    private void enforceSignalGuardrails(EvaluationDecisionOutput evalOutput,
                                         InterviewQuestion currentQuestion,
                                         InterviewSession session) {
        String signal = safeString(evalOutput.getSignal()).trim().toUpperCase(Locale.ROOT);
        if ("END".equals(signal) || evalOutput.getNextStrategy() == null) {
            return;
        }
        if ("DEEPEN".equals(signal)) {
            guardDeepen(evalOutput, currentQuestion, session);
            return;
        }
        if ("RETRY_SAME_DOMAIN".equals(signal)) {
            evalOutput.setPassCurrentLevel(false);
            evalOutput.setDeepen(false);
            evalOutput.setNextStrategy(buildRetrySameDomainStrategy(evalOutput.getNextStrategy(), currentQuestion, session));
            return;
        }
        if ("NEXT_DOMAIN".equals(signal)) {
            guardNextDomain(evalOutput, currentQuestion, session);
        }
    }

    private void guardDeepen(EvaluationDecisionOutput evalOutput,
                             InterviewQuestion currentQuestion,
                             InterviewSession session) {
        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        String domainTargetDepth = resolveDomainTargetDepth(session, currentDomainCode, currentDepth);
        if (depthIndex(currentDepth) >= depthIndex(domainTargetDepth)) {
            evalOutput.setDeepen(false);
            evalOutput.setSignal("NEXT_DOMAIN");
            guardNextDomain(evalOutput, currentQuestion, session);
            return;
        }

        String boundedDepth = minDepth(nextDepth(currentDepth), domainTargetDepth);
        evalOutput.setPassCurrentLevel(true);
        evalOutput.setDeepen(true);
        evalOutput.setNextStrategy(buildSameDomainStrategy(
                evalOutput.getNextStrategy(),
                currentQuestion,
                session,
                boundedDepth,
                resolveQuestionType(evalOutput.getNextStrategy().getQuestionType())
        ));
    }

    private void guardNextDomain(EvaluationDecisionOutput evalOutput,
                                 InterviewQuestion currentQuestion,
                                 InterviewSession session) {
        String currentDomainCode = resolveDomainCode(currentQuestion, session);
        EvaluationDecisionOutput.NextQuestionStrategy base = evalOutput.getNextStrategy();
        String requestedCode = base != null ? safeString(base.getNextDomainCode()) : "";
        boolean useFallback = !isValidNextDomain(session, currentDomainCode, requestedCode);
        String nextDomainCode = useFallback
                ? selectNextDomainCode(session, currentDomainCode)
                : requestedCode;
        if (nextDomainCode == null || nextDomainCode.isBlank()) {
            evalOutput.setSignal("END");
            evalOutput.setDeepen(false);
            evalOutput.setNextStrategy(null);
            return;
        }
        if (useFallback) {
            log.info("评估决策 fallbackReason=invalid_next_domain, fallbackSelectedDomain={}, fallbackSelectorSource=syllabus_order",
                    nextDomainCode);
        }
        evalOutput.setDeepen(false);
        evalOutput.setNextStrategy(buildNextDomainStrategy(base, session, nextDomainCode));
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildRetrySameDomainStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            InterviewQuestion currentQuestion,
            InterviewSession session) {
        String currentDepth = normalizeDepth(currentQuestion.getTargetDepth());
        return buildSameDomainStrategy(
                base,
                currentQuestion,
                session,
                currentDepth,
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
        strategy.setTargetSkill(extractSingleFocus(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .focusPoint(strategy.getTargetSkill())
                .build()));
        if (strategy.getExpectedPoints() == null || strategy.getExpectedPoints().isEmpty()) {
            return;
        }
        Set<String> blockedTokens = new LinkedHashSet<>(List.of("Seata", "TCC", "Saga"));
        List<String> sanitized = strategy.getExpectedPoints().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(point -> !point.isBlank())
                .filter(point -> blockedTokens.stream().noneMatch(point::contains) || point.contains(focus))
                .map(point -> point.contains(focus) ? point : "围绕" + focus + "说明：" + point)
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
        String[] parts = candidate.split("(?i)\\band\\b|\\bor\\b|\\bvs\\b|与|和|及|以及|、|/|\\+|,|，|;|；|\\|");
        String focus = parts.length > 0 ? parts[0].trim() : candidate.trim();
        return focus.isBlank() ? candidate.trim() : focus;
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
