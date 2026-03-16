package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.common.enums.ExperienceLevel;
import com.a05.aiinterview.common.enums.InterviewMode;
import com.a05.aiinterview.common.enums.TargetRole;
import com.a05.aiinterview.interview.dto.CreateInterviewRequest;
import com.a05.aiinterview.interview.dto.CreateInterviewResponse;
import com.a05.aiinterview.interview.dto.InterviewDetailDto;
import com.a05.aiinterview.interview.dto.QuestionDto;
import com.a05.aiinterview.interview.dto.SyllabusSummaryDto;
import com.a05.aiinterview.interview.engine.PlannerOrchestrationService;
import com.a05.aiinterview.interview.entity.InterviewPreference;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.entity.Resume;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试会话主服务。
 * 提供创建会话（同步落库 + 异步触发 Planner）和查询会话详情（含首题）两个核心能力。
 *
 * <p>注意：M2 升级后，SSE 流式出题逻辑已迁移至 {@link QuestionStreamService}，
 * 本类不再包含 streamQuestion 方法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewPreferenceService preferenceService;
    private final PlannerOrchestrationService plannerOrchestrationService;

    @Value("${ai.openai.model:mock}")
    private String aiModel;

    @Value("${ai.openai.mock-enabled:true}")
    private boolean mockEnabled;

    /**
     * 创建面试会话（同步部分）。
     * 完成参数校验、落库后立即返回 sessionId，异步触发 Planner 生成考纲与首题。
     *
     * @param userId  当前登录用户 ID
     * @param request 前端提交的创建参数
     * @return 会话 ID 和初始状态
     */
    public CreateInterviewResponse createInterview(Long userId, CreateInterviewRequest request) {
        log.info("创建面试会话, userId={}, targetRole={}, experienceLevel={}, mode={}",
                userId, request.getTargetRole(), request.getExperienceLevel(), request.getMode());

        // 1. 校验枚举合法性
        validateEnums(request);

        // 2. 校验简历（若提供）
        String resumeParsedText = null;
        if (request.getResumeId() != null) {
            Resume resume = resumeMapper.selectById(request.getResumeId());
            if (resume == null || !resume.getUserId().equals(userId)) {
                throw new IllegalArgumentException("简历不存在或无权访问");
            }
            if (!"parsed".equals(resume.getParseStatus())) {
                throw new IllegalArgumentException("简历尚未解析完成，请等待解析后再发起面试");
            }
            resumeParsedText = resume.getParsedText();
        }

        // 3. 构建会话实体
        InterviewSession session = buildSession(userId, request);
        interviewSessionMapper.insert(session);
        log.info("面试会话已落库, sessionId={}", session.getId());

        // 4. 若 rememberSettings != false，保存偏好
        if (!Boolean.FALSE.equals(request.getRememberSettings())) {
            savePreference(userId, request);
        }

        // 5. 异步触发 Planner 编排（考纲生成 + 状态初始化 + 首题生成）
        if (isSingleQuestionRequest(request)) {
            initializeSingleQuestionSession(session, request);
            log.info("?????????, sessionId={}, status=in_progress", session.getId());
            return new CreateInterviewResponse(session.getId(), "in_progress");
        }

        plannerOrchestrationService.runAsync(session.getId());
        log.info("Planner ???????, sessionId={}", session.getId());
        return new CreateInterviewResponse(session.getId(), session.getStatus());
    }

    /**
     * 获取面试会话详情，供加载页轮询使用。
     * 当 status=in_progress 时，响应中内嵌首题（currentQuestion 字段）。
     *
     * @param sessionId 会话 ID
     * @param userId    当前登录用户 ID（用于归属校验）
     * @return 会话详情 DTO
     */
    public InterviewDetailDto getSessionDetail(Long sessionId, Long userId) {
        log.info("查询面试会话详情, sessionId={}, userId={}", sessionId, userId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }

        InterviewDetailDto dto = new InterviewDetailDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setTargetRole(session.getTargetRole());
        dto.setExperienceLevel(session.getExperienceLevel());
        dto.setMode(session.getMode());
        dto.setCurrentQuestionNo(session.getCurrentQuestionNo());
        dto.setStatus(session.getStatus());

        // 提取考纲摘要（plannedDomains 列表，用于 Loading 页展示）
        //TODO  Loading 页不展示
        dto.setSyllabusSummary(buildSyllabusSummary(session));

        // 若已进入 in_progress，内嵌首题
        if ("in_progress".equals(session.getStatus()) && session.getFirstQuestionJson() != null) {
            dto.setCurrentQuestion(mapToQuestionDto(session.getFirstQuestionJson()));
        }

        log.info("查询面试会话详情完成, sessionId={}, status={}", sessionId, session.getStatus());
        return dto;
    }

    // ==================== 私有方法 ====================



    private boolean isSingleQuestionRequest(CreateInterviewRequest request) {
        return StringUtils.hasText(request.getSingleQuestionStem());
    }

    private void initializeSingleQuestionSession(InterviewSession session, CreateInterviewRequest request) {
        String domainCode = resolveSingleDomainCode(request.getSingleQuestionDomainName());
        String domainName = StringUtils.hasText(request.getSingleQuestionDomainName())
                ? request.getSingleQuestionDomainName().trim()
                : "????";
        String questionType = StringUtils.hasText(request.getSingleQuestionType())
                ? request.getSingleQuestionType().trim()
                : "PRINCIPLE";
        String targetDepth = StringUtils.hasText(request.getSingleQuestionTargetDepth())
                ? request.getSingleQuestionTargetDepth().trim()
                : "L2";
        int maxQuestions = request.getMaxQuestions() != null && request.getMaxQuestions() > 0
                ? request.getMaxQuestions() : 1;

        InterviewQuestion question = new InterviewQuestion();
        question.setSessionId(session.getId());
        question.setQuestionNo(1);
        question.setQuestionType(questionType);
        question.setDomainId(null);
        question.setStem(request.getSingleQuestionStem().trim());
        question.setTargetSkill(domainName);
        question.setExpectedPoints(request.getSingleQuestionExpectedPoints() != null
                ? request.getSingleQuestionExpectedPoints() : List.of());
        question.setTargetDepth(targetDepth);
        question.setStatus("asked");

        Map<String, Object> generationCtx = new LinkedHashMap<>();
        generationCtx.put("domainCode", domainCode);
        generationCtx.put("questionType", questionType);
        generationCtx.put("targetDepth", targetDepth);
        question.setGenerationContextJson(generationCtx);

        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.insert(question);

        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("domainId", null);
        domain.put("domainCode", domainCode);
        domain.put("domainName", domainName);
        domain.put("targetDepth", targetDepth);
        domain.put("priority", "high");

        Map<String, Object> syllabus = new LinkedHashMap<>();
        syllabus.put("domains", List.of(domain));
        syllabus.put("questionMixPlan", Map.of(questionType, 1));
        syllabus.put("projects", List.of());

        Map<String, Object> domainState = new LinkedHashMap<>();
        domainState.put("domain_id", domainCode);
        domainState.put("current_depth", "L1");
        domainState.put("status", "UNASKED");
        domainState.put("saturated", false);
        domainState.put("evidence_refs", new ArrayList<>());

        Map<String, Object> questionMixProgress = new LinkedHashMap<>();
        questionMixProgress.put(questionType, 0);

        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("domain_states", List.of(domainState));
        ledger.put("question_mix_progress", questionMixProgress);
        ledger.put("asked_total", 0);
        ledger.put("last_attempt_id", null);
        ledger.put("max_questions", maxQuestions);
        ledger.put("single_question_mode", true);

        Map<String, Object> firstQuestionSnapshot = new LinkedHashMap<>();
        firstQuestionSnapshot.put("questionId", question.getId());
        firstQuestionSnapshot.put("questionNo", 1);
        firstQuestionSnapshot.put("questionType", questionType);
        firstQuestionSnapshot.put("domainId", null);
        firstQuestionSnapshot.put("domainName", domainName);
        firstQuestionSnapshot.put("stem", question.getStem());
        firstQuestionSnapshot.put("targetSkill", question.getTargetSkill());
        firstQuestionSnapshot.put("targetDepth", targetDepth);
        firstQuestionSnapshot.put("aiResultStatus", "success");
        firstQuestionSnapshot.put("hintAvailable", true);

        InterviewSession update = new InterviewSession();
        update.setId(session.getId());
        update.setSyllabusJson(syllabus);
        update.setStateLedgerJson(ledger);
        update.setFirstQuestionJson(firstQuestionSnapshot);
        update.setCurrentQuestionNo(1);
        update.setStatus("in_progress");
        update.setStartedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);
    }

    private String resolveSingleDomainCode(String domainName) {
        if (!StringUtils.hasText(domainName)) {
            return "single_question";
        }
        String normalized = domainName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "single_question" : "single_" + normalized;
    }

    private void validateEnums(CreateInterviewRequest request) {
        try {
            TargetRole.valueOf(request.getTargetRole());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("targetRole 不合法：" + request.getTargetRole());
        }
        try {
            ExperienceLevel.valueOf(request.getExperienceLevel());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("experienceLevel 不合法：" + request.getExperienceLevel());
        }
        try {
            InterviewMode.valueOf(request.getMode());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode 不合法：" + request.getMode());
        }
    }

    private InterviewSession buildSession(Long userId, CreateInterviewRequest request) {
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(request.getResumeId());
        session.setTargetRole(request.getTargetRole());
        session.setExperienceLevel(request.getExperienceLevel());
        session.setMode(request.getMode());
        session.setJobDescription(request.getJobDescription());
        session.setFocusTopics(request.getFocusTopics());
        session.setThinkTimeLimitSeconds(request.getThinkTimeLimitSeconds());
        session.setAnswerTimeLimitSeconds(request.getAnswerTimeLimitSeconds());
        session.setPositionDomainVersion(1);
        session.setCurrentQuestionNo(0);
        session.setContextWindowSize(5);
        session.setStatus("planning");
        session.setModelProvider(mockEnabled ? "mock" : "openai");
        session.setModelName(aiModel);
        // 生成标题：岗位中文名 + 模拟面试 + 日期
        session.setTitle(resolvePositionName(request.getTargetRole()) + " 模拟面试 - " + LocalDate.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    private void savePreference(Long userId, CreateInterviewRequest request) {
        InterviewPreference pref = new InterviewPreference();
        pref.setTargetRole(request.getTargetRole());
        pref.setExperienceLevel(request.getExperienceLevel());
        pref.setMode(request.getMode());
        pref.setFocusTopics(request.getFocusTopics());
        pref.setThinkTimeLimitSeconds(request.getThinkTimeLimitSeconds());
        pref.setAnswerTimeLimitSeconds(request.getAnswerTimeLimitSeconds());
        preferenceService.saveOrUpdate(userId, pref);
    }

    private SyllabusSummaryDto buildSyllabusSummary(InterviewSession session) {
        if (session.getSyllabusJson() == null) {
            return new SyllabusSummaryDto(List.of());
        }
        try {
            // 从 syllabus_json 中提取 domains[].domainName 列表
            Object domainsObj = session.getSyllabusJson().get("domains");
            if (domainsObj instanceof List<?> domainsList) {
                List<String> domainNames = domainsList.stream()
                        .filter(d -> d instanceof Map)
                        .map(d -> (Map<?, ?>) d)
                        .map(d -> (String) d.get("domainName"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                return new SyllabusSummaryDto(domainNames);
            }
        } catch (Exception e) {
            log.warn("解析 syllabus_json 失败, sessionId={}", session.getId(), e);
        }
        return new SyllabusSummaryDto(List.of());
    }

    @SuppressWarnings("unchecked")
    private QuestionDto mapToQuestionDto(Map<String, Object> snapshot) {
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(toLong(snapshot.get("questionId")));
        dto.setQuestionNo(toInt(snapshot.get("questionNo")));
        dto.setQuestionType((String) snapshot.get("questionType"));
        dto.setDomainId(toLong(snapshot.get("domainId")));
        dto.setDomainName((String) snapshot.get("domainName"));
        dto.setStem((String) snapshot.get("stem"));
        dto.setTargetSkill((String) snapshot.get("targetSkill"));
        dto.setTargetDepth((String) snapshot.get("targetDepth"));
        Object aiResultStatus = snapshot.get("aiResultStatus");
        dto.setAiResultStatus(aiResultStatus instanceof String ? (String) aiResultStatus : null);
        Object hintAvailable = snapshot.get("hintAvailable");
        dto.setHintAvailable(hintAvailable instanceof Boolean ? (Boolean) hintAvailable : true);
        return dto;
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return null; }
    }

    private String resolvePositionName(String targetRole) {
        return switch (targetRole) {
            case "JAVA_BACKEND" -> "Java 后端开发";
            case "GO_BACKEND" -> "Go 后端开发";
            case "FRONTEND" -> "前端开发";
            case "DATA_ENGINEER" -> "数据工程师";
            case "QA" -> "测试工程师";
            case "DEVOPS" -> "DevOps 工程师";
            default -> targetRole;
        };
    }

    /**
     * 获取面试会话的状态账本（调试用）。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID（用于权限校验）
     * @return 状态账本 JSON
     */
    public Object getStateLedger(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }
        return session.getStateLedgerJson();
    }

}
