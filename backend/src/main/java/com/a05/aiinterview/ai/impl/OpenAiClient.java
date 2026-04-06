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
 * <p>核心功能：
 * <ul>
 *   <li>基于 Spring AI ChatClient 实现，支持 OpenAI 兼容协议（阿里云百炼、DeepSeek等）</li>
 *   <li>使用 BeanOutputConverter 将结构化 JSON Schema 注入 Prompt</li>
 *   <li>自动解析 AI 返回的 JSON 为 Java 对象，避免手工正则解析</li>
 *   <li>自动获取 Token 消耗统计（promptTokens、responseTokens）</li>
 *   <li>支持同步和流式两种调用模式</li>
 *   <li>完整的审计日志记录（ai_lite_audit）</li>
 *   <li>提示词版本控制集成</li>
 * </ul>
 *
 * <p>支持的 AI 接口：
 * <ul>
 *   <li>planner：考纲规划</li>
 *   <li>question_generation_stream：流式出题</li>
 *   <li>evaluation_decision：评估决策</li>
 *   <li>report_generation：报告生成</li>
 *   <li>intro_rewrite：首题改写</li>
 *   <li>question_detail_evaluation：单题详细评估</li>
 *   <li>question_consult：单题追问</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.openai.mock-enabled", havingValue = "false")
public class OpenAiClient implements AiClient {

    /** AI 响应预览在日志中的最大长度（字节） */
    private static final int RESPONSE_PREVIEW_MAX_LEN = 120;

    /** Spring AI ChatClient，用于构建和发送 AI 请求 */
    private final ChatClient chatClient;

    /** 提示词模板服务，用于渲染提示词 */
    private final PromptTemplateService promptTemplateService;

    /** 提示词属性配置，用于版本控制 */
    private final PromptProperties promptProperties;

    /** Jackson ObjectMapper，用于 JSON 序列化和反序列化 */
    private final ObjectMapper objectMapper;

    /** AI 输出契约验证器，用于验证输出格式 */
    private final AiOutputContractValidator aiOutputContractValidator;

    /** 配置的模型名称，从配置文件读取 */
    @Value("${ai.openai.model:${OPENAI_MODEL:gpt-4o-mini}}")
    private String configuredModel = "unknown";

    /**
     * OpenAiClient 构造函数。
     *
     * @param chatModel Spring AI ChatModel 实例
     * @param promptTemplateService 提示词模板服务
     * @param promptProperties 提示词属性配置
     * @param objectMapper Jackson ObjectMapper
     * @param aiOutputContractValidator AI 输出契约验证器
     */
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

    /**
     * 调用考纲规划 AI 接口。
     *
     * <p>功能：根据岗位、经验、简历等信息生成面试考纲（知识域列表）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>创建 PlannerOutput 的 BeanOutputConverter，生成 JSON Schema</li>
     *   <li>渲染提示词（包含岗位、经验、简历等变量）</li>
     *   <li>在用户提示词后追加 JSON Schema，指导 AI 返回结构化输出</li>
     *   <li>调用 AI 同步接口</li>
     *   <li>使用 BeanOutputConverter 自动解析 JSON 为 PlannerOutput 对象</li>
     *   <li>记录审计日志</li>
     *   <li>返回包含输出、Token 统计、延迟的 AiCallResult</li>
     * </ol>
     *
     * @param input 考纲规划输入，包含岗位、经验、简历等
     * @return AI 调用结果，包含考纲输出、Token 统计、延迟
     */
    @Override
    public AiCallResult<PlannerOutput> callPlanner(PlannerInput input) {
        log.info("调用 OpenAI Planner, positionCode={}, experienceLevel={}",
                input.getPositionCode(), input.getExperienceLevel());

        // 步骤1：创建 BeanOutputConverter，生成 PlannerOutput 的 JSON Schema
        BeanOutputConverter<PlannerOutput> converter = new BeanOutputConverter<>(PlannerOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            // 步骤2：渲染提示词（包含变量替换）
            rendered = renderPrompt(PROMPT_CODE_PLANNER, buildPlannerVariables(input));
            // 步骤3：在用户提示词后追加 JSON Schema，指导 AI 返回结构化输出
            String userPrompt = rendered.getUserPrompt() + "\n\n" + converter.getFormat();

            // 步骤4：调用 AI 同步接口
            ChatResponse response = callChat(rendered.getSystemPrompt(), userPrompt);

            // 步骤5：使用 BeanOutputConverter 自动解析 JSON 为 PlannerOutput 对象
            PlannerOutput output = requireConvert(converter, response, "planner");
            long latencyMs = System.currentTimeMillis() - startMs;

            // 步骤6：记录审计日志
            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    null,
                    null,
                    latencyMs,
                    "success",
                    null,
                    response.getResult().getOutput().getText(),
                    null,
                    null
            );
            log.info("Planner 调用成功，规划知识域数={}",
                    output.getDomains() != null ? output.getDomains().size() : 0);
            // 步骤7：返回结果
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            // 异常时也记录审计日志
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_PLANNER),
                    resolvePromptVersion(rendered, PROMPT_CODE_PLANNER),
                    input.getInterviewId(),
                    null,
                    null,
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

