package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首题生成服务。
 * 固定首题为 INTRO，流程为：题库选底稿 -> AI 改写 -> 失败回退到底稿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirstQuestionGenerationService {

    private static final String INTRO_DOMAIN_CODE = "intro";
    private static final String INTRO_QUESTION_TYPE = "INTRO";
    private static final String INTRO_TARGET_DEPTH = "L1";
    private static final String INTRO_TARGET_SKILL = "沟通表达与项目概述";
    private static final String INTRO_REWRITE_PROMPT_CODE = "intro_rewrite";
    private static final String DEFAULT_INTRO_BASE_PROMPT = "请先做一个简短的自我介绍，重点介绍你的技术背景和最近参与的项目。";
    private static final List<String> INTRO_EXPECTED_POINTS = List.of(
            "技术方向与核心技术栈",
            "最近一年代表项目与职责边界",
            "项目中的关键贡献或问题解决"
    );

    private final AiClient aiClient;
    private final PromptProperties promptProperties;
    private final IntroQuestionStrategyService introQuestionStrategyService;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final AiInvocationLogService aiInvocationLogService;

    /**
     * 生成并保存面试的第一道题目（固定 INTRO）。
     */
    public InterviewQuestion generateAndSave(InterviewSession session, PlannerOutput plannerOutput) {
        log.info("开始生成首题（固定 INTRO）, sessionId={}", session.getId());

        IntroQuestionStrategyService.IntroQuestionSelection selection =
                introQuestionStrategyService.selectIntroForUser(session.getUserId());

        String basePrompt = safePrompt(selection.getBasePrompt());
        boolean rewritten = false;
        String finalStem = basePrompt;

        boolean rewriteSuccess = true;
        String rewriteErrorMessage = null;
        AiCallResult<String> rewriteResult = null;

        String rewritePromptCode = INTRO_REWRITE_PROMPT_CODE;
        String rewritePromptVersion = promptProperties.resolveVersion(INTRO_REWRITE_PROMPT_CODE);

        try {
            IntroRewriteInput input = IntroRewriteInput.builder()
                    .positionCode(session.getTargetRole())
                    .experienceLevel(session.getExperienceLevel())
                    .mode(session.getMode())
                    .basePrompt(basePrompt)
                    .recentPrompts(selection.getRecentPrompts())
                    .avoidPhrases(selection.getAvoidPhrases())
                    .build();
            rewriteResult = aiClient.callIntroRewrite(input);

            rewritePromptCode = resolvePromptCode(rewriteResult, rewritePromptCode);
            rewritePromptVersion = resolvePromptVersion(rewriteResult, rewritePromptVersion);
            String rewrittenStem = rewriteResult.getOutput() != null ? rewriteResult.getOutput().trim() : "";
            if (rewrittenStem.isBlank()) {
                rewriteSuccess = false;
                rewriteErrorMessage = "intro_rewrite returned blank output";
                log.warn("INTRO 改写为空，回退到底稿, sessionId={}", session.getId());
            } else {
                rewritten = true;
                finalStem = rewrittenStem;
            }
        } catch (Exception e) {
            rewriteSuccess = false;
            rewriteErrorMessage = e.getMessage();
            log.warn("INTRO 改写失败，回退到底稿, sessionId={}", session.getId(), e);
        } finally {
            recordIntroRewriteLog(session, rewriteSuccess, rewriteErrorMessage, rewriteResult);
        }

        InterviewQuestion question = buildIntroQuestion(
                session.getId(),
                1,
                finalStem,
                selection,
                rewritten,
                rewritePromptCode,
                rewritePromptVersion
        );
        interviewQuestionMapper.insert(question);

        log.info("首题生成并保存成功, sessionId={}, questionId={}, rewritten={}",
                session.getId(), question.getId(), rewritten);
        return question;
    }

    private InterviewQuestion buildIntroQuestion(Long sessionId,
                                                 int questionNo,
                                                 String stem,
                                                 IntroQuestionStrategyService.IntroQuestionSelection selection,
                                                 boolean rewritten,
                                                 String rewritePromptCode,
                                                 String rewritePromptVersion) {
        InterviewQuestion question = new InterviewQuestion();
        question.setSessionId(sessionId);
        question.setQuestionNo(questionNo);
        question.setQuestionType(INTRO_QUESTION_TYPE);
        question.setDomainId(null);
        question.setStem(safePrompt(stem));
        question.setTargetSkill(INTRO_TARGET_SKILL);
        question.setExpectedPoints(INTRO_EXPECTED_POINTS);
        question.setTargetDepth(INTRO_TARGET_DEPTH);
        question.setStatus("asked");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("domainCode", INTRO_DOMAIN_CODE);
        ctx.put("questionType", INTRO_QUESTION_TYPE);
        ctx.put("targetDepth", INTRO_TARGET_DEPTH);
        ctx.put("variantId", selection.getVariantId());
        ctx.put("basePromptText", selection.getBasePrompt());
        ctx.put("rewritten", rewritten);
        ctx.put("historyAvoidCount", selection.getHistoryAvoidCount());
        ctx.put("rewritePromptCode", rewritePromptCode);
        ctx.put("rewritePromptVersion", rewritePromptVersion);
        question.setGenerationContextJson(ctx);

        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        return question;
    }

    private void recordIntroRewriteLog(InterviewSession session,
                                       boolean success,
                                       String errorMessage,
                                       AiCallResult<String> result) {
        AiInvocationLog logEntry = AiInvocationLog.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .promptCode(resolvePromptCode(result, INTRO_REWRITE_PROMPT_CODE))
                .promptVersion(resolvePromptVersion(result,
                        promptProperties.resolveVersion(INTRO_REWRITE_PROMPT_CODE)))
                .modelProvider(session.getModelProvider() != null ? session.getModelProvider() : "unknown")
                .modelName(session.getModelName() != null ? session.getModelName() : "")
                .requestTokens(result != null ? result.getPromptTokens() : 0)
                .responseTokens(result != null ? result.getResponseTokens() : 0)
                .latencyMs(result != null ? (int) result.getLatencyMs() : 0)
                .success(success)
                .errorMessage(errorMessage)
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

    private String safePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return DEFAULT_INTRO_BASE_PROMPT;
        }
        return prompt.trim();
    }
}
