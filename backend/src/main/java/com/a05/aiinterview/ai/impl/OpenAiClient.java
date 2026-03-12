package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.*;
import com.a05.aiinterview.ai.prompt.PromptTemplateService;
import com.a05.aiinterview.ai.prompt.RenderedPrompt;
import com.a05.aiinterview.common.TraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * AI 客户端 OpenAI-compatible 实现（基于 Spring AI ChatClient）。
 * 当配置 {@code ai.openai.mock-enabled=false} 时激活。
 *
 * <p>使用 {@link BeanOutputConverter} 将结构化 JSON schema 注入 Prompt，
 * 避免手工正则解析，同时自动获取 Token 计数。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.openai.mock-enabled", havingValue = "false")
public class OpenAiClient implements AiClient {

    private static final int RESPONSE_PREVIEW_MAX_LEN = 120;

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final PromptProperties promptProperties;
    private final ObjectMapper objectMapper;
    @Value("${ai.openai.model:${OPENAI_MODEL:gpt-4o-mini}}")
    private String configuredModel = "unknown";

    public OpenAiClient(ChatModel chatModel,
                        PromptTemplateService promptTemplateService,
                        PromptProperties promptProperties,
                        ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.promptTemplateService = promptTemplateService;
        this.promptProperties = promptProperties;
        this.objectMapper = objectMapper;
        log.info("OpenAiClient 已初始化（Spring AI ChatClient 模式）");
    }

    // ───────────────────────────── Planner ──────────────────────────────────

