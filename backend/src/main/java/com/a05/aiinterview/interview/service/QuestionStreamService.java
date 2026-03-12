package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.interview.dto.SseDeltaEvent;
import com.a05.aiinterview.interview.dto.SseDoneEvent;
import com.a05.aiinterview.interview.dto.SseErrorEvent;
import com.a05.aiinterview.interview.dto.SseStartEvent;
import com.a05.aiinterview.interview.dto.SseTtsReadyEvent;
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
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final RagRetrievalService ragRetrievalService;
    private final TtsService ttsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sse.generation-cache-ttl-seconds:1800}")
    private int cacheTtlSeconds;
    @Value("${sse.follow.poll-interval-ms:250}")
    private long followPollIntervalMs;
    @Value("${sse.follow.idle-timeout-seconds:30}")
    private long followIdleTimeoutSeconds;

    private static final String KEY_STATUS = "sse:gen:%s:status";
    private static final String KEY_CHUNKS = "sse:gen:%s:chunks";
    private static final String KEY_QUESTION_ID = "sse:gen:%s:questionId";
    private static final String KEY_TTS_READY = "sse:gen:%s:ttsReady";
    private static final String KEY_DONE_PAYLOAD = "sse:gen:%s:donePayload";
    private static final String KEY_ERROR_PAYLOAD = "sse:gen:%s:errorPayload";
    private static final Set<Character> SENTENCE_ENDINGS = Set.of('，', '。', '！', '？', '；');

    private static final String STATUS_STREAMING = "streaming";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_ERROR = "error";
    private static final long SEGMENT_TTS_DRAIN_TIMEOUT_SECONDS = 12;

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

        if (STATUS_ERROR.equals(cachedStatus)) {
            log.info("题目流式生成处于错误状态，从缓存回放 error, attemptId={}", attemptId);
            return replayErrorFromCache(attemptId, lastEventId);
        }

        if (STATUS_STREAMING.equals(cachedStatus)) {
            log.info("题目流式生成进行中，进入回放+尾随模式, attemptId={}", attemptId);
            return replayAndFollow(attemptId, lastEventId);
        }

        return startNewStream(sessionId, userId, attemptId, lastEventId);
    }

    // ==================== 流式生成核心逻辑 ====================

    /**
     * 启动新的流式生成流程，校验权限后调用 AI。
     */
    private Flux<ServerSentEvent<String>> startNewStream(
            Long sessionId, Long userId, String attemptId, String lastEventId) {
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
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                statusKey, STATUS_STREAMING, cacheTtlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("题目流式生成已被其他请求启动，转为回放+尾随, attemptId={}", attemptId);
            return replayAndFollow(attemptId, lastEventId);
        }

        clearGenerationCache(attemptId);
        startGenerationInBackground(session, strategy, genInput, attemptId);

        return Flux.concat(
                Flux.just(buildStartEvent(attemptId, session.getId())),
                replayAndFollow(attemptId, lastEventId)
        );
    }

    // ==================== 流式任务执行 ====================

    /**
     * 启动后台生成任务，生成过程与单个 HTTP SSE 连接解耦。
     */
    private void startGenerationInBackground(
            InterviewSession session,
            EvaluationDecisionOutput.NextQuestionStrategy strategy,
            QuestionGenerationInput genInput,
            String attemptId) {
        AtomicInteger chunkIndex = new AtomicInteger(0);
        StringBuilder fullText = new StringBuilder();
        StringBuilder sentenceBuffer = new StringBuilder();
        AtomicInteger segmentIndex = new AtomicInteger(0);
        List<CompletableFuture<Boolean>> segmentTtsTasks = new ArrayList<>();
        String chunksKey = String.format(KEY_CHUNKS, attemptId);
        String ttsReadyKey = String.format(KEY_TTS_READY, attemptId);
        String statusKey = String.format(KEY_STATUS, attemptId);

        aiClient.callQuestionGenerationStream(genInput)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        token -> onGenerationToken(
                                token,
                                session.getId(),
                                attemptId,
                                chunksKey,
                                ttsReadyKey,
                                statusKey,
                                chunkIndex,
                                segmentIndex,
                                fullText,
                                sentenceBuffer,
                                segmentTtsTasks),
                        error -> onGenerationError(attemptId, session.getId(), error),
                        () -> onGenerationCompleted(
                                session,
                                strategy,
                                attemptId,
                                statusKey,
                                chunkIndex.get(),
                                fullText.toString(),
                                sentenceBuffer,
                                segmentIndex,
                                ttsReadyKey,
                                segmentTtsTasks)
                );
    }

    private void onGenerationToken(
            String token,
            Long sessionId,
            String attemptId,
            String chunksKey,
            String ttsReadyKey,
            String statusKey,
            AtomicInteger chunkIndex,
            AtomicInteger segmentIndex,
            StringBuilder fullText,
            StringBuilder sentenceBuffer,
            List<CompletableFuture<Boolean>> segmentTtsTasks) {
        fullText.append(token);
        sentenceBuffer.append(token);
        redisTemplate.opsForList().rightPush(chunksKey, token);
        redisTemplate.expire(chunksKey, cacheTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(statusKey, cacheTtlSeconds, TimeUnit.SECONDS);
        chunkIndex.incrementAndGet();

        List<String> closedSentences = extractClosedSentences(sentenceBuffer);
        for (String sentence : closedSentences) {
            segmentTtsTasks.add(triggerSegmentTtsAsync(
                    sessionId,
                    attemptId,
                    segmentIndex.getAndIncrement(),
                    sentence,
                    ttsReadyKey));
        }
    }

    private void onGenerationCompleted(
            InterviewSession session,
            EvaluationDecisionOutput.NextQuestionStrategy strategy,
            String attemptId,
            String statusKey,
            int totalTokens,
            String finalStem,
            StringBuilder sentenceBuffer,
            AtomicInteger segmentIndex,
            String ttsReadyKey,
            List<CompletableFuture<Boolean>> segmentTtsTasks) {
        log.info("AI 题目流式生成完成, attemptId={}, stemLen={}", attemptId, finalStem.length());

        String trailing = flushTrailingSentence(sentenceBuffer);
        if (!trailing.isBlank()) {
            segmentTtsTasks.add(triggerSegmentTtsAsync(
                    session.getId(),
                    attemptId,
                    segmentIndex.getAndIncrement(),
                    trailing,
                    ttsReadyKey));
        }

        try {
            InterviewQuestion question = saveQuestion(session, strategy, finalStem);
            String questionIdKey = String.format(KEY_QUESTION_ID, attemptId);
            redisTemplate.opsForValue().set(questionIdKey, String.valueOf(question.getId()),
                    cacheTtlSeconds, TimeUnit.SECONDS);
            boolean ttsReady = ttsService.triggerQuestionAudioAsync(
                    session.getId(), question.getId(), finalStem);
            waitForSegmentTasks(segmentTtsTasks, attemptId);
            ServerSentEvent<String> doneEvent = buildDoneEvent(
                    attemptId, session.getId(), question.getId(), totalTokens, ttsReady);
            cacheDonePayload(attemptId, doneEvent.data());
            redisTemplate.opsForValue().set(statusKey, STATUS_DONE, cacheTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("题目流式生成后保存失败, attemptId={}", attemptId, e);
            onGenerationError(attemptId, session.getId(), e);
        }
    }

    private void onGenerationError(String attemptId, Long sessionId, Throwable error) {
        log.error("AI 题目流式生成异常, sessionId={}, attemptId={}", sessionId, attemptId, error);
        String statusKey = String.format(KEY_STATUS, attemptId);
        ServerSentEvent<String> errorEvent = buildErrorEvent(
                "AI_STREAM_ERROR",
                "题目生成服务暂时不可用，" + (error.getMessage() != null ? error.getMessage() : "请稍后重试"));
        cacheErrorPayload(attemptId, errorEvent.data());
        redisTemplate.opsForValue().set(statusKey, STATUS_ERROR, cacheTtlSeconds, TimeUnit.SECONDS);
    }

    // ==================== 回放与尾随 ====================

    private Flux<ServerSentEvent<String>> replayAndFollow(String attemptId, String lastEventId) {
        int startChunkIndex = parseReplayStartIndex(lastEventId);
        AtomicInteger nextChunkIndex = new AtomicInteger(startChunkIndex);
        AtomicInteger nextTtsIndex = new AtomicInteger(0);
        Duration followPollInterval = resolveFollowPollInterval(followPollIntervalMs);
        Duration followIdleTimeout = resolveFollowIdleTimeout(followIdleTimeoutSeconds);

        return Flux.interval(Duration.ZERO, followPollInterval)
                .onBackpressureDrop()
                .concatMap(tick -> pollLiveEvents(attemptId, nextChunkIndex, nextTtsIndex))
                .takeUntil(this::isTerminalEvent)
                .timeout(followIdleTimeout, Flux.just(buildErrorEvent(
                        "STREAM_INTERRUPTED",
                        "连接中断时间过长，请重试当前题目生成")));
    }

    private Flux<ServerSentEvent<String>> pollLiveEvents(
            String attemptId, AtomicInteger nextChunkIndex, AtomicInteger nextTtsIndex) {
        List<ServerSentEvent<String>> events = new ArrayList<>();
        events.addAll(readChunkEvents(attemptId, nextChunkIndex));
        events.addAll(readTtsReadyEvents(attemptId, nextTtsIndex));

        String status = redisTemplate.opsForValue().get(String.format(KEY_STATUS, attemptId));
        if (STATUS_DONE.equals(status)) {
            events.add(buildDoneEventFromCache(attemptId));
        } else if (STATUS_ERROR.equals(status)) {
            events.add(buildErrorEventFromCache(attemptId));
        }

        return events.isEmpty() ? Flux.empty() : Flux.fromIterable(events);
    }

    private List<ServerSentEvent<String>> readChunkEvents(String attemptId, AtomicInteger nextChunkIndex) {
        String chunksKey = String.format(KEY_CHUNKS, attemptId);
        List<String> chunkSlice = redisTemplate.opsForList().range(chunksKey, nextChunkIndex.get(), -1);
        if (chunkSlice == null || chunkSlice.isEmpty()) {
            return List.of();
        }
        List<ServerSentEvent<String>> events = new ArrayList<>(chunkSlice.size());
        for (String token : chunkSlice) {
            int currentIndex = nextChunkIndex.getAndIncrement();
            events.add(buildDeltaEvent(token, currentIndex));
        }
        return events;
    }

    private List<ServerSentEvent<String>> readTtsReadyEvents(String attemptId, AtomicInteger nextTtsIndex) {
        String ttsReadyKey = String.format(KEY_TTS_READY, attemptId);
        List<String> payloadSlice = redisTemplate.opsForList().range(ttsReadyKey, nextTtsIndex.get(), -1);
        if (payloadSlice == null || payloadSlice.isEmpty()) {
            return List.of();
        }
        List<ServerSentEvent<String>> events = new ArrayList<>(payloadSlice.size());
        for (String payload : payloadSlice) {
            nextTtsIndex.incrementAndGet();
            if (payload != null && !payload.isBlank()) {
                events.add(buildTtsReadyEvent(payload));
            }
        }
        return events;
    }

    private boolean isTerminalEvent(ServerSentEvent<String> event) {
        String eventName = event.event();
        return "done".equals(eventName) || "error".equals(eventName);
    }

    // ==================== 缓存回放逻辑 ====================

    /**
     * 从 Redis 缓存回放已完成的题目生成结果。
     *
     * @param attemptId   生成任务 ID
     * @param lastEventId 上次接收的 delta chunk 索引，null 表示从头回放
     */
    private Flux<ServerSentEvent<String>> replayFromCache(String attemptId, String lastEventId) {
        List<ServerSentEvent<String>> events = collectReplayEvents(attemptId, lastEventId);
        events.add(buildDoneEventFromCache(attemptId));

        log.info("缓存回放完成, attemptId={}, replayFrom={}", attemptId, lastEventId);
        return Flux.fromIterable(events);
    }

    private Flux<ServerSentEvent<String>> replayErrorFromCache(String attemptId, String lastEventId) {
        List<ServerSentEvent<String>> events = collectReplayEvents(attemptId, lastEventId);
        events.add(buildErrorEventFromCache(attemptId));
        return Flux.fromIterable(events);
    }

    private List<ServerSentEvent<String>> collectReplayEvents(String attemptId, String lastEventId) {
        String chunksKey = String.format(KEY_CHUNKS, attemptId);
        String ttsReadyKey = String.format(KEY_TTS_READY, attemptId);

        int startIdx = parseReplayStartIndex(lastEventId);
        List<String> chunks = redisTemplate.opsForList().range(chunksKey, startIdx, -1);
        List<String> ttsReadyPayloads = redisTemplate.opsForList().range(ttsReadyKey, 0, -1);

        List<ServerSentEvent<String>> events = new ArrayList<>();
        if (chunks != null) {
            for (int i = 0; i < chunks.size(); i++) {
                events.add(buildDeltaEvent(chunks.get(i), startIdx + i));
            }
        }
        if (ttsReadyPayloads != null) {
            for (String payload : ttsReadyPayloads) {
                if (payload != null && !payload.isBlank()) {
                    events.add(buildTtsReadyEvent(payload));
                }
            }
        }
        return events;
    }

    private int parseReplayStartIndex(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(lastEventId) + 1;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private ServerSentEvent<String> buildDoneEventFromCache(String attemptId) {
        String payloadKey = String.format(KEY_DONE_PAYLOAD, attemptId);
        String payload = redisTemplate.opsForValue().get(payloadKey);
        if (payload != null && !payload.isBlank()) {
            return ServerSentEvent.<String>builder().event("done").data(payload).build();
        }

        String questionIdKey = String.format(KEY_QUESTION_ID, attemptId);
        String qidStr = redisTemplate.opsForValue().get(questionIdKey);
        Long questionId = null;
        if (qidStr != null) {
            try {
                questionId = Long.parseLong(qidStr);
            } catch (NumberFormatException ignored) {
                questionId = null;
            }
        }
        String chunksKey = String.format(KEY_CHUNKS, attemptId);
        Long chunkCount = redisTemplate.opsForList().size(chunksKey);
        return buildDoneEvent(attemptId, null, questionId, chunkCount != null ? chunkCount.intValue() : 0, false);
    }

    private ServerSentEvent<String> buildErrorEventFromCache(String attemptId) {
        String payloadKey = String.format(KEY_ERROR_PAYLOAD, attemptId);
        String payload = redisTemplate.opsForValue().get(payloadKey);
        if (payload == null || payload.isBlank()) {
            payload = toJson(SseErrorEvent.builder()
                    .code("AI_STREAM_ERROR")
                    .message("题目生成服务暂时不可用，请稍后重试")
                    .build());
        }
        return ServerSentEvent.<String>builder().event("error").data(payload).build();
    }

    private void cacheDonePayload(String attemptId, String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        String key = String.format(KEY_DONE_PAYLOAD, attemptId);
        redisTemplate.opsForValue().set(key, payload, cacheTtlSeconds, TimeUnit.SECONDS);
    }

    private void cacheErrorPayload(String attemptId, String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        String key = String.format(KEY_ERROR_PAYLOAD, attemptId);
        redisTemplate.opsForValue().set(key, payload, cacheTtlSeconds, TimeUnit.SECONDS);
    }

    private void clearGenerationCache(String attemptId) {
        redisTemplate.delete(String.format(KEY_CHUNKS, attemptId));
        redisTemplate.delete(String.format(KEY_QUESTION_ID, attemptId));
        redisTemplate.delete(String.format(KEY_TTS_READY, attemptId));
        redisTemplate.delete(String.format(KEY_DONE_PAYLOAD, attemptId));
        redisTemplate.delete(String.format(KEY_ERROR_PAYLOAD, attemptId));
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
                .interviewId(session.getId())
                .questionId(null)
                .variantId(null)
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

    private ServerSentEvent<String> buildTtsReadyEvent(String payload) {
        return ServerSentEvent.<String>builder()
                .event("tts_ready")
                .data(payload)
                .build();
    }

    private ServerSentEvent<String> buildErrorEvent(String code, String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(toJson(SseErrorEvent.builder().code(code).message(message).build()))
                .build();
    }

    private String buildTtsReadyPayload(String generationId, int segmentIndex, String audioUrl) {
        return toJson(SseTtsReadyEvent.builder()
                .generationId(generationId)
                .segmentIndex(segmentIndex)
                .audioUrl(audioUrl)
                .build());
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

    private CompletableFuture<Boolean> triggerSegmentTtsAsync(
            Long sessionId,
            String attemptId,
            int segmentIndex,
            String sentence,
            String ttsReadyKey) {
        CompletableFuture<Boolean> future = ttsService.triggerQuestionSegmentAudioAsync(sessionId, attemptId, segmentIndex, sentence);
        future.whenComplete((ready, error) -> {
            if (error != null) {
                log.warn("片段 TTS 合成异常，忽略继续, attemptId={}, segmentIndex={}",
                        attemptId, segmentIndex, error);
                return;
            }
            if (!Boolean.TRUE.equals(ready)) {
                return;
            }
            String audioUrl = String.format(
                    "/api/v1/interviews/%d/attempts/%s/audio/segments/%d/file",
                    sessionId,
                    attemptId,
                    segmentIndex);
            String payload = buildTtsReadyPayload(attemptId, segmentIndex, audioUrl);
            redisTemplate.opsForList().rightPush(ttsReadyKey, payload);
            redisTemplate.expire(ttsReadyKey, cacheTtlSeconds, TimeUnit.SECONDS);
        });
        return future;
    }

    private void waitForSegmentTasks(List<CompletableFuture<Boolean>> segmentTtsTasks, String attemptId) {
        if (segmentTtsTasks.isEmpty()) {
            return;
        }
        CompletableFuture<?>[] futures = segmentTtsTasks.toArray(new CompletableFuture[0]);
        try {
            CompletableFuture.allOf(futures)
                    .orTimeout(SEGMENT_TTS_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            log.warn("等待片段 TTS 收尾超时或失败，进入 done 结束流, attemptId={}, pendingTasks={}",
                    attemptId, segmentTtsTasks.size(), e);
        }
    }

    static List<String> extractClosedSentences(StringBuilder buffer) {
        List<String> segments = new ArrayList<>();
        int sentenceStart = 0;
        for (int i = 0; i < buffer.length(); i++) {
            char current = buffer.charAt(i);
            if (!SENTENCE_ENDINGS.contains(current)) {
                continue;
            }
            String sentence = buffer.substring(sentenceStart, i + 1).trim();
            if (!sentence.isBlank()) {
                segments.add(sentence);
            }
            sentenceStart = i + 1;
        }
        if (sentenceStart > 0) {
            buffer.delete(0, sentenceStart);
        }
        return segments;
    }

    static String flushTrailingSentence(StringBuilder buffer) {
        String trailing = buffer.toString().trim();
        buffer.setLength(0);
        return trailing;
    }

    static Duration resolveFollowPollInterval(long configuredMs) {
        return Duration.ofMillis(Math.max(50L, configuredMs));
    }

    static Duration resolveFollowIdleTimeout(long configuredSeconds) {
        return Duration.ofSeconds(Math.max(5L, configuredSeconds));
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
