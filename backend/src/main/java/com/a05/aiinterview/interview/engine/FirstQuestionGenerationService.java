package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.ai.dto.QuestionGenerationOutput;
import com.a05.aiinterview.ai.entity.AiInvocationLog;
import com.a05.aiinterview.ai.service.AiInvocationLogService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 首题生成服务。
 * 在 Planner 规划完成后，负责生成面试的第一道题目并保存到数据库。
 *
 * <p>首题生成策略：
 * <ul>
 *   <li>若考纲中包含 INTRO 类型题目，优先生成自我介绍题，引导候选人进入面试状态</li>
 *   <li>若没有 INTRO，则从高优先级知识域中选择第一个域，生成 PRINCIPLE 类型题目</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirstQuestionGenerationService {

    private final AiClient aiClient;
    private final PromptProperties promptProperties;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final AiInvocationLogService aiInvocationLogService;

    /**
     * 生成并保存面试的第一道题目。
     *
     * @param session       面试会话实体（包含岗位、年限等上下文）
     * @param plannerOutput Planner 规划输出（包含考纲信息）
     * @return 生成的第一道题目实体（已持久化到数据库）
     */
    public InterviewQuestion generateAndSave(InterviewSession session, PlannerOutput plannerOutput) {
        log.info("开始生成首题, sessionId={}", session.getId());

        QuestionDecision decision = decideFirstQuestion(plannerOutput);

        QuestionGenerationInput input = QuestionGenerationInput.builder()
                .positionCode(session.getTargetRole())
                .mode(session.getMode())
                .experienceLevel(session.getExperienceLevel())
                .nextDomainId(decision.getDomainId())
                .nextDomainCode(decision.getDomainCode())
                .nextDomainName(decision.getDomainName())
                .nextQuestionType(decision.getQuestionType())
                .targetDepth(decision.getTargetDepth())
                .askedQuestions(List.of())
                .syllabus(new HashMap<>())
                .resumeTextSummary(null)
                .build();
        boolean success = true;
        String errorMessage = null;
        QuestionGenerationOutput output = null;
        AiCallResult<QuestionGenerationOutput> result = null;

        try {
            result = aiClient.callQuestionGeneration(input);
            output = result.getOutput();
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("首题生成 AI 调用失败, sessionId={}", session.getId(), e);
            throw e;
        } finally {
            int latencyMs = result != null ? (int) result.getLatencyMs() : 0;
            int promptTokens = result != null ? result.getPromptTokens() : 0;
            int responseTokens = result != null ? result.getResponseTokens() : 0;

            AiInvocationLog logEntry = AiInvocationLog.builder()
                    .sessionId(session.getId())
                    .userId(session.getUserId())
                    .promptCode("question_generation")
                    .promptVersion(promptProperties.resolveVersion("question_generation"))
                    .modelProvider(session.getModelProvider() != null ? session.getModelProvider() : "unknown")
                    .modelName(session.getModelName() != null ? session.getModelName() : "")
                    .requestTokens(promptTokens)
                    .responseTokens(responseTokens)
                    .latencyMs(latencyMs)
                    .success(success)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            aiInvocationLogService.saveAsync(logEntry);
        }

        // 将 AI 返回的题目内容封装为实体并持久化
        InterviewQuestion question = buildQuestion(session.getId(), 1, decision, output);
        interviewQuestionMapper.insert(question);

        log.info("首题生成并保存成功, sessionId={}, questionId={}, stem 前50字={}",
                session.getId(), question.getId(),
                question.getStem() != null ? question.getStem().substring(0, Math.min(50, question.getStem().length())) : "");
        return question;
    }

    /**
     * 根据考纲决定第一题的类型和知识域。
     * 优先选择 INTRO 类型作为开场；若考纲中没有 INTRO，则选择高优先级知识域的 PRINCIPLE 题目。
     */
    private QuestionDecision decideFirstQuestion(PlannerOutput plannerOutput) {
        if (plannerOutput.getQuestionMixPlan() != null
                && plannerOutput.getQuestionMixPlan().getOrDefault("INTRO", 0) > 0) {
            return QuestionDecision.builder()
                    .domainId(null)
                    .domainCode("intro")
                    .domainName("自我介绍")
                    .questionType("INTRO")
                    .targetDepth("L1")
                    .build();
        }

        if (plannerOutput.getDomains() != null && !plannerOutput.getDomains().isEmpty()) {
            PlannerOutput.DomainPlan firstDomain = plannerOutput.getDomains().stream()
                    .filter(d -> "high".equals(d.getPriority()))
                    .findFirst()
                    .orElse(plannerOutput.getDomains().get(0));

            return QuestionDecision.builder()
                    .domainId(firstDomain.getDomainId())
                    .domainCode(firstDomain.getDomainCode())
                    .domainName(firstDomain.getDomainName())
                    .questionType("PRINCIPLE")
                    .targetDepth(firstDomain.getTargetDepth() != null ? firstDomain.getTargetDepth() : "L3")
                    .build();
        }

        return QuestionDecision.builder()
                .domainId(null)
                .domainCode("intro")
                .domainName("Self-introduction")
                .questionType("INTRO")
                .targetDepth("L1")
                .build();
    }

    private InterviewQuestion buildQuestion(Long sessionId, int questionNo,
                                            QuestionDecision decision, QuestionGenerationOutput output) {
        InterviewQuestion question = new InterviewQuestion();
        question.setSessionId(sessionId);
        question.setQuestionNo(questionNo);
        question.setQuestionType(decision.getQuestionType());
        question.setDomainId(decision.getDomainId());
        question.setStem(output.getStem());
        question.setTargetSkill(output.getTargetSkill());
        question.setExpectedPoints(output.getExpectedPoints());
        question.setTargetDepth(output.getTargetDepth() != null ? output.getTargetDepth() : decision.getTargetDepth());
        question.setStatus("asked");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("domainCode", decision.getDomainCode());
        ctx.put("questionType", decision.getQuestionType());
        ctx.put("targetDepth", decision.getTargetDepth());
        question.setGenerationContextJson(ctx);

        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        return question;
    }

    /** 首题生成决策信息，包含知识域、题型、目标深度等 DTO */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class QuestionDecision {
        private Long domainId;
        private String domainCode;
        private String domainName;
        private String questionType;
        private String targetDepth;
    }
}
