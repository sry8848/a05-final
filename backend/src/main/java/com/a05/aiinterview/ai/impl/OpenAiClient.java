package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;


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

    private final ChatClient chatClient;

    public OpenAiClient(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
        log.info("OpenAiClient 已初始化（Spring AI ChatClient 模式）");
    }

    // ───────────────────────────── Planner ──────────────────────────────────

    @Override
    public AiCallResult<PlannerOutput> callPlanner(PlannerInput input) {
        log.info("调用 OpenAI Planner, positionCode={}, experienceLevel={}",
                input.getPositionCode(), input.getExperienceLevel());

        BeanOutputConverter<PlannerOutput> converter = new BeanOutputConverter<>(PlannerOutput.class);
        String userPrompt = buildPlannerUserPrompt(input) + "\n\n" + converter.getFormat();

        long startMs = System.currentTimeMillis();
        ChatResponse response = callChat(PLANNER_SYSTEM_PROMPT, userPrompt);
        PlannerOutput output = requireConvert(converter, response, "planner");
        log.info("Planner 调用成功，规划知识域数={}",
                output.getDomains() != null ? output.getDomains().size() : 0);
        return buildResult(output, response, System.currentTimeMillis() - startMs);
    }

    // ────────────────────────── QuestionGeneration ──────────────────────────

    @Override
    public AiCallResult<QuestionGenerationOutput> callQuestionGeneration(QuestionGenerationInput input) {
        log.info("调用 OpenAI 出题, domainCode={}, questionType={}",
                input.getNextDomainCode(), input.getNextQuestionType());

        BeanOutputConverter<QuestionGenerationOutput> converter =
                new BeanOutputConverter<>(QuestionGenerationOutput.class);
        String userPrompt = buildQuestionGenUserPrompt(input) + "\n\n" + converter.getFormat();

        long startMs = System.currentTimeMillis();
        ChatResponse response = callChat(QUESTION_GEN_SYSTEM_PROMPT, userPrompt);
        QuestionGenerationOutput output = requireConvert(converter, response, "question_generation");
        log.info("出题调用成功，stem 长度={}",
                output.getStem() != null ? output.getStem().length() : 0);
        return buildResult(output, response, System.currentTimeMillis() - startMs);
    }

    @Override
    public Flux<String> callQuestionGenerationStream(QuestionGenerationInput input) {
        log.info("调用 OpenAI 出题（流式）, domainCode={}", input.getNextDomainCode());
        String userPrompt = buildQuestionGenUserPrompt(input)
                + "\n\n请以纯文本输出题目正文，不要输出 JSON，不要包含任何额外说明。";
        return chatClient.prompt()
                .system(QUESTION_GEN_STREAM_SYSTEM_PROMPT)
                .user(userPrompt)
                .stream()
                .content();
    }

    // ────────────────────────── EvaluationDecision ──────────────────────────

    @Override
    public AiCallResult<EvaluationDecisionOutput> callEvaluationDecision(EvaluationDecisionInput input) {
        log.info("调用 OpenAI 评估决策, domainCode={}", input.getCurrentDomainCode());

        BeanOutputConverter<EvaluationDecisionOutput> converter =
                new BeanOutputConverter<>(EvaluationDecisionOutput.class);// 评估决策输出转换器
        String userPrompt = buildEvalDecisionUserPrompt(input) + "\n\n" + converter.getFormat();// 评估决策用户提示

        long startMs = System.currentTimeMillis();
        ChatResponse response = callChat(EVAL_DECISION_SYSTEM_PROMPT, userPrompt);
        EvaluationDecisionOutput output = requireConvert(converter, response, "evaluation_decision");
        log.info("评估决策调用成功，signal={}", output.getSignal());
        return buildResult(output, response, System.currentTimeMillis() - startMs);
    }

    // ─────────────────────────── ReportGeneration ───────────────────────────

    @Override
    public AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input) {
        log.info("调用 OpenAI 报告生成, positionCode={}, qaPairsCount={}",
                input.getPositionCode(),
                input.getQuestionAnswerPairs() != null ? input.getQuestionAnswerPairs().size() : 0);

        BeanOutputConverter<ReportGenerationOutput> converter =
                new BeanOutputConverter<>(ReportGenerationOutput.class);
        String userPrompt = buildReportGenUserPrompt(input) + "\n\n" + converter.getFormat();

        long startMs = System.currentTimeMillis();
        ChatResponse response = callChat(REPORT_GEN_SYSTEM_PROMPT, userPrompt);
        ReportGenerationOutput output = requireConvert(converter, response, "report_generation");
        log.info("报告生成调用成功，overallScore={}", output.getOverallScore());
        return buildResult(output, response, System.currentTimeMillis() - startMs);
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
    private <T> AiCallResult<T> buildResult(T output, ChatResponse response, long latencyMs) {
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
                .promptTokens(promptTokens)
                .responseTokens(responseTokens)
                .latencyMs(latencyMs)
                .build();
    }

    // ──────────────────────────── Prompt 构造 ────────────────────────────────

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一名经验丰富的技术面试官，擅长设计面试考纲。
            请根据候选人信息生成结构化的面试考纲，严格按照 JSON Schema 格式输出，不要输出任何额外文字。
            """;

    private static final String QUESTION_GEN_SYSTEM_PROMPT = """
            你是一名技术面试官，正在进行一场模拟面试。
            请根据要求出一道面试题，严格按照 JSON Schema 格式输出，不要输出任何额外文字。
            """;

    private static final String QUESTION_GEN_STREAM_SYSTEM_PROMPT = """
            你是一名技术面试官，正在进行一场模拟面试。
            请用自然语言口语化地提问，直接输出题目文本，不要包含 JSON 或 Markdown。
            """;

    private static final String EVAL_DECISION_SYSTEM_PROMPT = """
            你是一名技术面试考官，需要对候选人的回答进行评估，并决定下一步面试策略。
            请严格按照 JSON Schema 格式输出评估决策结果，不要输出任何额外文字。
            """;

    private static final String REPORT_GEN_SYSTEM_PROMPT = """
            你是一名技术面试评委，需要根据本场面试的完整 Q/A 记录生成评估报告。
            请严格按照 JSON Schema 格式输出报告，不要输出任何额外文字。
            """;

    private String buildPlannerUserPrompt(PlannerInput input) {
        StringBuilder sb = new StringBuilder("请为以下候选人生成面试考纲：\n\n");
        sb.append("岗位：").append(input.getPositionName())
                .append("（").append(input.getPositionCode()).append("）\n");
        sb.append("工作年限：").append(input.getExperienceLevel()).append("\n");
        sb.append("面试模式：").append(input.getMode()).append("\n");

        if (input.getJobDescription() != null && !input.getJobDescription().isBlank()) {
            sb.append("JD 内容：\n")
                    .append(input.getJobDescription(), 0,
                            Math.min(500, input.getJobDescription().length()))
                    .append("\n");
        }
        if (input.getResumeText() != null && !input.getResumeText().isBlank()) {
            sb.append("简历内容：\n")
                    .append(input.getResumeText(), 0,
                            Math.min(1000, input.getResumeText().length()))
                    .append("\n");
        }
        if (input.getFocusTopics() != null && !input.getFocusTopics().isBlank()) {
            sb.append("候选人希望重点考察：").append(input.getFocusTopics()).append("\n");
        }
        if (input.getDomains() != null && !input.getDomains().isEmpty()) {
            sb.append("\n可考察的知识域列表：\n");
            input.getDomains().forEach(d ->
                    sb.append("- id=").append(d.getDomainId())
                            .append(", code=").append(d.getDomainCode())
                            .append(", name=").append(d.getDomainName()).append("\n"));
        }
        return sb.toString();
    }

    private String buildQuestionGenUserPrompt(QuestionGenerationInput input) {
        StringBuilder sb = new StringBuilder("请出一道面试题，要求如下：\n\n");
        sb.append("知识域：").append(input.getNextDomainName())
                .append("（").append(input.getNextDomainCode()).append("）\n");
        sb.append("题目类型：").append(input.getNextQuestionType()).append("\n");
        sb.append("目标深度：").append(input.getTargetDepth()).append("\n");
        sb.append("候选人岗位：").append(input.getPositionCode())
                .append("，年限：").append(input.getExperienceLevel()).append("\n");
        sb.append("面试模式：").append(input.getMode()).append("\n");

        if (input.getRagContext() != null && !input.getRagContext().isBlank()) {
            sb.append("\n参考知识库内容：\n").append(input.getRagContext()).append("\n");
        }
        if (input.getAskedQuestions() != null && !input.getAskedQuestions().isEmpty()) {
            sb.append("\n已问过的题目（避免重复）：\n");
            input.getAskedQuestions().forEach(q ->
                    sb.append("- ").append(q.getStemSummary()).append("\n"));
        }
        return sb.toString();
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
}
