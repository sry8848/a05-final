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
                logAiDebug(session, evalCallResult, evalOutput);
            } catch (Exception e) {
                log.error("评估决策 AI 调用失败, sessionId={}, attemptId={}", sessionId, request.getAttemptId(), e);
                throw new RuntimeException("评估决策服务暂时不可用", e);
            }
        }

        evalOutput = applyBusinessGuardrails(evalOutput, currentQuestion, session);
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

    private void logAiDebug(InterviewSession session,
                            AiCallResult<EvaluationDecisionOutput> evalCallResult,
                            EvaluationDecisionOutput evalOutput) {
        log.info("╔══════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                              AI 调试信息                                      ║");
        log.info("╠══════════════════════════════════════════════════════════════════════════════╣");
        log.info("║ Prompt: code={}, version={}", evalCallResult.getPromptCode(), evalCallResult.getPromptVersion());
        log.info("╠──────────────────────────────────────────────────────────────────────────────╣");
        log.info("║ 系统提示词:");
        log.info("║ {}", evalCallResult.getSystemPrompt());
        log.info("╠──────────────────────────────────────────────────────────────────────────────╣");
        log.info("║ 用户提示词:");
        log.info("║ {}", evalCallResult.getUserPrompt());
        log.info("╠──────────────────────────────────────────────────────────────────────────────╣");
        log.info("║ AI 原始返回:");
        log.info("║ {}", evalCallResult.getRawResponse());
        log.info("╠──────────────────────────────────────────────────────────────────────────────╣");
        log.info("║ 解析后输出: signal={}, passCurrentLevel={}, deepen={}",
                evalOutput.getSignal(), evalOutput.isPassCurrentLevel(), evalOutput.isDeepen());
        log.info("╠──────────────────────────────────────────────────────────────────────────────╣");
        log.info("║ Token 使用: prompt={}, response={}, total={}, latency={}ms",
                evalCallResult.getPromptTokens(), evalCallResult.getResponseTokens(),
                evalCallResult.getPromptTokens() + evalCallResult.getResponseTokens(),
                evalCallResult.getLatencyMs());
        log.info("╠──────────────────────────────────────────────────────────────────────────────╣");
        log.info("║ 状态账本:");
        try {
            log.info("║ {}", objectMapper.writeValueAsString(session.getStateLedgerJson()));
        } catch (Exception e) {
            log.info("║ [序列化失败: {}]", e.getMessage());
        }
        log.info("╚══════════════════════════════════════════════════════════════════════════════╝");
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
        if ("INTRO".equalsIgnoreCase(currentQuestion.getQuestionType())) {
            if ("DEEPEN".equalsIgnoreCase(evalOutput.getSignal())
                    || "RETRY_SAME_DOMAIN".equalsIgnoreCase(evalOutput.getSignal())) {
                evalOutput.setSignal("NEXT_DOMAIN");
                evalOutput.setDeepen(false);
            }
        }
        if (evalOutput.getNextStrategy() != null) {
            sanitizeSingleFocusStrategy(evalOutput.getNextStrategy());
        }
        enforceSignalGuardrails(evalOutput, currentQuestion, session);
        if ("INTRO".equalsIgnoreCase(currentQuestion.getQuestionType())
                && "FRESH_GRAD".equalsIgnoreCase(session.getExperienceLevel())
                && !"END".equalsIgnoreCase(evalOutput.getSignal())
                && evalOutput.getNextStrategy() != null) {
            evalOutput.setNextStrategy(buildFreshGradIntroGuardrailStrategy(evalOutput.getNextStrategy(), session));
        }
        return evalOutput;
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildFreshGradIntroGuardrailStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            InterviewSession session) {
        String nextDomainCode = safeString(base.getNextDomainCode());
        Long nextDomainId = base.getNextDomainId();
        String nextDomainName = safeString(base.getNextDomainName());

        Map<String, Object> syllabusDomain = findSyllabusDomain(session, nextDomainCode, nextDomainId);
        if (syllabusDomain != null) {
            if (nextDomainCode.isBlank()) {
                nextDomainCode = safeString(syllabusDomain.get("domainCode"));
            }
            if (nextDomainName.isBlank()) {
                nextDomainName = safeString(syllabusDomain.get("domainName"));
            }
            if (nextDomainId == null) {
                nextDomainId = toLong(syllabusDomain.get("domainId"));
            }
        }

        String focus = extractSingleFocus(base);
        if (focus.isBlank() && syllabusDomain != null) {
            focus = extractFocusFromDomain(syllabusDomain);
        }
        if (focus.isBlank()) {
            focus = "基础能力";
        }
        if (nextDomainCode.isBlank()) {
            nextDomainCode = "java_core";
        }
        if (nextDomainName.isBlank()) {
            nextDomainName = nextDomainCode;
        }

        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(nextDomainId)
                .nextDomainCode(nextDomainCode)
                .nextDomainName(nextDomainName)
                .questionType(resolveQuestionType(base.getQuestionType()))
                .targetDepth("L2")
                .difficulty("L2")
                .focusPoint(focus)
                .targetSkill(focus + " 基础原理与实践")
                .expectedPoints(List.of(
                        "说明" + focus + "的核心概念与适用场景",
                        "结合项目说明" + focus + "的基础落地实现",
                        "给出一个" + focus + "常见问题及排查思路"
                ))
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
        String nextDomainCode = isValidNextDomain(session, currentDomainCode, requestedCode)
                ? requestedCode
                : selectNextDomainCode(session, currentDomainCode);
        if (nextDomainCode == null || nextDomainCode.isBlank()) {
            evalOutput.setSignal("END");
            evalOutput.setDeepen(false);
            evalOutput.setNextStrategy(null);
            return;
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

    private EvaluationDecisionOutput.NextQuestionStrategy buildNextDomainStrategy(
            EvaluationDecisionOutput.NextQuestionStrategy base,
            InterviewSession session,
            String nextDomainCode) {
        boolean reuseBase = base != null && nextDomainCode.equals(safeString(base.getNextDomainCode()));
        Map<String, Object> syllabusDomain = findSyllabusDomain(session, nextDomainCode, reuseBase ? base.getNextDomainId() : null);
        String nextDomainName = syllabusDomain != null ? safeString(syllabusDomain.get("domainName")) : nextDomainCode;
        Long nextDomainId = syllabusDomain != null ? toLong(syllabusDomain.get("domainId")) : (reuseBase ? base.getNextDomainId() : null);
        String domainTargetDepth = resolveDomainTargetDepth(session, nextDomainCode, "L2");
        String targetDepth = reuseBase ? minDepth(normalizeDepth(base.getTargetDepth()), domainTargetDepth) : domainTargetDepth;
        String questionType = reuseBase ? resolveQuestionType(base.getQuestionType()) : "PRINCIPLE";
        String focus = reuseBase
                ? extractSingleFocus(base)
                : extractFocusFromDomain(syllabusDomain != null ? syllabusDomain : Map.of("domainName", nextDomainName));
        List<String> expectedPoints = reuseBase && base.getExpectedPoints() != null && !base.getExpectedPoints().isEmpty()
                ? base.getExpectedPoints()
                : buildExpectedPointsForFocus(focus);
        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(nextDomainId)
                .nextDomainCode(nextDomainCode)
                .nextDomainName(nextDomainName)
                .questionType(questionType)
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .focusPoint(focus)
                .targetSkill(focus)
                .expectedPoints(expectedPoints)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String selectNextDomainCode(InterviewSession session, String currentDomainCode) {
        if (session.getStateLedgerJson() == null) {
            return null;
        }
        Object domainStatesObj = session.getStateLedgerJson().get("domain_states");
        if (!(domainStatesObj instanceof List<?> states)) {
            return null;
        }
        String inProgressFallback = null;
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> state = new LinkedHashMap<>((Map<String, Object>) raw);
            String code = safeString(state.get("domain_id"));
            String status = safeString(state.get("status")).toUpperCase(Locale.ROOT);
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
        if (candidateCode == null || candidateCode.isBlank() || Objects.equals(candidateCode, currentDomainCode)) {
            return false;
        }
        return !isCoveredDomain(session, candidateCode);
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
        Map<String, Object> fallback = null;
        for (Object item : domains) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> domain = new LinkedHashMap<>();
            raw.forEach((k, v) -> domain.put(String.valueOf(k), v));
            String code = safeString(domain.get("domainCode"));
            Long id = toLong(domain.get("domainId"));
            String priority = safeString(domain.get("priority"));
            if (fallback == null || "high".equalsIgnoreCase(priority)) {
                fallback = domain;
            }
            if (!nextDomainCode.isBlank() && nextDomainCode.equals(code)) {
                return domain;
            }
            if (nextDomainId != null && Objects.equals(nextDomainId, id)) {
                return domain;
            }
        }
        return fallback;
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
