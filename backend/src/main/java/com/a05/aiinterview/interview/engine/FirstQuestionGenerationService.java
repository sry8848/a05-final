package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.common.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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

    private static final ObjectMapper AUDIT_OBJECT_MAPPER = new ObjectMapper();
    private static final String INTRO_DOMAIN_CODE = "intro";
    private static final String INTRO_QUESTION_TYPE = "INTRO";
    private static final String INTRO_TARGET_SKILL = "沟通表达与项目概述";
    private static final String INTRO_REWRITE_PROMPT_CODE = "intro_rewrite";
    private static final int INTRO_REWRITE_MAX_LEN = 180;
    private static final int RESPONSE_PREVIEW_MAX_LEN = 120;
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
        String aiResultStatus = "success";
        String fallbackReason = "";

        boolean rewriteSuccess = true;
        String rewriteErrorMessage = null;
        AiCallResult<String> rewriteResult = null;

        String rewritePromptCode = INTRO_REWRITE_PROMPT_CODE;
        String rewritePromptVersion = promptProperties.resolveVersion(INTRO_REWRITE_PROMPT_CODE);

        try {
            IntroRewriteInput input = IntroRewriteInput.builder()
                    .interviewId(session.getId())
                    .questionId(null)
                    .variantId(selection.getVariantId())
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
            if (isInvalidRewriteText(rewrittenStem)) {
                rewriteSuccess = false;
                aiResultStatus = "fallback";
                fallbackReason = rewrittenStem.isBlank()
                        ? "blank_output"
                        : "too_long_output";
                rewriteErrorMessage = "intro_rewrite invalid output: " + fallbackReason;
                log.info("========== 兜底题提示 ==========");
                log.info("【首题改写失败】AI 改写输出无效，回退到底稿题目");
                log.info("原因: {}", fallbackReason);
                log.info("=================================");
                log.warn("INTRO 改写无效，回退到底稿, sessionId={}, reason={}", session.getId(), fallbackReason);
            } else {
                rewritten = true;
                finalStem = rewrittenStem;
            }
        } catch (Exception e) {
            rewriteSuccess = false;
            aiResultStatus = "fallback";
            fallbackReason = "exception";
            rewriteErrorMessage = e.getMessage();
            log.info("========== 兜底题提示 ==========");
            log.info("【首题改写异常】AI 改写过程发生异常，回退到底稿题目");
            log.info("异常信息: {}", e.getMessage());
            log.info("=================================");
            log.warn("INTRO 改写失败，回退到底稿, sessionId={}", session.getId(), e);
        } finally {
            recordIntroRewriteLiteAudit(session, selection.getVariantId(), rewriteSuccess,
                    fallbackReason, rewriteResult, rewritePromptCode, rewritePromptVersion, rewriteErrorMessage);
        }

        InterviewQuestion question = buildIntroQuestion(
                session.getId(),
                1,
                finalStem,
                selection,
                rewritten,
                aiResultStatus,
                fallbackReason,
                rewritePromptCode,
                rewritePromptVersion
        );
        try {
            interviewQuestionMapper.insert(question);
        } catch (DuplicateKeyException ex) {
            // 并发重入同一 session 初始化时，question_no=1 可能已被其他线程插入，回读已存在首题复用。
            InterviewQuestion existing = interviewQuestionMapper.selectOne(
                    new LambdaQueryWrapper<InterviewQuestion>()
                            .eq(InterviewQuestion::getSessionId, session.getId())
                            .eq(InterviewQuestion::getQuestionNo, 1)
                            .last("LIMIT 1")
            );
            if (existing == null) {
                throw ex;
            }
            question = existing;
            log.warn("首题已存在，复用已有记录, sessionId={}, questionId={}", session.getId(), existing.getId());
        }

        log.info("首题生成并保存成功, sessionId={}, questionId={}, rewritten={}",
                session.getId(), question.getId(), rewritten);
        return question;
    }

    private InterviewQuestion buildIntroQuestion(Long sessionId,
                                                 int questionNo,
                                                 String stem,
                                                 IntroQuestionStrategyService.IntroQuestionSelection selection,
                                                 boolean rewritten,
                                                 String aiResultStatus,
                                                 String fallbackReason,
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
        question.setStatus("asked");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("domainCode", INTRO_DOMAIN_CODE);
        ctx.put("questionType", INTRO_QUESTION_TYPE);
        ctx.put("variantId", selection.getVariantId());
        ctx.put("basePromptText", selection.getBasePrompt());
        ctx.put("rewritten", rewritten);
        ctx.put("aiResultStatus", aiResultStatus);
        ctx.put("fallbackReason", fallbackReason);
        ctx.put("historyAvoidCount", selection.getHistoryAvoidCount());
        ctx.put("rewritePromptCode", rewritePromptCode);
        ctx.put("rewritePromptVersion", rewritePromptVersion);
        question.setGenerationContextJson(ctx);

        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        return question;
    }

    private void recordIntroRewriteLiteAudit(InterviewSession session,
                                             String variantId,
                                             boolean success,
                                             String fallbackReason,
                                             AiCallResult<String> result,
                                             String promptCode,
                                             String promptVersion,
                                             String errorMessage) {
        try {
            ObjectNode audit = AUDIT_OBJECT_MAPPER.createObjectNode();
            audit.put("traceId", TraceContext.getOrCreateTraceId());
            audit.put("requestId", TraceContext.getOrCreateRequestId());
            audit.put("interviewId", session.getId());
            audit.putNull("questionId");
            audit.put("promptCode", promptCode);
            audit.put("variantId", variantId == null ? "" : variantId);
            audit.put("model", session.getModelName() == null ? "" : session.getModelName());
            audit.put("latencyMs", result != null ? Math.max(result.getLatencyMs(), 0) : 0);
            audit.put("status", success ? "success" : "fallback");
            audit.put("fallbackReason", fallbackReason == null ? "" : fallbackReason);
            audit.put("promptVersion", promptVersion == null ? "" : promptVersion);

            String output = result != null ? result.getOutput() : null;
            if (output != null && !output.isBlank()) {
                String preview = output.replace("\r", " ").replace("\n", " ").trim();
                if (preview.length() > RESPONSE_PREVIEW_MAX_LEN) {
                    preview = preview.substring(0, RESPONSE_PREVIEW_MAX_LEN);
                }
                audit.put("responsePreview", preview);
                audit.put("responseLength", output.length());
            } else {
                audit.put("responseLength", 0);
            }
            if (!success && errorMessage != null && !errorMessage.isBlank()) {
                audit.put("errorMessage", errorMessage.length() > 200
                        ? errorMessage.substring(0, 200)
                        : errorMessage);
            }
            log.info("ai_lite_audit={}", audit);
        } catch (Exception e) {
            log.warn("INTRO Lite 审计日志记录失败, sessionId={}", session.getId(), e);
        }
    }

    private boolean isInvalidRewriteText(String rewrittenStem) {
        return rewrittenStem == null
                || rewrittenStem.isBlank()
                || rewrittenStem.length() > INTRO_REWRITE_MAX_LEN;
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
