package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.dto.InterviewSyllabus;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.service.support.InterviewDomainDisplaySupport;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.service.PositionService;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Planner 编排服务，负责协调面试考纲生成的完整流程。
 * 当用户创建面试时，系统会异步执行此服务，将 session.status=planning 更新为 in_progress。
 * 主要步骤：
 * <ol>
 *   <li>获取简历解析文本（若有）</li>
 *   <li>获取岗位对应的知识域列表</li>
 *   <li>调用 AI Planner 生成考纲</li>
 *   <li>将考纲结果保存到 session.syllabus_json</li>
 *   <li>初始化状态账本 session_skill_states</li>
 *   <li>生成第一道题目并保存到 interview_questions</li>
 *   <li>将首题快照存入 session.first_question_json</li>
 *   <li>更新 session.status = in_progress</li>
 * </ol>
 *
 * <p>任何步骤失败都会在 catch 块中将 status = aborted，并记录错误日志供排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerOrchestrationService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final ResumeMapper resumeMapper;
    private final PositionService positionService;
    private final AiClient aiClient;
    private final StateLedgerInitService stateLedgerInitService;
    private final FirstQuestionGenerationService firstQuestionGenerationService;
    private final PlannerHistoryBuilderService plannerHistoryBuilderService;
    private final PlannerDomainNormalizationService plannerDomainNormalizationService;
    private final PlannerHistoryDedupService plannerHistoryDedupService;
    private final ResumeExperienceMergeService resumeExperienceMergeService;
    private final InterviewSyllabusAssembler interviewSyllabusAssembler;
    private final InterviewDebugTraceService interviewDebugTraceService;
    private final ObjectMapper objectMapper;

    /**
     * 异步执行 Planner 编排流程。
     * 由 InterviewService.createInterview() 调用，在事务提交后异步执行。
     *
     * @param sessionId 面试会话 ID
     */
    @Async
    public void runAsync(Long sessionId) {
        log.info("Planner 编排流程开始, sessionId={}", sessionId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            log.error("Planner 编排流程找不到会话，可能已被删除, sessionId={}", sessionId);
            return;
        }

        try {
            String resumeText = fetchResumeText(session);

            // 2. 获取岗位对应的知识域列表
            List<PositionSkillDomain> domains = positionService.listSkillDomainEntities(session.getTargetRole());

            PlannerInput plannerInput = buildPlannerInput(session, resumeText, domains);
            interviewDebugTraceService.recordPlannerStage(
                    sessionId,
                    "plannerInput",
                    plannerInput,
                    Map.of(
                            "domainsCount", plannerInput.getDomains() == null ? 0 : plannerInput.getDomains().size(),
                            "historyCount", plannerInput.getHistoryInterviews() == null ? 0 : plannerInput.getHistoryInterviews().size()
                    )
            );

            AiCallResult<PlannerOutput> plannerResult = aiClient.callPlanner(plannerInput);
            PlannerOutput plannerOutput = plannerResult.getOutput();
            interviewDebugTraceService.recordPlannerStage(
                    sessionId,
                    "plannerOutput",
                    buildPlannerDebugPayload(plannerResult, plannerOutput),
                    Map.of(
                            "domainsCount", plannerOutput.getDomains() == null ? 0 : plannerOutput.getDomains().size(),
                            "experienceItemsCount", plannerOutput.getExperienceItems() == null ? 0 : plannerOutput.getExperienceItems().size()
                    )
            );

            validateAndFillPlannerOutput(plannerOutput, sessionId);
            PlannerDomainNormalizationService.NormalizationResult normalizationResult =
                    plannerDomainNormalizationService.normalize(plannerOutput, domains);
            plannerOutput = normalizationResult.normalizedOutput();
            recordPlannerNormalization(sessionId, normalizationResult);
            plannerOutput = resumeExperienceMergeService.mergeIntoPlannerOutput(resumeText, plannerOutput);
            plannerOutput = plannerHistoryDedupService.deduplicate(
                    plannerOutput,
                    domains,
                    plannerInput.getHistoryInterviews()
            );
            InterviewSyllabus syllabus = interviewSyllabusAssembler.assemble(plannerOutput, domains);
            interviewDebugTraceService.recordPlannerStage(
                    sessionId,
                    "assembledSyllabus",
                    syllabus,
                    Map.of(
                            "rawDomainsCount", normalizationResult.rawDomainsCount(),
                            "droppedDomains", normalizationResult.droppedDomains(),
                            "backfilledDomains", normalizationResult.backfilledDomains(),
                            "finalDomainsCount", normalizationResult.finalDomainsCount(),
                            "domainsCount", syllabus.getDomains() == null ? 0 : syllabus.getDomains().size(),
                            "experienceItemsCount", syllabus.getExperienceItems() == null ? 0 : syllabus.getExperienceItems().size()
                    )
            );

            // 5. 将考纲结果保存到 session
            @SuppressWarnings("unchecked")
            Map<String, Object> syllabusMap = objectMapper.convertValue(syllabus, Map.class);
            InterviewSession syllabusUpdate = new InterviewSession();
            syllabusUpdate.setId(sessionId);
            syllabusUpdate.setSyllabusJson(syllabusMap);
            syllabusUpdate.setUpdatedAt(LocalDateTime.now());
            interviewSessionMapper.updateById(syllabusUpdate);

            // 6. 初始化状态账本 session_skill_states
            Map<String, Object> ledger = stateLedgerInitService.initLedger(
                    sessionId,
                    session.getExperienceLevel(),
                    syllabus,
                    domains
            );
            session.setStateLedgerJson(ledger);

            // 7. 生成第一道题目
            InterviewQuestion firstQuestion = firstQuestionGenerationService.generateAndSave(session, plannerOutput);
            ledger.put("asked_total", 1);

            Map<String, Object> firstQuestionSnapshot = buildFirstQuestionSnapshot(firstQuestion, domains);

            // 9. 更新 session 状态为 in_progress
            InterviewSession progressUpdate = new InterviewSession();
            progressUpdate.setId(sessionId);
            progressUpdate.setStateLedgerJson(ledger);
            progressUpdate.setFirstQuestionJson(firstQuestionSnapshot);
            progressUpdate.setCurrentQuestionNo(1);
            progressUpdate.setStatus("in_progress");
            progressUpdate.setStartedAt(LocalDateTime.now());
            progressUpdate.setUpdatedAt(LocalDateTime.now());
            interviewSessionMapper.updateById(progressUpdate);

            log.info("Planner 编排流程完成, sessionId={}, status=in_progress", sessionId);

        } catch (Exception e) {
            log.error("Planner 编排流程异常，设置 session status=aborted, sessionId={}", sessionId, e);
            InterviewSession abortedUpdate = new InterviewSession();
            abortedUpdate.setId(sessionId);
            abortedUpdate.setStatus("aborted");
            abortedUpdate.setUpdatedAt(LocalDateTime.now());
            interviewSessionMapper.updateById(abortedUpdate);
        }
    }

    private String fetchResumeText(InterviewSession session) {
        if (session.getResumeId() == null) {
            return null;
        }
        try {
            var resume = resumeMapper.selectById(session.getResumeId());
            return resume != null ? resume.getParsedText() : null;
        } catch (Exception e) {
            log.warn("获取简历解析文本失败，忽略继续, resumeId={}", session.getResumeId(), e);
            return null;
        }
    }

    private PlannerInput buildPlannerInput(InterviewSession session, String resumeText,
                                           List<PositionSkillDomain> domains) {
        List<PlannerInput.DomainInfo> domainInfos = domains.stream()
                .map(d -> PlannerInput.DomainInfo.builder()
                        .domainCode(d.getDomainCode())
                        .domainName(d.getDomainName())
                        .build())
                .collect(Collectors.toList());

        return PlannerInput.builder()
                .interviewId(session.getId())
                .positionCode(session.getTargetRole())
                .positionName(resolvePositionName(session.getTargetRole()))
                .experienceLevel(session.getExperienceLevel())
                .roundType("")
                .mode(session.getMode())
                .jobDescription(session.getJobDescription())
                .resumeText(resumeText)
                .focusTopics(session.getFocusTopics())
                .domains(domainInfos)
                .historyInterviews(plannerHistoryBuilderService.buildRecentHistory(session))
                .build();
    }

    /**
     * 将首题实体转换为 Map 快照，存入 first_question_json。
     * 包含题目 ID、题号、题型、知识域名称等前端展示所需字段。
     */
    private Map<String, Object> buildFirstQuestionSnapshot(InterviewQuestion q, List<PositionSkillDomain> domains) {
        String domainCode = q.getDomainCode();
        String contextDomainName = null;
        if (q.getGenerationContextJson() != null) {
            Object rawDomainName = q.getGenerationContextJson().get("domainName");
            if (rawDomainName instanceof String s && !s.isBlank()) {
                contextDomainName = s;
            }
        }
        String domainName = firstNonBlank(
                contextDomainName,
                domains.stream()
                        .filter(d -> Objects.equals(d.getDomainCode(), domainCode))
                        .findFirst()
                        .map(PositionSkillDomain::getDomainName)
                        .orElse(null),
                InterviewDomainDisplaySupport.resolveSpecialDomainName(domainCode)
        );

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionId", q.getId());
        snapshot.put("questionNo", q.getQuestionNo());
        snapshot.put("questionType", q.getQuestionType());
        snapshot.put("domainCode", domainCode);
        snapshot.put("domainName", domainName);
        snapshot.put("stem", q.getStem());
        snapshot.put("targetSkill", q.getTargetSkill());
        snapshot.put("aiResultStatus", resolveAiResultStatus(q));
        snapshot.put("hintAvailable", true);
        return snapshot;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resolveAiResultStatus(InterviewQuestion question) {
        if (question.getGenerationContextJson() == null) {
            return null;
        }
        Object value = question.getGenerationContextJson().get("aiResultStatus");
        return value instanceof String s ? s : null;
    }

    /**
     * 对 Planner 输出进行校验和补全，防止 AI 返回不完整数据导致后续流程异常。
     * 若关键字段缺失，则填充默认值并打印 warning。
     * 这样可以保证后续流程不会因为空指针而中断（避免 NPE）。
     */
    private void validateAndFillPlannerOutput(PlannerOutput output, Long sessionId) {
        if (output.getDomains() == null || output.getDomains().isEmpty()) {
            log.warn("Planner 输出缺少 domains，设为空列表, sessionId={}", sessionId);
            output.setDomains(Collections.emptyList());
        }
        if (output.getExperienceItems() == null) {
            output.setExperienceItems(Collections.emptyList());
        }
    }

    private void recordPlannerNormalization(Long sessionId,
                                            PlannerDomainNormalizationService.NormalizationResult normalizationResult) {
        if (normalizationResult == null) {
            return;
        }
        if (!normalizationResult.droppedDomains().isEmpty()) {
            log.warn("Planner 输出包含跨岗位或非法知识域，已过滤, sessionId={}, droppedDomains={}",
                    sessionId, normalizationResult.droppedDomains());
        }
        if (!normalizationResult.backfilledDomains().isEmpty()) {
            log.info("Planner 输出知识域不足，已按岗位配置补齐, sessionId={}, backfilledDomains={}",
                    sessionId, normalizationResult.backfilledDomains());
        }
        interviewDebugTraceService.recordPlannerStage(
                sessionId,
                "normalizedPlannerDomains",
                Map.of(
                        "rawDomainsCount", normalizationResult.rawDomainsCount(),
                        "droppedDomains", normalizationResult.droppedDomains(),
                        "backfilledDomains", normalizationResult.backfilledDomains(),
                        "finalDomainsCount", normalizationResult.finalDomainsCount(),
                        "normalizedDomains", normalizationResult.normalizedOutput().getDomains()
                ),
                Map.of(
                        "rawDomainsCount", normalizationResult.rawDomainsCount(),
                        "droppedDomains", normalizationResult.droppedDomains(),
                        "backfilledDomains", normalizationResult.backfilledDomains(),
                        "finalDomainsCount", normalizationResult.finalDomainsCount()
                )
        );
    }

    /** 根据岗位编码解析岗位中文名称，用于 Planner 输出标题展示。 */
    private String resolvePositionName(String targetRole) {
        return switch (targetRole) {
            case "JAVA_BACKEND" -> "Java Backend";
            case "GO_BACKEND" -> "Go Backend";
            case "FRONTEND" -> "Frontend";
            case "DATA_ENGINEER" -> "Data Engineer";
            case "QA" -> "QA Engineer";
            case "DEVOPS" -> "DevOps Engineer";
            default -> targetRole;
        };
    }

    private Map<String, Object> buildPlannerDebugPayload(AiCallResult<PlannerOutput> plannerResult,
                                                         PlannerOutput plannerOutput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("promptCode", plannerResult.getPromptCode());
        payload.put("promptVersion", plannerResult.getPromptVersion());
        payload.put("promptTokens", plannerResult.getPromptTokens());
        payload.put("responseTokens", plannerResult.getResponseTokens());
        payload.put("latencyMs", plannerResult.getLatencyMs());
        payload.put("systemPrompt", plannerResult.getSystemPrompt());
        payload.put("userPrompt", plannerResult.getUserPrompt());
        payload.put("rawResponse", plannerResult.getRawResponse());
        payload.put("parsedOutput", plannerOutput);
        return payload;
    }
}