    /**
     * 调用流式出题 AI 接口（使用 Reactor Flux）。
     *
     * <p>功能：根据决策结果流式生成下一道题目。
     *
     * <p>特点：
     * <ul>
     *   <li>使用 Spring AI 的流式 API，实时推送 token</li>
     *   <li>使用 Flux 响应式编程，非阻塞 I/O</li>
     *   <li>实时累计响应长度，用于审计</li>
     *   <li>在完成和出错时自动记录审计日志</li>
     * </ul>
     *
     * <p>处理流程：
     * <ol>
     *   <li>渲染提示词</li>
     *   <li>调用 ChatClient 的 stream() 方法</li>
     *   <li>通过 content() 提取纯文本 token</li>
     *   <li>在 doOnNext 中累计响应长度</li>
     *   <li>在 doOnComplete 中记录成功审计日志</li>
     *   <li>在 doOnError 中记录错误审计日志</li>
     * </ol>
     *
     * @param input 流式出题输入，包含决策结果、上下文等
     * @return Flux<String> 文本 token 流
     */
    @Override
    public Flux<String> callQuestionGenerationStream(QuestionGenerationInput input) {
        String relatedDomainCode = input.getNextQuestionGoal() != null
                ? input.getNextQuestionGoal().getRelatedDomainCode()
                : null;
        log.info("调用 OpenAI 出题（流式）, domainCode={}", relatedDomainCode);
        RenderedPrompt rendered = renderPrompt(PROMPT_CODE_QUESTION_GENERATION_STREAM,
                buildQuestionGenerationStreamVariables(input));
        long startMs = System.currentTimeMillis();
        AtomicInteger responseLength = new AtomicInteger(0);
        return chatClient.prompt()
                .system(rendered.getSystemPrompt())
                .user(rendered.getUserPrompt())
                .stream()
                .content()
                .doOnNext(token -> {// 累计响应长度
                    if (token != null) {
                        responseLength.addAndGet(token.length());
                    }
                })
                .doOnComplete(() -> auditLite(// 记录成功审计日志
                        rendered.getPromptCode(),
                        rendered.getPromptVersion(),
                        input.getInterviewId(),
                        input.getQuestionId(),
                        null,
                        System.currentTimeMillis() - startMs,
                        "success",
                        null,
                        null,
                        responseLength.get(),
                        null
                ))
                .doOnError(error -> auditLite(// 记录错误审计日志
                        rendered.getPromptCode(),
                        rendered.getPromptVersion(),
                        input.getInterviewId(),
                        input.getQuestionId(),
                        null,
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

    /**
     * 调用评估决策 AI 接口。
     *
     * <p>功能：评估用户回答质量，决定下一步策略（继续出题/结束/追问/换题）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>创建 EvaluationDecisionOutput 的 BeanOutputConverter</li>
     *   <li>渲染提示词（包含历史回答、配额状态、可用策略等）</li>
     *   <li>调用 AI 同步接口</li>
     *   <li>解析 JSON 为 EvaluationDecisionOutput 对象</li>
     *   <li>使用 AiOutputContractValidator 验证输出格式</li>
     *   <li>记录审计日志</li>
     *   <li>返回结果</li>
     * </ol>
     *
     * <p>输出内容：
     * <ul>
     *   <li>interviewAction：CONTINUE 或 WRAPUP</li>
     *   <li>finalDecision：策略编码（S_P_*、S_PRJ_*、S_WRAPUP）</li>
     *   <li>nextFocus：下一题焦点</li>
     *   <li>newCoveredPoints/Domains：新覆盖的知识点/知识域</li>
     * </ul>
     *
     * @param input 评估决策输入，包含当前问题、用户回答、历史、配额等
     * @return AI 调用结果，包含评估决策、Token 统计、延迟
     */
    @Override
    public AiCallResult<EvaluationDecisionOutput> callEvaluationDecision(EvaluationDecisionInput input) {
        log.info("调用 OpenAI 评估决策, questionId={}", input.getCurrentQuestionId());

        // 步骤1：创建 BeanOutputConverter，用于结构化输出
        BeanOutputConverter<EvaluationDecisionOutput> converter =
                new BeanOutputConverter<>(EvaluationDecisionOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            // 步骤2：渲染提示词（包含 JSON Schema）
            rendered = renderPrompt(PROMPT_CODE_EVALUATION_DECISION,
                    buildEvaluationDecisionVariables(input, converter.getFormat()));

            // 步骤3：调用 AI 同步接口
            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());

            // 步骤4：解析 JSON 并使用契约验证器验证
            EvaluationDecisionOutput output = aiOutputContractValidator.validateEvaluationDecision(
                    requireConvert(converter, response, "evaluation_decision")
            );
            long latencyMs = System.currentTimeMillis() - startMs;

            // 步骤5：记录审计日志
            auditLite(
                    rendered.getPromptCode(),
                    rendered.getPromptVersion(),
                    input.getInterviewId(),
                    input.getCurrentQuestionId(),
                    null,
                    latencyMs,
                    "success",
                    null,
                    response.getResult().getOutput().getText(),
                    null,
                    null
            );
            log.info("评估决策调用成功，interviewAction={}", output.getInterviewAction());

            // 步骤6：返回结果
            return buildResult(output, response, latencyMs, rendered);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            auditLite(
                    resolvePromptCode(rendered, PROMPT_CODE_EVALUATION_DECISION),
                    resolvePromptVersion(rendered, PROMPT_CODE_EVALUATION_DECISION),
                    input.getInterviewId(),
                    input.getCurrentQuestionId(),
                    null,
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

    /**
     * 调用报告生成 AI 接口。
     *
     * <p>功能：根据完整的面试问答对生成面试总结报告。
     *
     * <p>处理流程：
     * <ol>
     *   <li>创建 ReportGenerationOutput 的 BeanOutputConverter</li>
     *   <li>渲染提示词（包含所有问答对、考纲、状态账本等）</li>
     *   <li>调用 AI 同步接口</li>
     *   <li>解析 JSON 为 ReportGenerationOutput 对象</li>
     *   <li>使用 AiOutputContractValidator 验证输出格式</li>
     *   <li>记录审计日志</li>
     *   <li>返回结果</li>
     * </ol>
     *
     * <p>输出内容：
     * <ul>
     *   <li>overallScore：总体评分</li>
     *   <li>domainScores：各知识域评分</li>
     *   <li>strengths/weaknesses：优势/劣势</li>
     *   <li>summary：总结</li>
     *   <li>learningRecommendations：学习建议</li>
     * </ul>
     *
     * @param input 报告生成输入，包含问答对、考纲、状态账本等
     * @return AI 调用结果，包含报告、Token 统计、延迟
     */
    @Override
    public AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input) {
        log.info("调用 OpenAI 报告生成, positionCode={}, qaPairsCount={}",
                input.getPositionCode(),
                input.getQuestionAnswerPairs() != null ? input.getQuestionAnswerPairs().size() : 0);

        // 步骤1：创建 BeanOutputConverter，用于结构化输出
        BeanOutputConverter<ReportGenerationOutput> converter =
                new BeanOutputConverter<>(ReportGenerationOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            // 步骤2：渲染提示词（包含 JSON Schema）
            rendered = renderPrompt(PROMPT_CODE_REPORT_GENERATION,
                    buildReportGenerationVariables(input, converter.getFormat()));

            // 步骤3：调用 AI 同步接口
            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());

            // 步骤4：解析 JSON 并使用契约验证器验证
            ReportGenerationOutput output = aiOutputContractValidator.validateReport(
                    requireConvert(converter, response, "report_generation")
            );
            long latencyMs = System.currentTimeMillis() - startMs;

            // 步骤5：记录审计日志
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

            // 步骤6：返回结果
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

    /**
     * 将 AI 输出的 QuestionDetailEvaluationAiOutput 映射为最终的 QuestionDetailEvaluationOutput。
     * 注意：此方法只做简单的字段拷贝，不计算 start/end 定位信息，
     * start/end 由后续的 HighlightedAnnotationLocator 负责计算。
     *
     * @param aiOutput AI 输出的 DTO（不含 start/end）
     * @return 最终的 DTO（highlightedAnnotations 的 start/end 暂时为 null）
     */
    private QuestionDetailEvaluationOutput mapAiOutputToFinalOutput(QuestionDetailEvaluationAiOutput aiOutput) {
        if (aiOutput == null) {
            return null;
        }

        List<QuestionDetailEvaluationOutput.EvaluatedDomain> evaluatedDomains = null;
        if (aiOutput.getEvaluatedDomains() != null) {
            evaluatedDomains = aiOutput.getEvaluatedDomains().stream()
                    .map(domain -> QuestionDetailEvaluationOutput.EvaluatedDomain.builder()
                            .domainCode(domain.getDomainCode())
                            .domainName(domain.getDomainName())
                            .score(domain.getScore())
                            .commentary(domain.getCommentary())
                            .build())
                    .toList();
        }

        List<QuestionDetailEvaluationOutput.HighlightedSegment> highlightedSegments = null;
        if (aiOutput.getHighlightedSegments() != null) {
            highlightedSegments = aiOutput.getHighlightedSegments().stream()
                    .map(seg -> QuestionDetailEvaluationOutput.HighlightedSegment.builder()
                            .segment(seg.getSegment())
                            .label(seg.getLabel())
                            .comment(seg.getComment())
                            .build())
                    .toList();
        }

        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> highlightedAnnotations = null;
        if (aiOutput.getHighlightedAnnotations() != null) {
            highlightedAnnotations = aiOutput.getHighlightedAnnotations().stream()
                    .map(candidate -> QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                            .quote(candidate.getQuote())
                            .label(candidate.getLabel())
                            .comment(candidate.getComment())
                            .build())
                    .toList();
        }

        return QuestionDetailEvaluationOutput.builder()
                .score(aiOutput.getScore())
                .commentary(aiOutput.getCommentary())
                .strengthPoints(aiOutput.getStrengthPoints())
                .weakPoints(aiOutput.getWeakPoints())
                .evaluatedDomains(evaluatedDomains)
                .highlightedSegments(highlightedSegments)
                .highlightedAnnotations(highlightedAnnotations)
                .idealAnswerOutline(aiOutput.getIdealAnswerOutline())
                .rewrittenAnswer(aiOutput.getRewrittenAnswer())
                .build();
    }

    @Override
    public AiCallResult<QuestionDetailEvaluationOutput> callQuestionDetailEvaluation(QuestionDetailEvaluationInput input) {
        log.info("调用 OpenAI 单题详细评估, questionId={}, domainCode={}",
                input.getQuestionId(), input.getDomainCode());

        // 使用新的 AI 专用 DTO（不含 start/end）
        BeanOutputConverter<QuestionDetailEvaluationAiOutput> converter =
                new BeanOutputConverter<>(QuestionDetailEvaluationAiOutput.class);
        RenderedPrompt rendered = null;
        long startMs = System.currentTimeMillis();
        try {
            rendered = renderPrompt(PROMPT_CODE_QUESTION_DETAIL_EVALUATION,
                    buildQuestionDetailEvaluationVariables(input, converter.getFormat()));

            ChatResponse response = callChat(rendered.getSystemPrompt(), rendered.getUserPrompt());
            QuestionDetailEvaluationAiOutput aiOutput =
                    requireConvert(converter, response, "question_detail_evaluation");
            
            // 将 AI 输出映射为最终的 DTO（start/end 暂时为 null，由后续定位器计算）
            QuestionDetailEvaluationOutput output = mapAiOutputToFinalOutput(aiOutput);
            
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

    @Override
    public Flux<String> callQuestionConsultStream(QuestionConsultInput input) {
        log.info("调用 OpenAI 单题追问, questionId={}, assistantMessageId={}",
                input.getQuestionId(), input.getAssistantMessageId());
        RenderedPrompt rendered = renderPrompt(PROMPT_CODE_QUESTION_CONSULT,
                buildQuestionConsultVariables(input));
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
                        input.getAssistantMessageId() == null ? null : String.valueOf(input.getAssistantMessageId()),
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
                        input.getAssistantMessageId() == null ? null : String.valueOf(input.getAssistantMessageId()),
                        System.currentTimeMillis() - startMs,
                        "error",
                        null,
                        null,
                        responseLength.get(),
                        asException(error)
                ));
    }

    // ──────────────────────────── 公共工具 ──────────────────────────────────

    /**
     * 发起同步 Chat 调用，返回 ChatResponse；response 为 null 时抛出异常。
     *
     * <p>这是所有同步 AI 调用的基础方法。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return ChatResponse 响应对象
     * @throws RuntimeException 如果响应为 null
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
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查 AI 返回的文本是否为空</li>
     *   <li>使用 BeanOutputConverter 转换为目标类型</li>
     *   <li>如果转换失败，尝试提取字段级错误信息</li>
     *   <li>记录错误日志并抛出异常</li>
     * </ol>
     *
     * @param converter BeanOutputConverter 实例
     * @param response ChatResponse 响应对象
     * @param promptCode 提示词编码，用于日志
     * @return 转换后的 DTO 对象
     * @throws RuntimeException 如果转换失败
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
            // 尝试提取字段级错误信息，便于快速定位问题
            String fieldHint = extractFieldErrorHint(e.getMessage());
            log.error("{} AI 响应解析失败, field={}, reason={}, rawText={}",
                    promptCode, fieldHint, e.getMessage(), text);
            throw new RuntimeException(promptCode + " AI 响应解析失败: field=" + fieldHint + ", reason=" + e.getMessage(), e);
        }
    }

    /**
     * 从异常信息中提取字段级错误提示。
     *
     * <p>错误模式识别：
     * <ul>
     *   <li>Jackson reference chain：如 "through reference chain: ...["patch"]["evidenceQuestionId"]"</li>
     *   <li>UUID 未加引号：如 "Unexpected character ... expecting comma"</li>
     * </ul>
     *
     * @param errorMessage 异常信息
     * @return 字段级错误提示
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
     *
     * <p>Token 计算逻辑：
     * <ul>
     *   <li>promptTokens：直接从 usage.promptTokens 获取</li>
     *   <li>responseTokens：用 totalTokens - promptTokens 计算，兼容不同版本 Spring AI</li>
     * </ul>
     *
     * @param output AI 输出对象
     * @param response ChatResponse 响应对象
     * @param latencyMs 延迟（毫秒）
     * @param renderedPrompt 渲染后的提示词
     * @return 包装后的 AiCallResult
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

    /**
     * 记录轻量级审计日志（ai_lite_audit）。
     *
     * <p>审计字段说明：
     * <ul>
     *   <li>traceId/requestId：请求链路追踪</li>
     *   <li>interviewId/questionId/variantId：业务标识</li>
     *   <li>promptCode/promptVersion/model：AI调用元数据</li>
     *   <li>latencyMs/status/fallbackReason：调用状态</li>
     *   <li>responsePreview/responseLength：响应预览</li>
     *   <li>errorType/errorMessage：错误信息</li>
     * </ul>
     *
     * @param promptCode 提示词编码
     * @param promptVersion 提示词版本
     * @param interviewId 会话ID
     * @param questionId 题目ID
     * @param variantId 变体ID
     * @param latencyMs 延迟（毫秒）
     * @param status 状态（success/error）
     * @param fallbackReason 兜底原因
     * @param responseText 响应文本（同步模式）
     * @param responseLength 响应长度（流式模式）
     * @param error 异常对象（错误模式）
     */
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

    /**
     * 安全地向 ObjectNode 放入 Long 字段，null 值放入 null。
     *
     * @param audit ObjectNode
     * @param fieldName 字段名
     * @param value Long 值
     */
    private void putNullableLong(ObjectNode audit, String fieldName, Long value) {
        if (value == null) {
            audit.putNull(fieldName);
            return;
        }
        audit.put(fieldName, value);
    }

    /**
     * 安全地解析提示词编码，失败时返回默认值。
     *
     * @param rendered 渲染后的提示词
     * @param defaultCode 默认编码
     * @return 提示词编码
     */
    private String resolvePromptCode(RenderedPrompt rendered, String defaultCode) {
        if (rendered == null || rendered.getPromptCode() == null || rendered.getPromptCode().isBlank()) {
            return defaultCode;
        }
        return rendered.getPromptCode();
    }

    /**
     * 安全地解析提示词版本，失败时从配置中获取。
     *
     * @param rendered 渲染后的提示词
     * @param promptCode 提示词编码
     * @return 提示词版本
     */
    private String resolvePromptVersion(RenderedPrompt rendered, String promptCode) {
        if (rendered == null || rendered.getPromptVersion() == null || rendered.getPromptVersion().isBlank()) {
            return promptProperties.resolveVersion(promptCode);
        }
        return rendered.getPromptVersion();
    }

    /**
     * 解析模型名称，失败时返回 "unknown"。
     *
     * @return 模型名称
     */
    private String resolveModelName() {
        if (configuredModel == null || configuredModel.isBlank()) {
            return "unknown";
        }
        return configuredModel;
    }

    /**
     * 将 Throwable 转换为 Exception（如果是 Error 则包装为 RuntimeException）。
     *
     * @param throwable Throwable 对象
     * @return Exception 对象
     */
    private Exception asException(Throwable throwable) {
        if (throwable instanceof Exception e) {
            return e;
        }
        return new RuntimeException(throwable);
    }

    // ──────────────────────────── Prompt 构造 ────────────────────────────────

    /** 提示词编码常量 - 考纲规划 */
    private static final String PROMPT_CODE_PLANNER = "planner";
    /** 提示词编码常量 - 评估决策 */
    private static final String PROMPT_CODE_EVALUATION_DECISION = "evaluation_decision";
    /** 提示词编码常量 - 报告生成 */
    private static final String PROMPT_CODE_REPORT_GENERATION = "report_generation";
    /** 提示词编码常量 - 流式出题 */
    private static final String PROMPT_CODE_QUESTION_GENERATION_STREAM = "question_generation_stream";
    /** 提示词编码常量 - 首题改写 */
    private static final String PROMPT_CODE_INTRO_REWRITE = "intro_rewrite";
    /** 提示词编码常量 - 单题详细评估 */
    private static final String PROMPT_CODE_QUESTION_DETAIL_EVALUATION = "question_detail_evaluation";
    /** 提示词编码常量 - 单题追问 */
    private static final String PROMPT_CODE_QUESTION_CONSULT = "question_consult";

    /**
     * 构建 Planner（考纲规划）提示词变量。
     *
     * <p>输入变量：岗位、经验、JD、简历、历史面试等。
     *
     * @param input Planner 输入
     * @return 变量 Map
     */
    private Map<String, Object> buildPlannerVariables(PlannerInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("position", safeString(input.getPositionName()));
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("roundType", safeString(input.getRoundType()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("jd", truncate(input.getJobDescription(), 500));
        variables.put("resumeText", truncate(input.getResumeText(), 1000));
        variables.put("focusTopics", safeString(input.getFocusTopics()));
        variables.put("domains", formatDomains(input.getDomains()));
        variables.put("historyInterviews", stringifyAsJson(input.getHistoryInterviews()));
        return variables;
    }

    /**
     * 构建 IntroRewrite（首题改写）提示词变量。
     *
     * <p>输入变量：候选人上下文、面试官人设、基础提示词、最近提示词、避免使用的短语等。
     *
     * @param input 首题改写输入
     * @return 变量 Map
     */
    private Map<String, Object> buildIntroRewriteVariables(IntroRewriteInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("candidateContext", buildCandidateContext(input));
        variables.put("interviewerArchetype", safeString(input.getInterviewerArchetype()));
        variables.put("basePrompt", safeString(input.getBasePrompt()));
        variables.put("recentPrompts", formatBulletLines(input.getRecentPrompts()));
        variables.put("avoidPhrases", formatBulletLines(input.getAvoidPhrases()));
        return variables;
    }

    /**
     * 构建 EvaluationDecision（评估决策）提示词变量。
     *
     * <p>输入变量：岗位、经验、题次、配额快照、剩余知识域、已覆盖知识点、可用策略、当前问题、用户回答等。
     *
     * @param input 评估决策输入
     * @param outputSchema 输出 JSON Schema（由 BeanOutputConverter 生成）
     * @return 变量 Map
     */
    private Map<String, Object> buildEvaluationDecisionVariables(EvaluationDecisionInput input, String outputSchema) {
        Map<String, Object> variables = new LinkedHashMap<>();
        EvaluationDecisionInput.InterviewMeta interview = input.getInterview();
        variables.put("positionCode", safeString(interview != null ? interview.getPositionCode() : null));
        variables.put("experienceLevel", safeString(interview != null ? interview.getExperienceLevel() : null));
        variables.put("roundType", safeString(interview != null ? interview.getRoundType() : null));
        variables.put("questionIndex", input.getQuestionIndex() == null ? "" : String.valueOf(input.getQuestionIndex()));
        variables.put("maxQuestions", input.getMaxQuestions() == null ? "" : String.valueOf(input.getMaxQuestions()));
        variables.put("quotaSnapshot", stringifyAsJson(input.getQuotaSnapshot()));
        variables.put("projectAndInternshipSummary", stringifyAsJson(input.getProjectAndInternshipSummary()));
        variables.put("remainingTargetDomains", formatRemainingTargetDomains(input.getRemainingTargetDomains()));
        variables.put("coveredKnowledgeSummary", stringifyAsJson(input.getCoveredKnowledgeSummary()));
        variables.put("crossSessionBlockedKnowledgePoints", stringifyAsJson(input.getCrossSessionBlockedKnowledgePoints()));
        variables.put("availableStrategies", formatAvailableStrategies(input.getAvailableStrategies()));
        variables.put("currentQuestion", stringifyAsJson(input.getCurrentQuestion()));
        variables.put("answerText", safeString(input.getAnswerText()));
        variables.put("expectedPoints", stringifyAsJson(input.getExpectedPoints()));
        variables.put("retrievedMaterials", stringifyAsJson(input.getRetrievedMaterials()));
        variables.put("recentInterviewMemory", stringifyAsJson(input.getRecentInterviewMemory()));
        variables.put("repairMode", Boolean.TRUE.equals(input.getRepairMode()) ? "true" : "false");
        variables.put("repairAttemptNo", input.getRepairAttemptNo() == null ? "" : String.valueOf(input.getRepairAttemptNo()));
        variables.put("rawDecisionOutput", safeString(input.getRawDecisionOutput()));
        variables.put("validationErrors", stringifyAsJson(input.getValidationErrors()));
        variables.put("outputSchema", safeString(outputSchema));
        return variables;
    }

    /**
     * 构建 ReportGeneration（报告生成）提示词变量。
     *
     * <p>输入变量：岗位、经验、会话标题、问答对、状态账本、考纲等。
     *
     * @param input 报告生成输入
     * @param outputSchema 输出 JSON Schema（由 BeanOutputConverter 生成）
     * @return 变量 Map
     */
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

    /**
     * 构建 QuestionDetailEvaluation（单题详细评估）提示词变量。
     *
     * <p>输入变量：岗位、经验、题目、题型、知识域、用户回答、预期要点、最近上下文等。
     *
     * @param input 单题详细评估输入
     * @param outputSchema 输出 JSON Schema（由 BeanOutputConverter 生成）
     * @return 变量 Map
     */
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
        variables.put("answerText", safeString(input.getAnswerText()));
        variables.put("expectedPoints", formatBulletLines(input.getExpectedPoints()));
        variables.put("recentContext", formatQuestionDetailRecentContext(input.getRecentContext()));
        variables.put("outputSchema", safeString(outputSchema));
        return variables;
    }

    /**
     * 构建 QuestionConsult（单题追问）提示词变量。
     *
     * <p>输入变量：岗位、经验、题目、原回答、评分、点评、优缺点、理想答案、追问历史、用户最新问题等。
     *
     * @param input 单题追问输入
     * @return 变量 Map
     */
    private Map<String, Object> buildQuestionConsultVariables(QuestionConsultInput input) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("positionCode", safeString(input.getPositionCode()));
        variables.put("experienceLevel", safeString(input.getExperienceLevel()));
        variables.put("mode", safeString(input.getMode()));
        variables.put("questionStem", safeString(input.getQuestionStem()));
        variables.put("questionType", safeString(input.getQuestionType()));
        variables.put("domainCode", safeString(input.getDomainCode()));
        variables.put("domainName", safeString(input.getDomainName()));
        variables.put("originalAnswerText", safeString(input.getOriginalAnswerText()));
        variables.put("evaluationScore", input.getEvaluationScore() == null ? "" : String.valueOf(input.getEvaluationScore()));
        variables.put("evaluationCommentary", safeString(input.getEvaluationCommentary()));
        variables.put("strengthPoints", formatBulletLines(input.getStrengthPoints()));
        variables.put("weakPoints", formatBulletLines(input.getWeakPoints()));
        variables.put("idealAnswerOutline", formatBulletLines(input.getIdealAnswerOutline()));
        variables.put("rewrittenAnswer", safeString(input.getRewrittenAnswer()));
        variables.put("consultHistory", formatConsultHistory(input.getConsultHistory()));
        variables.put("latestUserQuestion", safeString(input.getLatestUserQuestion()));
        return variables;
    }

    /**
     * 构建出题时的角色上下文（如果输入中没有则返回默认值）。
     *
     * @param input 流式出题输入
     * @return 角色上下文 Map
     */
    private Map<String, Object> buildQuestionRoleContext(QuestionGenerationInput input) {
        if (input.getRoleContext() != null) {
            return toMap(input.getRoleContext());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("roundType", "");
        context.put("style", "efficiency");
        return context;
    }

    /**
     * 构建出题时的项目上下文（如果输入中没有则返回默认值）。
     *
     * @param input 流式出题输入
     * @return 项目上下文 Map
     */
    private Map<String, Object> buildQuestionProjectContext(QuestionGenerationInput input) {
        if (input.getProjectContext() != null) {
            return toMap(input.getProjectContext());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("activeItemKey", null);
        context.put("itemType", null);
        context.put("itemName", null);
        context.put("currentFocus", null);
        return context;
    }

    /**
     * 构建出题时的最近上下文（如果输入中没有则返回默认值）。
     *
     * <p>默认情况下，从已出题列表中提取最后一题作为 lastQuestion。
     *
     * @param input 流式出题输入
     * @return 最近上下文 Map
     */
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

    /**
     * 构建出题时的目标上下文（如果输入中没有则返回默认值）。
     *
     * @param input 流式出题输入
     * @return 目标上下文 Map
     */
    private Map<String, Object> buildQuestionGoalContext(QuestionGenerationInput input) {
        if (input.getNextQuestionGoal() != null) {
            return toMap(input.getNextQuestionGoal());
        }
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("questionType", "");
        goal.put("nextFocus", "");
        goal.put("goalSummary", "");
        goal.put("relatedDomainCode", "");
        goal.put("relatedDomainName", "");
        goal.put("relatedItemKey", "");
        goal.put("relatedItemType", "");
        goal.put("relatedItemName", "");
        goal.put("expectedAnswerPoints", List.of());
        return goal;
    }

    /**
     * 构建出题时的 RAG 检索上下文（如果输入中没有则返回默认值）。
     *
     * @param input 流式出题输入
     * @return 检索上下文 Map
     */
    private Map<String, Object> buildQuestionRetrievalContext(QuestionGenerationInput input) {
        if (input.getRetrievalContext() != null) {
            return toMap(input.getRetrievalContext());
        }
        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("summary", "暂无 RAG 检索资料");
        retrieval.put("retrievalPlans", List.of());
        retrieval.put("retrievedMaterials", List.of());
        retrieval.put("followUpCandidates", List.of());
        retrieval.put("retrievalAudit", Map.of(
                "retrievalTriggered", false,
                "lexicalCandidateCount", 0,
                "denseCandidateCount", 0,
                "rerankPreTopQuestionIds", List.of(),
                "rerankPostTopQuestionIds", List.of(),
                "injectedQuestionIds", List.of()
        ));
        return retrieval;
    }

    /**
     * 构建出题时的约束条件（如果输入中没有则返回默认值）。
     *
     * @param input 流式出题输入
     * @return 约束条件 Map
     */
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

    /**
     * 渲染提示词模板。
     *
     * <p>流程：
     * <ol>
     *   <li>从 PromptProperties 解析版本号</li>
     *   <li>调用 PromptTemplateService 渲染提示词</li>
     *   <li>记录日志</li>
     * </ol>
     *
     * @param promptCode 提示词编码
     * @param variables 变量 Map
     * @return 渲染后的提示词
     */
    private RenderedPrompt renderPrompt(String promptCode, Map<String, Object> variables) {
        String promptVersion = promptProperties.resolveVersion(promptCode);
        RenderedPrompt rendered = promptTemplateService.render(promptCode, promptVersion, variables);
        log.info("Prompt 渲染完成, promptCode={}, promptVersion={}",
                rendered.getPromptCode(), rendered.getPromptVersion());
        return rendered;
    }

    /**
     * 构建流式出题提示词变量。
     *
     * <p>输入变量：岗位、经验、简历摘要、角色上下文、项目上下文、最近上下文、目标上下文、检索上下文、约束条件、已出题、考纲等。
     *
     * @param input 流式出题输入
     * @return 变量 Map
     */
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

    /**
     * 格式化已出题列表为提示词友好的文本格式（每条前面加 - ）。
     *
     * @param askedQuestions 已出题列表
     * @return 格式化后的文本
     */
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

    /**
     * 格式化单题详细评估的最近上下文（问答历史）。
     *
     * <p>格式：- [题型/知识域] Q: 题目 | A: 回答
     *
     * @param recentContext 最近问答上下文列表
     * @return 格式化后的文本
     */
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

    /**
     * 格式化单题追问的历史记录。
     *
     * <p>格式：- 角色: 内容
     *
     * @param consultHistory 追问历史记录列表
     * @return 格式化后的文本
     */
    private String formatConsultHistory(List<QuestionConsultInput.ConsultTurn> consultHistory) {
        if (consultHistory == null || consultHistory.isEmpty()) {
            return "- 无";
        }
        StringBuilder sb = new StringBuilder();
        for (QuestionConsultInput.ConsultTurn turn : consultHistory) {
            if (turn == null || safeString(turn.getContent()).isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("- ")
                    .append(safeString(turn.getRole()))
                    .append(": ")
                    .append(safeString(turn.getContent()));
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    /**
     * 格式化停顿统计信息。
     *
     * <p>格式：wpm=xxx, longPauseCount=xxx, longestPauseMs=xxx
     *
     * @param pauseStats 停顿统计信息 Map
     * @return 格式化后的文本
     */
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

    /**
     * 安全地将 Object 转换为 int，失败时返回 0。
     *
     * @param value 待转换的值
     * @return 转换后的 int 值
     */
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

    /**
     * 格式化报告生成的问答对。
     *
     * <p>格式：题型、知识域、题目、回答、参考要点
     *
     * @param pairs 问答对列表
     * @return 格式化后的文本
     */
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
            sb.append("参考要点:\n").append(formatBulletLines(pair.getExpectedPoints()));
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    /**
     * 格式化列表为项目符号格式（每条前面加 - ）。
     *
     * @param lines 字符串列表
     * @return 格式化后的文本
     */
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

    /**
     * 格式化可用策略列表为提示词友好的文本格式。
     *
     * <p>格式：编号. 策略名称（StrategyCode: xxx）：/n- 意图：xxx /n- 适用条件：/n  xxx
     *
     * @param strategies 可用策略列表
     * @return 格式化后的文本
     */
    private String formatAvailableStrategies(List<EvaluationDecisionInput.AvailableStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            return "1. 结束面试（StrategyCode: S_WRAPUP）：\n- 意图：结束本场面试。\n- 适用条件：\n  当前已形成足够能力画像或已无继续追问价值。";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (EvaluationDecisionInput.AvailableStrategy strategy : strategies) {
            if (strategy == null || strategy.getStrategyCode() == null || strategy.getStrategyCode().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(index++).append(". ")
                    .append(safeString(strategy.getLabel()))
                    .append("（StrategyCode: ").append(safeString(strategy.getStrategyCode())).append("）：\n")
                    .append("- 意图：").append(safeString(strategy.getDescription())).append("\n")
                    .append("- 适用条件：\n  ").append(safeString(strategy.getApplicableWhen()));
        }
        return sb.isEmpty()
                ? "1. 结束面试（StrategyCode: S_WRAPUP）：\n- 意图：结束本场面试。\n- 适用条件：\n  当前已形成足够能力画像或已无继续追问价值。"
                : sb.toString();
    }

    /**
     * 格式化剩余待考察知识域列表为提示词友好的文本格式。
     *
     * <p>格式：编号. 知识域名称（domainCode: xxx）/n- 关联知识点：知识点1、知识点2、...
     *
     * @param domains 剩余待考察知识域列表
     * @return 格式化后的文本
     */
    private String formatRemainingTargetDomains(List<EvaluationDecisionInput.RemainingTargetDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return "- 无剩余待考察理论域";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (EvaluationDecisionInput.RemainingTargetDomain domain : domains) {
            if (domain == null || domain.getDomainCode() == null || domain.getDomainCode().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(index++).append(". ")
                    .append(safeString(domain.getDomainName()))
                    .append("（domainCode: ").append(safeString(domain.getDomainCode())).append("）\n")
                    .append("- 关联知识点：");
            List<String> focusPoints = domain.getFocusPoints();
            if (focusPoints == null || focusPoints.isEmpty()) {
                sb.append("无");
            } else {
                sb.append(String.join("、", focusPoints.stream()
                        .filter(item -> item != null && !item.isBlank())
                        .map(String::trim)
                        .toList()));
            }
        }
        return sb.isEmpty() ? "- 无剩余待考察理论域" : sb.toString();
    }

    /**
     * 构建首题改写的候选人上下文字符串。
     *
     * @param input 首题改写输入
     * @return 上下文字符串
     */
    private String buildCandidateContext(IntroRewriteInput input) {
        return "positionCode=" + safeString(input.getPositionCode())
                + ", experienceLevel=" + safeString(input.getExperienceLevel())
                + ", mode=" + safeString(input.getMode());
    }

    /**
     * 格式化学纲规划的知识域列表。
     *
     * <p>格式：- code=xxx, name=xxx
     *
     * @param domains 知识域信息列表
     * @return 格式化后的文本
     */
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
            sb.append("- code=").append(safeString(domain.getDomainCode()))
                    .append(", name=").append(safeString(domain.getDomainName()));
        }
        return sb.isEmpty() ? "- 无" : sb.toString();
    }

    /**
     * 将 Object 安全地转换为 Map<String, Object>，null 返回空 Map。
     *
     * @param value 待转换的值
     * @return 转换后的 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(objectMapper.convertValue(value, Map.class));
    }

    /**
     * 从状态账本中提取标量值，失败时返回 null。
     *
     * @param ledger 状态账本 Map
     * @param key 键名
     * @return 提取的值
     */
    private Object extractLedgerScalar(Map<String, Object> ledger, String key) {
        if (ledger == null || key == null || key.isBlank()) {
            return null;
        }
        return ledger.get(key);
    }

    /**
     * 从状态账本中提取列表值，失败时返回空列表。
     *
     * @param ledger 状态账本 Map
     * @param key 键名
     * @return 提取的字符串列表
     */
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

    /**
     * 将对象序列化为 JSON 字符串，null 返回 "{}"，失败时回退为 toString()。
     *
     * @param value 待序列化的对象
     * @return JSON 字符串
     */
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

    /**
     * 截断字符串用于日志显示（会先去除换行）。
     *
     * @param value 待截断的字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
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

    /**
     * 安全字符串处理，null 返回空字符串。
     *
     * @param value 待处理的字符串
     * @return 处理后的字符串
     */
    private String safeString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 截断字符串，null 返回空字符串。
     *
     * @param value 待截断的字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
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
