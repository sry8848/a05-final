package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.*;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 回答提交主服务——逐题循环核心入口。
 *
 * <p>按策略文档主链路处理每一次候选人提交（M2 升级后共 7 步）：
 * <ol>
 *   <li>幂等校验 attempt_id（同 ID 直接返回历史结果）</li>
 *   <li>读取会话、主考纲、账本、历史题目</li>
 *   <li>组装上下文（近题全量 Q/A，更早仅题目）</li>
 *   <li>调用评估决策 AI</li>
 *   <li>应用账本 Patch（同会话行锁，保证串行）</li>
 *   <li>若 signal=END 则触发报告生成；否则保存完整下一题策略至 evaluationJson</li>
 *   <li>返回 streamAttemptId，前端凭此调用 SSE 端点获取实时题目流</li>
 * </ol>
 *
 * <p>M2 变更说明：下一题不再在此同步生成，而是由 {@code QuestionStreamService} 在 SSE 流中实时生成落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerSubmitService {

    private final AiClient aiClient;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final StateLedgerPatchService stateLedgerPatchService;
    private final AiInvocationLogService aiInvocationLogService;
    private final ReportGenerationService reportGenerationService;
    private final PromptProperties promptProperties;

    /**
     * 提交候选人回答，完整执行 8 步主链路。
     *
     * @param sessionId 面试会话 ID
     * @param userId    当前登录用户 ID（用于归属校验）
     * @param request   提交请求（questionId、attemptId、answerText）
     * @return 下一题信息和当前会话状态
     */
    public SubmitAttemptResponse submitAnswer(Long sessionId, Long userId, SubmitAttemptRequest request) {
        log.info("提交回答主链路开始, sessionId={}, questionId={}, attemptId={}",
                sessionId, request.getQuestionId(), request.getAttemptId());

        // ── Step 1：幂等校验 ──────────────────────────────────────
        InterviewAttempt existing = interviewAttemptMapper.selectByAttemptId(request.getAttemptId());
        if (existing != null) {
            log.info("幂等命中，直接返回历史结果, attemptId={}", request.getAttemptId());
            return buildIdempotentResponse(existing);
        }

        // ── Step 2：读取会话、主考纲、账本、历史题目 ────────────────
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        validateSession(session, userId, sessionId);

        InterviewQuestion currentQuestion = interviewQuestionMapper.selectById(request.getQuestionId());
        validateQuestion(currentQuestion, sessionId, request.getQuestionId());

        // 加载同一 session 的所有历史题目（按题号升序）
        List<InterviewQuestion> allQuestions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)// 按题号升序排序
        );

        // 加载历史回答（用于组装 Q/A 上下文）
        List<InterviewAttempt> allAttempts = interviewAttemptMapper.selectList(
                new LambdaQueryWrapper<InterviewAttempt>()
                        .eq(InterviewAttempt::getSessionId, sessionId)
        );

        // ── Step 3：组装上下文 ────────────────────────────────────
        // 最简规则：将历史题目按题号排，近 contextWindowSize 题携带回答，更早的只带题干
        boolean forceEndByMaxQuestions = shouldForceEndByMaxQuestions(session);// 是否强制结束（超过最大题目数）
        EvaluationDecisionOutput evalOutput;// 评估决策输出

        if (forceEndByMaxQuestions) {
            evalOutput = buildForcedEndDecision(currentQuestion, session);
            log.info("达到最大题目数限制，强制结束面试, sessionId={}, currentQuestionNo={}, maxQuestions={}",
                    sessionId, session.getCurrentQuestionNo(), extractMaxQuestions(session.getStateLedgerJson()));
        } else {
            List<EvaluationDecisionInput.QaContext> recentContext =
                    buildRecentContext(allQuestions, allAttempts, session.getContextWindowSize());

                    // 调用评估决策 AI
            EvaluationDecisionInput evalInput = EvaluationDecisionInput.builder()
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
                    .pauseStats(request.getPauseStats())
                    .stateLedger(session.getStateLedgerJson())
                    .syllabusJson(session.getSyllabusJson())
                    .recentContext(recentContext)
                    .build();

            boolean evalSuccess = true;
            String evalError = null;
            AiCallResult<EvaluationDecisionOutput> evalResult = null;

            try {
                evalResult = aiClient.callEvaluationDecision(evalInput);
                evalOutput = evalResult.getOutput();
            } catch (Exception e) {
                evalSuccess = false;
                evalError = e.getMessage();
                log.error("评估决策 AI 调用失败, sessionId={}, attemptId={}", sessionId, request.getAttemptId(), e);
                throw new RuntimeException("评估决策服务暂时不可用", e);
            } finally {
                recordEvalLog(session, currentQuestion, evalSuccess, evalError, evalResult);
            }
        }

        InterviewAttempt attempt = saveAttempt(sessionId, currentQuestion.getId(), request, evalOutput);

        // ── Step 5b：应用账本 Patch（行锁串行） ─────────────────────
        if (evalOutput.getPatch() != null) {
            evalOutput.getPatch().setEvidenceQuestionId(currentQuestion.getId());
        }
        stateLedgerPatchService.applyPatch(sessionId, evalOutput.getPatch(), request.getAttemptId(), attempt.getId());

        // 更新当前题目状态为 answered
        markQuestionAnswered(currentQuestion);

        // ── Step 6：signal=END 则结束面试；否则仅返回流式标识符，下一题由 SSE 实时生成 ──
        if ("END".equals(evalOutput.getSignal())) {
            log.info("评估决策信号=END，触发结束面试并异步生成报告, sessionId={}", sessionId);
            markSessionFinishing(sessionId);
            reportGenerationService.generateAsync(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .evaluationSignal("END")
                    .streamAttemptId(null)
                    .sessionStatus("report_generating")
                    .build();
        }

        // ── Step 7：返回流式出题标识符（下一题通过 SSE QuestionStreamService 实时生成） ──
        log.info("提交回答主链路完成, sessionId={}, signal={}, 下一题将通过 SSE 流式生成, attemptId={}",
                sessionId, evalOutput.getSignal(), request.getAttemptId());

        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .evaluationSignal(evalOutput.getSignal())
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .build();
    }

    // ────────────────────────────────────────────────────────────
    // 私有方法
    // ────────────────────────────────────────────────────────────



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

    private EvaluationDecisionOutput buildForcedEndDecision(InterviewQuestion question, InterviewSession session) {
        String domainCode = resolveDomainCode(question, session);
        EvaluationDecisionOutput.LedgerPatch patch = EvaluationDecisionOutput.LedgerPatch.builder()
                .domainCode(domainCode)
                .domainId(question.getDomainId())
                .currentDepth(question.getTargetDepth())
                .domainStatus(DomainStatus.COVERED)
                .saturated(true)
                .questionType(question.getQuestionType())
                .build();

        return EvaluationDecisionOutput.builder()
                .domainCode(domainCode)
                .depthReached(question.getTargetDepth())
                .saturated(true)
                .signal("END")
                .patch(patch)
                .nextStrategy(null)
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

    /**
     * 组装最近 N 题的 Q/A 上下文。
     * 近 windowSize 题携带完整回答，更早的题目只带题干（answer 为 null）。
     * 这是策略文档"固定上下文窗口"的最简实现，防止 token 爆炸的同时保留近因效应。
     */
    private List<EvaluationDecisionInput.QaContext> buildRecentContext(
            List<InterviewQuestion> questions,
            List<InterviewAttempt> attempts,
            Integer windowSize) {

        if (questions.isEmpty()) return List.of();

        int window = windowSize != null ? windowSize : 5;

        // 以 questionId 为 key 建立回答索引
        Map<Long, String> answerMap = attempts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsFinal()) && a.getAnswerText() != null)
                .collect(Collectors.toMap(
                        InterviewAttempt::getQuestionId,
                        InterviewAttempt::getAnswerText,
                        (a, b) -> b  // 同一题保留最新
                ));

        int total = questions.size();
        int cutoff = total - window; // 此序号之前的题目只传题干

        List<EvaluationDecisionInput.QaContext> ctx = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            InterviewQuestion q = questions.get(i);
            // 近 window 题才携带回答
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

    /**
     * 保存 attempt 记录（含评估结果快照）。
     */
    private InterviewAttempt saveAttempt(Long sessionId, Long questionId,
                                         SubmitAttemptRequest request,
                                         EvaluationDecisionOutput evalOutput) {
        // 将评估结果序列化为 Map 快照
        Map<String, Object> evalSnapshot = new LinkedHashMap<>();
        evalSnapshot.put("signal", evalOutput.getSignal());
        evalSnapshot.put("depthReached", evalOutput.getDepthReached());
        evalSnapshot.put("saturated", evalOutput.isSaturated());
        evalSnapshot.put("reasoning", evalOutput.getReasoning());
        // 保存完整策略：QuestionStreamService 需要这些字段重建出题入参
        if (evalOutput.getNextStrategy() != null) {
            EvaluationDecisionOutput.NextQuestionStrategy strat = evalOutput.getNextStrategy();
            Map<String, Object> ns = new LinkedHashMap<>();
            ns.put("nextDomainId", strat.getNextDomainId());
            ns.put("nextDomainCode", strat.getNextDomainCode());
            ns.put("nextDomainName", strat.getNextDomainName());
            ns.put("questionType", strat.getQuestionType());
            ns.put("targetDepth", strat.getTargetDepth());
            ns.put("focusPoint", strat.getFocusPoint());
            evalSnapshot.put("nextStrategy", ns);
        }

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setAttemptId(request.getAttemptId());
        attempt.setAnswerText(request.getAnswerText());
        attempt.setIsFinal(Boolean.TRUE.equals(request.getIsFinal()));
        attempt.setEvaluationJson(evalSnapshot);
        attempt.setCreatedAt(LocalDateTime.now());
        interviewAttemptMapper.insert(attempt);

        log.info("attempt 落库完成, attemptId={}, attemptPK={}", request.getAttemptId(), attempt.getId());
        return attempt;
    }

    /**
     * 将已存在的 attempt 转化为幂等返回（从 evaluationJson 快照中还原响应）。
     * M2 升级后：不再查找 nextQuestion，而是返回 streamAttemptId；
     * 前端使用该 ID 重新打开 SSE，QuestionStreamService 会从 Redis 缓存中重放已生成内容。
     */
    private SubmitAttemptResponse buildIdempotentResponse(InterviewAttempt existing) {
        String signal = "NEXT_DOMAIN";
        if (existing.getEvaluationJson() != null) {
            Object s = existing.getEvaluationJson().get("signal");
            if (s instanceof String str) signal = str;
        }

        boolean isEnd = "END".equals(signal);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .evaluationSignal(signal)
                .streamAttemptId(isEnd ? null : existing.getAttemptId())
                .sessionStatus(isEnd ? "report_generating" : "in_progress")
                .build();
    }

    private void markQuestionAnswered(InterviewQuestion question) {
        InterviewQuestion update = new InterviewQuestion();
        update.setId(question.getId());
        update.setStatus("answered");
        update.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.updateById(update);
    }

    private void markSessionFinishing(Long sessionId) {
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStatus("report_generating");
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);
    }

    /**
     * 记录评估决策 AI 调用审计日志（含 Token 计数）。
     */
    private void recordEvalLog(InterviewSession session, InterviewQuestion question,
                                boolean success, String errorMsg,
                                AiCallResult<EvaluationDecisionOutput> result) {
        AiInvocationLog logEntry = AiInvocationLog.builder()
                .sessionId(session.getId())
                .questionId(question.getId())
                .userId(session.getUserId())
                .promptCode(resolvePromptCode(result, "evaluation_decision"))
                .promptVersion(resolvePromptVersion(result,
                        promptProperties.resolveVersion("evaluation_decision")))
                .modelProvider(session.getModelProvider() != null ? session.getModelProvider() : "unknown")
                .modelName(session.getModelName() != null ? session.getModelName() : "")
                .requestTokens(result != null ? result.getPromptTokens() : 0)
                .responseTokens(result != null ? result.getResponseTokens() : 0)
                .latencyMs(result != null ? (int) result.getLatencyMs() : 0)
                .success(success)
                .errorMessage(errorMsg)
                .createdAt(LocalDateTime.now())
                .build();
        aiInvocationLogService.saveAsync(logEntry);
    }

    private String resolvePromptCode(AiCallResult<?> result, String defaultCode) {
        if (result == null || result.getPromptCode() == null || result.getPromptCode().isBlank()) {
            return defaultCode;
        }
        return result.getPromptCode();
    }

    private String resolvePromptVersion(AiCallResult<?> result, String defaultVersion) {
        if (result == null || result.getPromptVersion() == null || result.getPromptVersion().isBlank()) {
            return defaultVersion;
        }
        return result.getPromptVersion();
    }

    /**
     * 从 generationContextJson 中提取 domainCode（兜底空字符串）。
     */
    private String resolveDomainCodeFromGenCtx(InterviewQuestion q) {
        if (q.getGenerationContextJson() == null) return "";
        Object code = q.getGenerationContextJson().get("domainCode");
        return code instanceof String s ? s : "";
    }

    /**
     * 从题目实体解析知识域编码，优先从 generationContextJson 中取，兜底 "intro"。
     */
    private String resolveDomainCode(InterviewQuestion q, InterviewSession session) {
        String fromCtx = resolveDomainCodeFromGenCtx(q);
        if (!fromCtx.isBlank()) return fromCtx;
        return "intro";
    }

    /**
     * 从账本 domain_states 查找当前题目所属域的中文名。
     */
    private String resolveDomainName(InterviewQuestion q, InterviewSession session) {
        String domainCode = resolveDomainCode(q, session);
        if (session.getSyllabusJson() == null) return domainCode;

        Object domainsObj = session.getSyllabusJson().get("domains");
        if (!(domainsObj instanceof List<?> domains)) return domainCode;

        for (Object d : domains) {
            if (!(d instanceof Map<?, ?> dm)) continue;
            if (domainCode.equals(dm.get("domainCode"))) {
                Object name = dm.get("domainName");
                if (name instanceof String s) return s;
            }
        }
        return domainCode;
    }
}