    @Override
    public AiCallResult<PlannerOutput> callPlanner(PlannerInput input) {
        log.info("调用 OpenAI Planner, positionCode={}, experienceLevel={}",
                input.getPositionCode(), input.getExperienceLevel());

        BeanOutputConverter<PlannerOutput> converter = new BeanOutputConverter<>(PlannerOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            rendered = renderPrompt(PROMPT_CODE_PLANNER, buildPlannerVariables(input));
            String userPrompt = rendered.getUserPrompt() + "\n\n" + converter.getFormat();

            ChatResponse response = callChat(rendered.getSystemPrompt(), userPrompt);
            PlannerOutput output = requireConvert(converter, response, "planner");
            long latencyMs = System.currentTimeMillis() - startMs;

            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "success",
                    null,
                    response.getResult().getOutput().getText(),
                    null,
                    null
            );
            log.info("Planner 调用成功，规划知识域数={}",
                    output.getDomains() != null ? output.getDomains().size() : 0);
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_PLANNER),
                    resolvePromptVersion(rendered, PROMPT_CODE_PLANNER),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "error",
                    null,
                    null,
                    null,
                    e
            );
            throw e;
        }
    }

    // ────────────────────────── QuestionGeneration ──────────────────────────

    @Override
    public AiCallResult<QuestionGenerationOutput> callQuestionGeneration(QuestionGenerationInput input) {
        log.info("调用 OpenAI 出题, domainCode={}, questionType={}",
                input.getNextDomainCode(), input.getNextQuestionType());

        BeanOutputConverter<QuestionGenerationOutput> converter =
                new BeanOutputConverter<>(QuestionGenerationOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            rendered = renderPrompt(PROMPT_CODE_QUESTION_GENERATION,
                    buildQuestionGenerationVariables(input));
            String userPrompt = rendered.getUserPrompt() + "\n\n" + converter.getFormat();

            ChatResponse response = callChat(rendered.getSystemPrompt(), userPrompt);
            QuestionGenerationOutput output = requireConvert(converter, response, "question_generation");
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "success",
                    null,
                    response.getResult().getOutput().getText(),
                    null,
                    null
            );
            log.info("出题调用成功，stem 长度={}",
                    output.getStem() != null ? output.getStem().length() : 0);
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_QUESTION_GENERATION),
                    resolvePromptVersion(rendered, PROMPT_CODE_QUESTION_GENERATION),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "error",
                    null,
                    null,
                    null,
                    e
            );
            throw e;
        }
    }

    @Override
    public Flux<String> callQuestionGenerationStream(QuestionGenerationInput input) {
        log.info("调用 OpenAI 出题（流式）, domainCode={}", input.getNextDomainCode());
        RenderedPrompt rendered = renderPrompt(PROMPT_CODE_QUESTION_GENERATION_STREAM,
                buildQuestionGenerationStreamVariables(input));
        long startMs = System.currentTimeMillis();
        AtomicInteger responseLength = new AtomicInteger(0);
        return chatClient.prompt()
                .system(rendered.getSystemPrompt())
                .user(rendered.getUserPrompt())
                .stream()
                .content()
                .doOnNext(token -> {
                    if (token != null) {
                        responseLength.addAndGet(token.length());
                    }
                })
                .doOnComplete(() -> auditLite(
                        rendered.getPromptCode(),
                        rendered.getPromptVersion(),
                        input.getInterviewId(),
                        input.getQuestionId(),
                        input.getVariantId(),
                        System.currentTimeMillis() - startMs,
                        "success",
                        null,
                        null,
                        responseLength.get(),
                        null
                ))
                .doOnError(error -> auditLite(
                        rendered.getPromptCode(),
                        rendered.getPromptVersion(),
                        input.getInterviewId(),
                        input.getQuestionId(),
                        input.getVariantId(),
                        System.currentTimeMillis() - startMs,
                        "error",
                        null,
                        null,
                        responseLength.get(),
                        asException(error)
                ));
    }

    @Override
    public AiCallResult<String> callIntroRewrite(IntroRewriteInput input) {
        log.info("调用 OpenAI 首题改写, positionCode={}, experienceLevel={}",
                input.getPositionCode(), input.getExperienceLevel());

        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            rendered = renderPrompt(PROMPT_CODE_INTRO_REWRITE, buildIntroRewriteVariables(input));
            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            String rewritten = response.getResult().getOutput().getText();
            if (rewritten == null || rewritten.isBlank()) {
                throw new RuntimeException("intro_rewrite AI 返回空文本");
            }
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "success",
                    null,
                    rewritten,
                    null,
                    null
            );
            return buildResult(rewritten.trim(), response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_INTRO_REWRITE),
                    resolvePromptVersion(rendered, PROMPT_CODE_INTRO_REWRITE),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "error",
                    null,
                    null,
                    null,
                    e
            );
            throw e;
        }
    }

    // ────────────────────────── EvaluationDecision ──────────────────────────

    @Override
    public AiCallResult<EvaluationDecisionOutput> callEvaluationDecision(EvaluationDecisionInput input) {
        log.info("调用 OpenAI 评估决策, domainCode={}", input.getCurrentDomainCode());

        BeanOutputConverter<EvaluationDecisionOutput> converter =
                new BeanOutputConverter<>(EvaluationDecisionOutput.class);// 评估决策输出转换器
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            String evaluationPayload = buildEvalDecisionUserPrompt(input) + "\n\n" + converter.getFormat();
            rendered = renderPrompt(PROMPT_CODE_EVALUATION_DECISION,
                    Map.of("evaluationPayload", evaluationPayload));

            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            EvaluationDecisionOutput output = requireConvert(converter, response, "evaluation_decision");
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    input.getCurrentQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "success",
                    null,
                    response.getResult().getOutput().getText(),
                    null,
                    null
            );
            log.info("评估决策调用成功，signal={}", output.getSignal());
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_EVALUATION_DECISION),
                    resolvePromptVersion(rendered, PROMPT_CODE_EVALUATION_DECISION),
                    input.getInterviewId(),
                    input.getCurrentQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "error",
                    null,
                    null,
                    null,
                    e
            );
            throw e;
        }
    }

    // ─────────────────────────── ReportGeneration ───────────────────────────

    @Override
    public AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input) {
        log.info("调用 OpenAI 报告生成, positionCode={}, qaPairsCount={}",
                input.getPositionCode(),
                input.getQuestionAnswerPairs() != null ? input.getQuestionAnswerPairs().size() : 0);

        BeanOutputConverter<ReportGenerationOutput> converter =
                new BeanOutputConverter<>(ReportGenerationOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            String reportPayload = buildReportGenUserPrompt(input) + "\n\n" + converter.getFormat();
            rendered = renderPrompt(PROMPT_CODE_REPORT_GENERATION,
                    Map.of("reportPayload", reportPayload));

            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            ReportGenerationOutput output = requireConvert(converter, response, "report_generation");
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "success",
                    null,
                    response.getResult().getOutput().getText(),
                    null,
                    null
            );
            log.info("报告生成调用成功，overallScore={}", output.getOverallScore());
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_REPORT_GENERATION),
                    resolvePromptVersion(rendered, PROMPT_CODE_REPORT_GENERATION),
                    input.getInterviewId(),
                    input.getQuestionId(),
                    input.getVariantId(),
                    latencyMs,
                    "error",
                    null,
                    null,
                    null,
                    e
            );
            throw e;
        }
    }

    // ──────────────────────────── 公共工具 ──────────────────────────────────

    /**
     * 发起同步 Chat 调用，返回 ChatResponse；response 为 null 时抛出异常。
     */
    private ChatResponse callChat(String systemPrompt, String userPrompt) {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
        if (response == null) {
            throw new RuntimeException("OpenAI 返回空响应（ChatResponse is null）");
        }
        return response;
    }

    /**
     * 将模型文本输出转换为结构化 DTO；结果为 null 时抛出描述性异常。
     */
    private <T> T requireConvert(BeanOutputConverter<T> converter, ChatResponse response,
                                  String promptCode) {
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new RuntimeException(promptCode + " AI 返回空文本");
        }
        T result = converter.convert(text);
        if (result == null) {
            log.error("{} AI 响应解析失败，rawText={}", promptCode, text);
            throw new RuntimeException(promptCode + " AI 响应解析失败：BeanOutputConverter 返回 null");
        }
        return result;
    }

    /**
     * 从 ChatResponse 中提取 Token 消耗，包装为 AiCallResult。
     */
    private <T> AiCallResult<T> buildResult(T output,
                                            ChatResponse response,
                                            long latencyMs,
                                            RenderedPrompt renderedPrompt) {
        Usage usage = response.getMetadata().getUsage();
        int promptTokens = 0;
        int responseTokens = 0;
        if (usage != null) {
            if (usage.getPromptTokens() != null) {
                promptTokens = usage.getPromptTokens().intValue();
            }
            // 用 total - prompt 计算输出 token，兼容不同版本 Spring AI Usage 接口
            if (usage.getTotalTokens() != null && usage.getPromptTokens() != null) {
                responseTokens = (int) Math.max(0,
                        usage.getTotalTokens().longValue() - usage.getPromptTokens().longValue());
            }
        }
        return AiCallResult.<T>builder()
                .output(output)
                .promptCode(renderedPrompt.getPromptCode())
                .promptVersion(renderedPrompt.getPromptVersion())
                .promptTokens(promptTokens)
                .responseTokens(responseTokens)
                .latencyMs(latencyMs)
                .build();
    }

    private void auditLite(String promptCode,
                           String promptVersion,
                           Long interviewId,
                           Long questionId,
                           String variantId,
                           long latencyMs,
                           String status,
                           String fallbackReason,
                           String responseText,
                           Integer responseLength,
                           Exception error) {
        try {
            ObjectNode audit = objectMapper.createObjectNode();
            audit.put("traceId", TraceContext.getOrCreateTraceId());
            audit.put("requestId", TraceContext.getOrCreateRequestId());
            putNullableLong(audit, "interviewId", interviewId);
            putNullableLong(audit, "questionId", questionId);
            audit.put("promptCode", safeString(promptCode));
            audit.put("variantId", variantId == null ? "" : variantId);
            audit.put("model", resolveModelName());
            audit.put("latencyMs", Math.max(0, latencyMs));
            audit.put("status", status);
            audit.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
            audit.put("promptVersion", safeString(promptVersion));

            if (responseText != null) {
                audit.put("responsePreview", truncateForLog(responseText, RESPONSE_PREVIEW_MAX_LEN));
                audit.put("responseLength", responseText.length());
            } else if (responseLength != null) {
                audit.put("responseLength", Math.max(0, responseLength));
            } else {
                audit.put("responseLength", 0);
            }

            if (error != null) {
                audit.put("errorType", error.getClass().getSimpleName());
                audit.put("errorMessage", truncateForLog(error.getMessage(), 200));
            }
            log.info("ai_lite_audit={}", audit);
        } catch (Exception logError) {
            log.warn("ai_lite_audit 记录失败, promptCode={}, status={}", promptCode, status, logError);
        }
    }

    private void putNullableLong(ObjectNode audit, String fieldName, Long value) {
        if (value == null) {
            audit.putNull(fieldName);
            return;
        }
        audit.put(fieldName, value);
    }

    private String resolvePromptCode(RenderedPrompt rendered, String defaultCode) {
        if (rendered == null || rendered.getPromptCode() == null || rendered.getPromptCode().isBlank()) {
            return defaultCode;
        }
        return rendered.getPromptCode();
    }

    private String resolvePromptVersion(RenderedPrompt rendered, String promptCode) {
        if (rendered == null || rendered.getPromptVersion() == null || rendered.getPromptVersion().isBlank()) {
            return promptProperties.resolveVersion(promptCode);
        }
        return rendered.getPromptVersion();
    }

    private String resolveModelName() {
        if (configuredModel == null || configuredModel.isBlank()) {
            return "unknown";
        }
        return configuredModel;
    }

    private Exception asException(Throwable throwable) {
        if (throwable instanceof Exception e) {
            return e;
        }
        return new RuntimeException(throwable);
    }

    // ──────────────────────────── Prompt 构造 ────────────────────────────────

    private static final String PROMPT_CODE_PLANNER = "planner";
    private static final String PROMPT_CODE_QUESTION_GENERATION = "question_generation";
    private static final String PROMPT_CODE_EVALUATION_DECISION = "evaluation_decision";
    private static final String PROMPT_CODE_REPORT_GENERATION = "report_generation";
    private static final String PROMPT_CODE_QUESTION_GENERATION_STREAM = "question_generation_stream";
    private static final String PROMPT_CODE_INTRO_REWRITE = "intro_rewrite";

    private Map<String, Object> buildPlannerVariables(PlannerInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("position", safeString(input.getPositionName()));
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("jd", truncate(input.getJobDescription(), 500));
        variables.put("resumeText", truncate(input.getResumeText(), 1000));
        variables.put("focusTopics", safeString(input.getFocusTopics()));
        variables.put("domains", formatDomains(input.getDomains()));
        return variables;
    }

    private Map<String, Object> buildQuestionGenerationVariables(QuestionGenerationInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nextDomainName", safeString(input.getNextDomainName()));
        variables.put("nextDomainCode", safeString(input.getNextDomainCode()));
        variables.put("nextQuestionType", safeString(input.getNextQuestionType()));
        variables.put("targetDepth", safeString(input.getTargetDepth()));
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("askedQuestions", formatAskedQuestions(input.getAskedQuestions()));
        variables.put("ragContext", safeString(input.getRagContext()));
        variables.put("syllabus", stringifyAsJson(input.getSyllabus()));
        return variables;
    }

    private Map<String, Object> buildIntroRewriteVariables(IntroRewriteInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("candidateContext", buildCandidateContext(input));
        variables.put("basePrompt", safeString(input.getBasePrompt()));
        variables.put("recentPrompts", formatBulletLines(input.getRecentPrompts()));
        variables.put("avoidPhrases", formatBulletLines(input.getAvoidPhrases()));
        return variables;
    }

    private String buildEvalDecisionUserPrompt(EvaluationDecisionInput input) {
        StringBuilder sb = new StringBuilder("请对以下候选人回答进行评估：\n\n");
        sb.append("当前题目：").append(input.getCurrentQuestionStem()).append("\n");
        sb.append("知识域：").append(input.getCurrentDomainName())
                .append("（").append(input.getCurrentDomainCode()).append("）\n");
        sb.append("目标深度：").append(input.getCurrentTargetDepth()).append("\n");
        sb.append("题目类型：").append(input.getCurrentQuestionType()).append("\n");
        sb.append("候选人回答：\n").append(input.getAnswerText()).append("\n");

        if (input.getExpectedPoints() != null && !input.getExpectedPoints().isEmpty()) {
            sb.append("\n期望答到的要点：\n");
            input.getExpectedPoints().forEach(p -> sb.append("- ").append(p).append("\n"));
        }
        if (input.getRecentContext() != null && !input.getRecentContext().isEmpty()) {
            sb.append("\n近期 Q/A 上下文：\n");
            input.getRecentContext().forEach(ctx ->
                    sb.append("Q: ").append(ctx.getStem()).append("\n")
                            .append("A: ").append(ctx.getAnswer()).append("\n\n"));
        }

        // 语音模式下，附加表达节奏信息供 AI 评估流畅度维度
        if (input.getPauseStats() != null && !input.getPauseStats().isEmpty()) {
            sb.append("\n【表达节奏观察（语音作答）】\n");
            Object wpm = input.getPauseStats().get("wpm");
            Object longPauseCount = input.getPauseStats().get("longPauseCount");
            Object longestPauseMs = input.getPauseStats().get("longestPauseMs");
            if (wpm != null) sb.append("语速：约 ").append(wpm).append(" 字/分钟\n");
            if (longPauseCount != null) sb.append("明显停顿次数：").append(longPauseCount).append(" 次\n");
            if (longestPauseMs != null) {
                double seconds = ((Number) longestPauseMs).doubleValue() / 1000.0;
                sb.append("最长单次停顿：").append(String.format("%.1f", seconds)).append(" 秒\n");
            }
            sb.append("（回答文本中 [停顿 Xs] 标签为停顿位置标记，供参考）\n");
            sb.append("请在评估中加入【表达节奏观察】字段，简短评价候选人语言表达的流畅度。\n");
        }

        return sb.toString();
    }

    private String buildReportGenUserPrompt(ReportGenerationInput input) {
        StringBuilder sb = new StringBuilder("请根据以下面试 Q/A 记录生成评估报告：\n\n");
        sb.append("岗位：").append(input.getPositionCode()).append("\n");
        sb.append("工作年限：").append(input.getExperienceLevel()).append("\n");
        sb.append("面试标题：").append(input.getSessionTitle()).append("\n\n");

        if (input.getQuestionAnswerPairs() != null) {
            sb.append("完整 Q/A 记录（共 ").append(input.getQuestionAnswerPairs().size()).append(" 题）：\n");
            input.getQuestionAnswerPairs().forEach(pair -> {
                sb.append("---\n");
                sb.append("题目（").append(pair.getQuestionType()).append("）：")
                        .append(pair.getStem()).append("\n");
                sb.append("候选人回答：").append(pair.getAnswerText()).append("\n");
                if (pair.getExpectedPoints() != null && !pair.getExpectedPoints().isEmpty()) {
                    sb.append("参考要点：")
                            .append(String.join("、", pair.getExpectedPoints())).append("\n");
                }
            });
        }
        return sb.toString();
    }

    private RenderedPrompt renderPrompt(String promptCode, Map<String, Object> variables) {
        String promptVersion = promptProperties.resolveVersion(promptCode);
        RenderedPrompt rendered = promptTemplateService.render(promptCode, promptVersion, variables);
        log.info("Prompt 渲染完成, promptCode={}, promptVersion={}",
                rendered.getPromptCode(), rendered.getPromptVersion());
        return rendered;
    }

    private Map<String, Object> buildQuestionGenerationStreamVariables(QuestionGenerationInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nextDomainName", safeString(input.getNextDomainName()));
        variables.put("nextDomainCode", safeString(input.getNextDomainCode()));
        variables.put("nextQuestionType", safeString(input.getNextQuestionType()));
        variables.put("targetDepth", safeString(input.getTargetDepth()));
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("askedQuestions", formatAskedQuestions(input.getAskedQuestions()));
        variables.put("syllabus", stringifyAsJson(input.getSyllabus()));
        return variables;
    }

    private String formatAskedQuestions(List<QuestionGenerationInput.AskedQuestion> askedQuestions) {
        if (askedQuestions == null || askedQuestions.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (QuestionGenerationInput.AskedQuestion question : askedQuestions) {
            String summary = question != null ? safeString(question.getStemSummary()) : "";
            if (summary.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- ").append(summary);
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    private String formatBulletLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- ").append(line);
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    private String buildCandidateContext(IntroRewriteInput input) {
        return "positionCode=" + safeString(input.getPositionCode())
                + ", experienceLevel=" + safeString(input.getExperienceLevel())
                + ", mode=" + safeString(input.getMode());
    }

    private String formatDomains(List<PlannerInput.DomainInfo> domains) {
        if (domains == null || domains.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (PlannerInput.DomainInfo domain : domains) {
            if (domain == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- id=").append(domain.getDomainId())
                    .append(", code=").append(safeString(domain.getDomainCode()))
                    .append(", name=").append(safeString(domain.getDomainName()));
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    private String stringifyAsJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Prompt 变量 JSON 序列化失败，回退为字符串, valueType={}",
                    value.getClass().getName(), e);
            return String.valueOf(value);
        }
    }

    private String truncateForLog(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
