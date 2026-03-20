package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.contract.AiOutputContractValidator;
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
    private final AiOutputContractValidator aiOutputContractValidator;
    @Value("${ai.openai.model:${OPENAI_MODEL:gpt-4o-mini}}")
    private String configuredModel = "unknown";

    public OpenAiClient(ChatModel chatModel,
                        PromptTemplateService promptTemplateService,
                        PromptProperties promptProperties,
                        ObjectMapper objectMapper,
                        AiOutputContractValidator aiOutputContractValidator) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.promptTemplateService = promptTemplateService;
        this.promptProperties = promptProperties;
        this.objectMapper = objectMapper;
        this.aiOutputContractValidator = aiOutputContractValidator;
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
            rendered = renderPrompt(PROMPT_CODE_EVALUATION_DECISION,
                    buildEvaluationDecisionVariables(input, converter.getFormat()));

            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            EvaluationDecisionOutput output = aiOutputContractValidator.validateEvaluationDecision(
                    requireConvert(converter, response, "evaluation_decision")
            );
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
            log.info("评估决策调用成功，decision={}", output.getDecision());
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
            rendered = renderPrompt(PROMPT_CODE_REPORT_GENERATION,
                    buildReportGenerationVariables(input, converter.getFormat()));

            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            ReportGenerationOutput output = aiOutputContractValidator.validateReport(
                    requireConvert(converter, response, "report_generation")
            );
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

    @Override
    public AiCallResult<QuestionDetailEvaluationOutput> callQuestionDetailEvaluation(QuestionDetailEvaluationInput input) {
        log.info("调用 OpenAI 单题详细评估, questionId={}, domainCode={}",
                input.getQuestionId(), input.getDomainCode());

        BeanOutputConverter<QuestionDetailEvaluationOutput> converter =
                new BeanOutputConverter<>(QuestionDetailEvaluationOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            rendered = renderPrompt(PROMPT_CODE_QUESTION_DETAIL_EVALUATION,
                    buildQuestionDetailEvaluationVariables(input, converter.getFormat()));

            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            QuestionDetailEvaluationOutput output =
                    requireConvert(converter, response, "question_detail_evaluation");
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
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_QUESTION_DETAIL_EVALUATION),
                    resolvePromptVersion(rendered, PROMPT_CODE_QUESTION_DETAIL_EVALUATION),
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
     * 解析失败时会尝试提取具体字段错误信息，便于快速定位问题。
     */
    private <T> T requireConvert(BeanOutputConverter<T> converter, ChatResponse response,
                                  String promptCode) {
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new RuntimeException(promptCode + " AI 返回空文本");
        }
        try {
            T result = converter.convert(text);
            if (result == null) {
                log.error("{} AI 响应解析失败，rawText={}", promptCode, text);
                throw new RuntimeException(promptCode + " AI 响应解析失败：BeanOutputConverter 返回 null");
            }
            return result;
        } catch (Exception e) {
            // 尝试提取字段级错误信息
            String fieldHint = extractFieldErrorHint(e.getMessage());
            log.error("{} AI 响应解析失败, field={}, reason={}, rawText={}",
                    promptCode, fieldHint, e.getMessage(), text);
            throw new RuntimeException(promptCode + " AI 响应解析失败: field=" + fieldHint + ", reason=" + e.getMessage(), e);
        }
    }

    /**
     * 从异常信息中提取字段级错误提示。
     * 例如从 "Unexpected character 'a' ... at patch.evidenceQuestionId" 提取出 "patch.evidenceQuestionId"
     */
    private String extractFieldErrorHint(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "unknown";
        }
        // Jackson 错误通常包含字段路径，如 "at [Source: ...] through reference chain: ...["patch"]["evidenceQuestionId"]"
        int throughRefIdx = errorMessage.indexOf("through reference chain:");
        if (throughRefIdx > 0) {
            String refChain = errorMessage.substring(throughRefIdx);
            int bracketStart = refChain.lastIndexOf('[');
            int bracketEnd = refChain.lastIndexOf(']');
            if (bracketStart > 0 && bracketEnd > bracketStart) {
                return refChain.substring(bracketStart + 2, bracketEnd - 1);
            }
        }
        // 尝试匹配 "Unexpected character" 模式，提示可能是 UUID 未加引号
        if (errorMessage.contains("Unexpected character") && errorMessage.contains("expecting comma")) {
            return "likely_uuid_without_quotes";
        }
        return "parse_error";
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
        String rawResponse = response.getResult() != null 
                ? response.getResult().getOutput().getText() 
                : null;
        return AiCallResult.<T>builder()
                .output(output)
                .promptCode(renderedPrompt.getPromptCode())
                .promptVersion(renderedPrompt.getPromptVersion())
                .promptTokens(promptTokens)
                .responseTokens(responseTokens)
                .latencyMs(latencyMs)
                .systemPrompt(renderedPrompt.getSystemPrompt())
                .userPrompt(renderedPrompt.getUserPrompt())
                .rawResponse(rawResponse)
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
    private static final String PROMPT_CODE_EVALUATION_DECISION = "evaluation_decision";
    private static final String PROMPT_CODE_REPORT_GENERATION = "report_generation";
    private static final String PROMPT_CODE_QUESTION_GENERATION_STREAM = "question_generation_stream";
    private static final String PROMPT_CODE_INTRO_REWRITE = "intro_rewrite";
    private static final String PROMPT_CODE_QUESTION_DETAIL_EVALUATION = "question_detail_evaluation";

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

    private Map<String, Object> buildIntroRewriteVariables(IntroRewriteInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("candidateContext", buildCandidateContext(input));
        variables.put("basePrompt", safeString(input.getBasePrompt()));
        variables.put("recentPrompts", formatBulletLines(input.getRecentPrompts()));
        variables.put("avoidPhrases", formatBulletLines(input.getAvoidPhrases()));
        return variables;
    }

    private Map<String, Object> buildEvaluationDecisionVariables(EvaluationDecisionInput input, String outputSchema) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("interviewHardConstraints", stringifyAsJson(buildInterviewHardConstraints(input)));
        variables.put("currentQuestionStem", safeString(input.getCurrentQuestionStem()));
        variables.put("currentDomainName", safeString(input.getCurrentDomainName()));
        variables.put("currentDomainCode", safeString(input.getCurrentDomainCode()));
        variables.put("currentTargetDepth", safeString(input.getCurrentTargetDepth()));
        variables.put("currentQuestionType", safeString(input.getCurrentQuestionType()));
        variables.put("answerText", safeString(input.getAnswerText()));
        variables.put("resumeText", truncate(input.getResumeText(), 1000));
        variables.put("expectedPoints", formatBulletLines(input.getExpectedPoints()));
        variables.put("recentContext", formatRecentContext(input.getRecentContext()));
        variables.put("currentInterviewContext", stringifyAsJson(buildCurrentInterviewContext(input)));
        variables.put("pauseStats", formatPauseStats(input.getPauseStats()));
        variables.put("stateLedgerJson", stringifyAsJson(input.getStateLedger()));
        variables.put("syllabusJson", stringifyAsJson(input.getSyllabusJson()));
        variables.put("outputSchema", safeString(outputSchema));
        return variables;
    }

    private Map<String, Object> buildReportGenerationVariables(ReportGenerationInput input, String outputSchema) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("sessionTitle", safeString(input.getSessionTitle()));
        variables.put("qaPairs", formatReportQaPairs(input.getQuestionAnswerPairs()));
        variables.put("stateLedgerJson", stringifyAsJson(input.getStateLedgerJson()));
        variables.put("syllabusJson", stringifyAsJson(input.getSyllabusJson()));
        variables.put("outputSchema", safeString(outputSchema));
        return variables;
    }

    private Map<String, Object> buildQuestionDetailEvaluationVariables(
            QuestionDetailEvaluationInput input, String outputSchema) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("questionStem", safeString(input.getQuestionStem()));
        variables.put("questionType", safeString(input.getQuestionType()));
        variables.put("domainCode", safeString(input.getDomainCode()));
        variables.put("domainName", safeString(input.getDomainName()));
        variables.put("targetDepth", safeString(input.getTargetDepth()));
        variables.put("answerText", safeString(input.getAnswerText()));
        variables.put("expectedPoints", formatBulletLines(input.getExpectedPoints()));
        variables.put("recentContext", formatQuestionDetailRecentContext(input.getRecentContext()));
        variables.put("outputSchema", safeString(outputSchema));
        return variables;
    }

    private Map<String, Object> buildInterviewHardConstraints(EvaluationDecisionInput input) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("difficultyBand", List.of(normalizeDepth(safeString(input.getCurrentTargetDepth()))));
        constraints.put("remainingTurnBudget", extractLedgerScalar(input.getStateLedger(), "remaining_turn_budget"));
        constraints.put("requiredDomains", extractSyllabusDomainCodes(input.getSyllabusJson()));
        constraints.put("maxDomainRescueCount", 1);
        constraints.put("maxSessionRescueCount", 3);
        constraints.put("maxFocusFollowupStreak", 5);
        return constraints;
    }

    private Map<String, Object> buildCurrentInterviewContext(EvaluationDecisionInput input) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("activeProjectId", extractLedgerScalar(input.getStateLedger(), "active_project_id"));
        context.put("currentFocus", extractLedgerScalar(input.getStateLedger(), "current_focus"));
        context.put("coveredDomains", extractLedgerList(input.getStateLedger(), "covered_domains"));
        context.put("coveredPoints", extractLedgerList(input.getStateLedger(), "covered_points"));
        context.put("weakSignals", extractLedgerList(input.getStateLedger(), "weak_signals"));
        context.put("recentQuestionFamilies", extractLedgerList(input.getStateLedger(), "recent_question_families"));
        int sessionRescueCount = toInt(extractLedgerScalar(input.getStateLedger(), "rescue_total"));
        int currentFocusFollowupStreak = toInt(extractLedgerScalar(input.getStateLedger(), "current_focus_streak"));
        String currentDomainCode = safeString(input.getCurrentDomainCode());
        context.put("domainRescueUsed", domainRescueUsed(input.getStateLedger(), currentDomainCode));
        context.put("sessionRescueCount", sessionRescueCount);
        context.put("currentFocusFollowupStreak", currentFocusFollowupStreak);
        context.put("lastFocusPoint", extractLedgerScalar(input.getStateLedger(), "last_focus_point"));
        context.put("maxDomainRescueCount", 1);
        context.put("maxSessionRescueCount", 3);
        context.put("maxFocusFollowupStreak", 5);
        return context;
    }

    private Map<String, Object> buildQuestionRoleContext(QuestionGenerationInput input) {
        if (input.getRoleContext() != null) {
            return toMap(input.getRoleContext());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("roundType", input.getMode());
        context.put("candidateLevel", input.getExperienceLevel());
        context.put("difficultyBand", List.of(normalizeDepth(safeString(input.getTargetDepth()))));
        context.put("style", "natural_followup");
        return context;
    }

    private Map<String, Object> buildQuestionProjectContext(QuestionGenerationInput input) {
        if (input.getProjectContext() != null) {
            return toMap(input.getProjectContext());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("activeProjectId", null);
        context.put("projectName", null);
        context.put("currentFocus", safeString(input.getTargetSkill()));
        return context;
    }

    private Map<String, Object> buildQuestionRecentContext(QuestionGenerationInput input) {
        if (input.getRecentContext() != null) {
            return toMap(input.getRecentContext());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        String lastQuestion = null;
        if (input.getAskedQuestions() != null && !input.getAskedQuestions().isEmpty()) {
            QuestionGenerationInput.AskedQuestion last = input.getAskedQuestions().getLast();
            lastQuestion = safeString(last.getStem());
        }
        context.put("lastQuestion", lastQuestion);
        context.put("lastAnswerSummary", null);
        context.put("recentTurnsSummary", null);
        context.put("lastAnswerHighlights", List.of());
        return context;
    }

    private Map<String, Object> buildQuestionGoalContext(QuestionGenerationInput input) {
        if (input.getNextQuestionGoal() != null) {
            return toMap(input.getNextQuestionGoal());
        }
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("decision", "broaden");
        goal.put("targetFocus", safeString(input.getTargetSkill()));
        goal.put("targetAngle", "implementation");
        goal.put("difficultyAdjustment", "same");
        goal.put("questionType", safeString(input.getNextQuestionType()));
        goal.put("focusPoint", safeString(input.getTargetSkill()));
        goal.put("nextQuestionGoal", safeString(input.getTargetSkill()));
        goal.put("nextDomainId", input.getNextDomainId());
        goal.put("nextDomainCode", safeString(input.getNextDomainCode()));
        goal.put("nextDomainName", safeString(input.getNextDomainName()));
        return goal;
    }

    private Map<String, Object> buildQuestionRetrievalContext(QuestionGenerationInput input) {
        if (input.getRetrievalContext() != null) {
            return toMap(input.getRetrievalContext());
        }
        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("query", safeString(input.getTargetSkill()));
        retrieval.put("ragContext", safeString(input.getRagContext()));
        retrieval.put("domainHint", safeString(input.getNextDomainCode()));
        retrieval.put("questionTypeHint", safeString(input.getNextQuestionType()));
        retrieval.put("avoidRecentFamilies", List.of());
        return retrieval;
    }

    private Map<String, Object> buildQuestionConstraints(QuestionGenerationInput input) {
        if (input.getConstraints() != null) {
            return toMap(input.getConstraints());
        }
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("avoidRepetitionFamilies", List.of());
        constraints.put("mustSoundNatural", true);
        constraints.put("maxSentences", 2);
        return constraints;
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
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("resumeTextSummary", truncate(input.getResumeTextSummary(), 500));
        variables.put("roleContext", stringifyAsJson(buildQuestionRoleContext(input)));
        variables.put("projectContext", stringifyAsJson(buildQuestionProjectContext(input)));
        variables.put("recentContext", stringifyAsJson(buildQuestionRecentContext(input)));
        variables.put("nextQuestionGoal", stringifyAsJson(buildQuestionGoalContext(input)));
        variables.put("retrievalContext", stringifyAsJson(buildQuestionRetrievalContext(input)));
        variables.put("constraints", stringifyAsJson(buildQuestionConstraints(input)));
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
            String stem = question != null ? safeString(question.getStem()) : "";
            if (stem.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- ").append(stem);
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    private String formatRecentContext(List<EvaluationDecisionInput.QaContext> recentContext) {
        if (recentContext == null || recentContext.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (EvaluationDecisionInput.QaContext ctx : recentContext) {
            if (ctx == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- [")
                    .append(safeString(ctx.getQuestionType()))
                    .append("/")
                    .append(safeString(ctx.getDomainCode()))
                    .append("] Q: ")
                    .append(safeString(ctx.getStem()))
                    .append(" | A: ")
                    .append(safeString(ctx.getAnswer()));
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    private String formatQuestionDetailRecentContext(List<QuestionDetailEvaluationInput.QaContext> recentContext) {
        if (recentContext == null || recentContext.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (QuestionDetailEvaluationInput.QaContext ctx : recentContext) {
            if (ctx == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- [")
                    .append(safeString(ctx.getQuestionType()))
                    .append("/")
                    .append(safeString(ctx.getDomainCode()))
                    .append("] Q: ")
                    .append(safeString(ctx.getStem()))
                    .append(" | A: ")
                    .append(safeString(ctx.getAnswer()));
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    private String formatPauseStats(Map<String, Object> pauseStats) {
        if (pauseStats == null || pauseStats.isEmpty()) {
            return "无";
        }
        Object wpm = pauseStats.get("wpm");
        Object longPauseCount = pauseStats.get("longPauseCount");
        Object longestPauseMs = pauseStats.get("longestPauseMs");
        StringBuilder sb = new StringBuilder();
        sb.append("wpm=").append(wpm != null ? wpm : "N/A");
        sb.append(", longPauseCount=").append(longPauseCount != null ? longPauseCount : "N/A");
        sb.append(", longestPauseMs=").append(longestPauseMs != null ? longestPauseMs : "N/A");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private boolean domainRescueUsed(Map<String, Object> ledger, String domainCode) {
        if (ledger == null || domainCode == null || domainCode.isBlank()) {
            return false;
        }
        Object raw = ledger.get("rescue_counts_by_domain");
        if (!(raw instanceof Map<?, ?> rescueCounts)) {
            return false;
        }
        Object value = rescueCounts.get(domainCode);
        return toInt(value) >= 1;
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

    private String formatReportQaPairs(List<ReportGenerationInput.QuestionAnswerPair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (ReportGenerationInput.QuestionAnswerPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("题型: ").append(safeString(pair.getQuestionType())).append("\n");
            sb.append("知识域: ").append(safeString(pair.getDomainCode())).append("/")
                    .append(safeString(pair.getDomainName())).append("\n");
            sb.append("题目: ").append(safeString(pair.getStem())).append("\n");
            sb.append("回答: ").append(safeString(pair.getAnswerText())).append("\n");
            sb.append("目标深度: ").append(safeString(pair.getTargetDepth())).append("\n");
            sb.append("参考要点:\n").append(formatBulletLines(pair.getExpectedPoints()));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(objectMapper.convertValue(value, Map.class));
    }

    private Object extractLedgerScalar(Map<String, Object> ledger, String key) {
        if (ledger == null || key == null || key.isBlank()) {
            return null;
        }
        return ledger.get(key);
    }

    private List<String> extractLedgerList(Map<String, Object> ledger, String key) {
        if (ledger == null || key == null || key.isBlank()) {
            return List.of();
        }
        Object value = ledger.get(key);
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>();
        for (Object item : rawList) {
            String text = safeString(item == null ? null : String.valueOf(item)).trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<String> extractSyllabusDomainCodes(Map<String, Object> syllabus) {
        if (syllabus == null) {
            return List.of();
        }
        Object domainsObj = syllabus.get("domains");
        if (!(domainsObj instanceof List<?> domains)) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>();
        for (Object domainObj : domains) {
            if (!(domainObj instanceof Map<?, ?> domain)) {
                continue;
            }
            String code = safeString(domain.get("domainCode") == null ? null : String.valueOf(domain.get("domainCode"))).trim();
            if (!code.isBlank()) {
                result.add(code);
            }
        }
        return result;
    }

    private String normalizeDepth(String depth) {
        if (depth == null || depth.isBlank()) {
            return "L2";
        }
        String normalized = depth.trim().toUpperCase();
        return normalized.matches("L[1-5]") ? normalized : "L2";
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
