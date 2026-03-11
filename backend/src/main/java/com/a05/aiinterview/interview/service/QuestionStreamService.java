package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.interview.dto.SseDeltaEvent;
import com.a05.aiinterview.interview.dto.SseDoneEvent;
import com.a05.aiinterview.interview.dto.SseErrorEvent;
import com.a05.aiinterview.interview.dto.SseStartEvent;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import com.a05.aiinterview.speech.service.TtsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * SSE 题目流式生成服务，支持断点续传。
 *
 * <p>核心流程：
 * <ol>
 *   <li>检查 Redis 缓存状态，若已完成则直接回放</li>
 *   <li>从 attempt.evaluationJson 提取下一题策略</li>
 *   <li>调用 RAG 检索相关知识点作为上下文</li>
 *   <li>调用 {@link AiClient#callQuestionGenerationStream} 流式生成 token</li>
 *   <li>将 delta 实时写入 Redis 缓存，支持断点续传</li>
 *   <li>流式结束后持久化题目实体，发送 done 事件</li>
 * </ol>
 *
 * <p>SSE 事件格式：
 * <pre>
 * event: start  开始事件，包含 generationId 和 sessionId
 * event: delta  每个 token 生成事件，id 为 chunk 索引，data 为文本片段
 * event: done   流式结束事件，包含 questionId
 * event: error  错误事件
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionStreamService {

    private final AiClient aiClient;
    private final PromptProperties promptProperties;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final AiInvocationLogService aiInvocationLogService;
    private final RagRetrievalService ragRetrievalService;
    private final TtsService ttsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sse.generation-cache-ttl-seconds:1800}")
    private int cacheTtlSeconds;

    private static final String KEY_STATUS = "sse:gen:%s:status";
    private static final String KEY_CHUNKS = "sse:gen:%s:chunks";
    private static final String KEY_QUESTION_ID = "sse:gen:%s:questionId";

    private static final String STATUS_STREAMING = "streaming";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_ERROR = "error";

    /**
     * 题目流式生成入口，支持断点续传和 SSE 事件推送。
     *
     * @param sessionId   面试会话 ID
     * @param userId      用户 ID，用于权限校验
     * @param attemptId   作答记录 ID，作为本次生成的唯一标识，前端应在提交答案后获得
     * @param lastEventId 上次接收的事件 ID，用于断点续传；若为 null 则从头开始
     * @return SSE 事件流，包含 start、delta...、done | error
     */
    public Flux<ServerSentEvent<String>> streamQuestion(
            Long sessionId, Long userId, String attemptId, String lastEventId) {
        log.info("题目流式生成请求开始, sessionId={}, attemptId={}, lastEventId={}", sessionId, attemptId, lastEventId);

        String statusKey = String.format(KEY_STATUS, attemptId);
        String cachedStatus = redisTemplate.opsForValue().get(statusKey);

        if (STATUS_DONE.equals(cachedStatus)) {
            log.info("题目流式生成已完成，从缓存回放, attemptId={}", attemptId);
            return replayFromCache(attemptId, lastEventId);
        }

        if (STATUS_STREAMING.equals(cachedStatus)) {
            log.warn("题目流式生成正在进行中，拒绝并发请求, attemptId={}", attemptId);
            return Flux.just(buildErrorEvent("CONCURRENT_REQUEST", "Question generation is already running, please retry shortly"));
        }

        return startNewStream(sessionId, userId, attemptId);
    }

    // ==================== 流式生成核心逻辑 ====================

    /**
     * 启动新的流式生成流程，校验权限后调用 AI。
     */
    private Flux<ServerSentEvent<String>> startNewStream(Long sessionId, Long userId, String attemptId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Flux.just(buildErrorEvent("UNAUTHORIZED", "No permission to access this interview session"));
        }

        InterviewAttempt attempt = interviewAttemptMapper.selectByAttemptId(attemptId);
        if (attempt == null) {
            return Flux.just(buildErrorEvent("ATTEMPT_NOT_FOUND", "Attempt record not found, submit answer first"));
        }

        EvaluationDecisionOutput.NextQuestionStrategy strategy = extractStrategy(attempt);
        if (strategy == null) {
            return Flux.just(buildErrorEvent("NO_STRATEGY", "No next-question strategy in evaluation result"));
        }

        List<InterviewQuestion> historyQuestions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)
        );

        RagContext ragContext = safeRetrieveRag(session, strategy);

        QuestionGenerationInput genInput = buildGenInput(session, strategy, historyQuestions, ragContext);

        String statusKey = String.format(KEY_STATUS, attemptId);
        redisTemplate.opsForValue().set(statusKey, STATUS_STREAMING, cacheTtlSeconds, TimeUnit.SECONDS);

        return buildSseFlux(session, strategy, genInput, attemptId);
    }

    // ==================== SSE 事件流构建 ====================

    /**
     * 构建完整的 SSE 事件流（start + delta... + done/error）。
     * 使用 {@link Sinks.One} 在 doOnComplete/doOnError 中发送终止事件，确保顺序正确。
     */
    private Flux<ServerSentEvent<String>> buildSseFlux(
            InterviewSession session,
            EvaluationDecisionOutput.NextQuestionStrategy strategy,
            QuestionGenerationInput genInput,
            String attemptId) {

        AtomicInteger chunkIndex = new AtomicInteger(0);
        AtomicReference<StringBuilder> fullText = new AtomicReference<>(new StringBuilder());
        String chunksKey = String.format(KEY_CHUNKS, attemptId);

        // 用于在流结束时发送 done 或 error 事件的 Sink
        Sinks.One<ServerSentEvent<String>> completionSink = Sinks.one();

        Flux<ServerSentEvent<String>> deltaFlux = aiClient.callQuestionGenerationStream(genInput)
                // 切换到 boundedElastic 调度器执行阻塞操作，避免阻塞 Reactor 事件循环线程
                .publishOn(Schedulers.boundedElastic())
                .map(token -> {
                    fullText.get().append(token);
                    int idx = chunkIndex.getAndIncrement();
                    redisTemplate.opsForList().rightPush(chunksKey, token);
                    redisTemplate.expire(chunksKey, cacheTtlSeconds, TimeUnit.SECONDS);
                    return buildDeltaEvent(token, idx);
                })
                .doOnComplete(() -> {
                    String finalStem = fullText.get().toString();
                    log.info("AI 题目流式生成完成, attemptId={}, stemLen={}", attemptId, finalStem.length());
                    try {
                        InterviewQuestion question = saveQuestion(session, strategy, finalStem);
                        // 更新 Redis 缓存状态和 questionId
                        String statusKey = String.format(KEY_STATUS, attemptId);
                        String questionIdKey = String.format(KEY_QUESTION_ID, attemptId);
                        redisTemplate.opsForValue().set(statusKey, STATUS_DONE, cacheTtlSeconds, TimeUnit.SECONDS);
                        redisTemplate.opsForValue().set(questionIdKey, String.valueOf(question.getId()),
                                cacheTtlSeconds, TimeUnit.SECONDS);
                        recordStreamLog(session, question.getId(), true, null, chunkIndex.get());
                        boolean ttsReady = ttsService.triggerQuestionAudioAsync(
                                session.getId(), question.getId(), finalStem);
                        completionSink.tryEmitValue(buildDoneEvent(
                                attemptId, session.getId(), question.getId(), chunkIndex.get(), ttsReady));
                    } catch (Exception e) {
                        log.error("题目流式生成后保存失败, attemptId={}", attemptId, e);
                        String statusKey = String.format(KEY_STATUS, attemptId);
                        redisTemplate.opsForValue().set(statusKey, STATUS_ERROR, cacheTtlSeconds, TimeUnit.SECONDS);
                        recordStreamLog(session, null, false, e.getMessage(), chunkIndex.get());
                        completionSink.tryEmitValue(buildErrorEvent("SAVE_FAILED", "题目保存失败，请稍后重试"));
                    }
                })
                .onErrorResume(e -> {
                    // AI 调用失败时记录日志并返回 error 事件，deltaFlux 不会包含任何 delta 事件
                    log.error("AI 题目流式生成异常, sessionId={}, attemptId={}", session.getId(), attemptId, e);
                    String statusKey = String.format(KEY_STATUS, attemptId);
                    redisTemplate.opsForValue().set(statusKey, STATUS_ERROR, cacheTtlSeconds, TimeUnit.SECONDS);
                    recordStreamLog(session, null, false, e.getMessage(), chunkIndex.get());
                    completionSink.tryEmitValue(
                            buildErrorEvent("AI_STREAM_ERROR", "题目生成服务暂时不可用，" +
                                    (e.getMessage() != null ? e.getMessage() : "请稍后重试")));
                    return Flux.empty();
                });

        return Flux.concat(
                Flux.just(buildStartEvent(attemptId, session.getId())),
                deltaFlux,
                completionSink.asMono().flux()
        );
    }

    // ==================== 缓存回放逻辑 ====================

    /**
     * 从 Redis 缓存回放已完成的题目生成结果。
     *
     * @param attemptId   生成任务 ID
     * @param lastEventId 上次接收的 delta chunk 索引，null 表示从头回放
     */
    private Flux<ServerSentEvent<String>> replayFromCache(String attemptId, String lastEventId) {
        String chunksKey = String.format(KEY_CHUNKS, attemptId);
        String questionIdKey = String.format(KEY_QUESTION_ID, attemptId);

        List<String> chunks = redisTemplate.opsForList().range(chunksKey, 0, -1);
        String qidStr = redisTemplate.opsForValue().get(questionIdKey);
        Long questionId = qidStr != null ? Long.parseLong(qidStr) : null;

        int startIdx = 0;
        if (lastEventId != null) {
            try {
                startIdx = Integer.parseInt(lastEventId) + 1;
            } catch (NumberFormatException ignored) {
            }
        }

        List<ServerSentEvent<String>> events = new ArrayList<>();
        if (chunks != null) {
            int finalStartIdx = startIdx;
            for (int i = 0; i < chunks.size(); i++) {
                if (i >= finalStartIdx) {
                    events.add(buildDeltaEvent(chunks.get(i), i));
                }
            }
        }
        events.add(buildDoneEvent(attemptId, null, questionId, chunks != null ? chunks.size() : 0, false));

        log.info("缓存回放完成, attemptId={}, totalChunks={}, replayFrom={}",
                attemptId, chunks != null ? chunks.size() : 0, startIdx);
        return Flux.fromIterable(events);
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 attempt.evaluationJson 提取下一题策略，供 AI 调用时使用。
     */
    @SuppressWarnings("unchecked")
    private EvaluationDecisionOutput.NextQuestionStrategy extractStrategy(InterviewAttempt attempt) {
        Map<String, Object> evalJson = attempt.getEvaluationJson();
        if (evalJson == null) return null;

        Object strategyObj = evalJson.get("nextStrategy");
        if (!(strategyObj instanceof Map)) return null;

        Map<String, Object> ns = (Map<String, Object>) strategyObj;
        EvaluationDecisionOutput.NextQuestionStrategy strategy =
                new EvaluationDecisionOutput.NextQuestionStrategy();
        strategy.setNextDomainId(toLong(ns.get("nextDomainId")));
        strategy.setNextDomainCode(toStr(ns.get("nextDomainCode")));
        strategy.setNextDomainName(toStr(ns.get("nextDomainName")));
        strategy.setQuestionType(toStr(ns.get("questionType")));
        strategy.setTargetDepth(toStr(ns.get("targetDepth")));
        strategy.setFocusPoint(toStr(ns.get("focusPoint")));
        return strategy;
    }

    /**
     * RAG 检索相关知识点，失败时返回空上下文不影响主流程。
     */
    private RagContext safeRetrieveRag(InterviewSession session,
                                        EvaluationDecisionOutput.NextQuestionStrategy strategy) {
        try {
            RagRetrievalRequest req = RagRetrievalRequest.builder()
                    .domainCode(strategy.getNextDomainCode())
                    .questionType(strategy.getQuestionType())
                    .targetDepth(strategy.getTargetDepth())
                    .focusPoint(strategy.getFocusPoint())
                    .positionCode(session.getTargetRole())
                    .build();
            return ragRetrievalService.retrieve(req);
        } catch (Exception e) {
            log.error("SSE 题目流式生成 RAG 检索失败，忽略继续, sessionId={}", session.getId(), e);
            return RagContext.empty();
        }
    }

    /**
     * 构建流式生成 AI 调用的入参。
     */
    private QuestionGenerationInput buildGenInput(
            InterviewSession session,
            EvaluationDecisionOutput.NextQuestionStrategy strategy,
            List<InterviewQuestion> historyQuestions,
            RagContext ragContext) {

        List<QuestionGenerationInput.AskedQuestion> asked = historyQuestions.stream()
                .map(q -> QuestionGenerationInput.AskedQuestion.builder()
                        .questionId(q.getId())
                        .questionType(q.getQuestionType())
                        .domainCode(resolveDomainCode(q))
                        .stemSummary(q.getStem() != null
                                ? q.getStem().substring(0, Math.min(80, q.getStem().length()))
                                : "")
                        .build())
                .collect(Collectors.toList());

        QuestionGenerationInput input = QuestionGenerationInput.builder()
                .positionCode(session.getTargetRole())
                .mode(session.getMode())
                .experienceLevel(session.getExperienceLevel())
                .nextDomainId(strategy.getNextDomainId())
                .nextDomainCode(strategy.getNextDomainCode())
                .nextDomainName(strategy.getNextDomainName())
                .nextQuestionType(strategy.getQuestionType())
                .targetDepth(strategy.getTargetDepth())
                .askedQuestions(asked)
                .syllabus(session.getSyllabusJson() != null ? session.getSyllabusJson() : new HashMap<>())
                .build();

        if (ragContext != null && !ragContext.isEmpty()
                && ragContext.getContextText() != null
                && !ragContext.getContextText().isBlank()) {
            input.setRagContext(ragContext.getContextText());
        }
        return input;
    }

    /**
     * 将生成的题目文本持久化到数据库，并更新 session.currentQuestionNo。
     */
    private InterviewQuestion saveQuestion(InterviewSession session,
                                            EvaluationDecisionOutput.NextQuestionStrategy strategy,
                                            String stem) {
        int nextQuestionNo = (session.getCurrentQuestionNo() != null
                ? session.getCurrentQuestionNo() : 0) + 1;

        InterviewQuestion question = new InterviewQuestion();
        question.setSessionId(session.getId());
        question.setQuestionNo(nextQuestionNo);
        question.setQuestionType(strategy.getQuestionType());
        question.setDomainId(strategy.getNextDomainId());
        question.setStem(stem);
        question.setTargetSkill(strategy.getFocusPoint());
        question.setTargetDepth(strategy.getTargetDepth());
        question.setStatus("asked");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("domainCode", strategy.getNextDomainCode());
        ctx.put("questionType", strategy.getQuestionType());
        ctx.put("targetDepth", strategy.getTargetDepth());
        ctx.put("focusPoint", strategy.getFocusPoint());
        ctx.put("generatedByStream", true);
        question.setGenerationContextJson(ctx);

        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.insert(question);

        // 同步更新 session 当前题号
        InterviewSession update = new InterviewSession();
        update.setId(session.getId());
        update.setCurrentQuestionNo(nextQuestionNo);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);

        log.info("题目流式生成保存成功, sessionId={}, questionNo={}, questionId={}",
                session.getId(), nextQuestionNo, question.getId());
        return question;
    }

    /**
     * 异步记录流式 AI 调用日志，含成功状态、错误信息、token 数量等。
     */
    private void recordStreamLog(InterviewSession session, Long questionId,
                                  boolean success, String errorMsg, int tokenCount) {
        AiInvocationLog logEntry = AiInvocationLog.builder()
                .sessionId(session.getId())
                .questionId(questionId)
                .userId(session.getUserId())
                .promptCode("question_generation_stream")
                .promptVersion(promptProperties.resolveVersion("question_generation_stream"))
                .modelProvider(session.getModelProvider() != null ? session.getModelProvider() : "unknown")
                .modelName(session.getModelName() != null ? session.getModelName() : "")
                .requestTokens(0)
                .responseTokens(tokenCount)
                .latencyMs(0)
                .success(success)
                .errorMessage(errorMsg)
                .createdAt(LocalDateTime.now())
                .build();
        aiInvocationLogService.saveAsync(logEntry);
    }

    // ==================== SSE 事件构建 ====================

    private ServerSentEvent<String> buildStartEvent(String attemptId, Long sessionId) {
        return ServerSentEvent.<String>builder()
                .event("start")
                .data(toJson(SseStartEvent.builder()
                        .generationId(attemptId)
                        .sessionId(sessionId)
                        .build()))
                .build();
    }

    private ServerSentEvent<String> buildDeltaEvent(String text, int index) {
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(index))
                .event("delta")
                .data(toJson(SseDeltaEvent.builder().text(text).build()))
                .build();
    }

    private ServerSentEvent<String> buildDoneEvent(
            String attemptId, Long sessionId, Long questionId, int totalTokens, boolean ttsReady) {
        String audioStatusUrl = (ttsReady && sessionId != null && questionId != null)
                ? String.format("/api/v1/interviews/%d/questions/%d/audio", sessionId, questionId)
                : null;
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(toJson(SseDoneEvent.builder()
                        .generationId(attemptId)
                        .questionId(questionId)
                        .totalTokens(totalTokens)
                        .ttsReady(ttsReady)
                        .audioStatusUrl(audioStatusUrl)
                        .build()))
                .build();
    }

    private ServerSentEvent<String> buildErrorEvent(String code, String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(toJson(SseErrorEvent.builder().code(code).message(message).build()))
                .build();
    }

    // ==================== 工具方法 ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("SSE 事件序列化失败, obj={}", obj, e);
            return "{}";
        }
    }

    private String resolveDomainCode(InterviewQuestion q) {
        if (q.getGenerationContextJson() == null) return "";
        Object code = q.getGenerationContextJson().get("domainCode");
        return code instanceof String s ? s : "";
    }

    private String toStr(Object val) {
        return val instanceof String s ? s : null;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
