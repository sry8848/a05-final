package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.common.enums.ExperienceLevel;
import com.a05.aiinterview.common.enums.InterviewMode;
import com.a05.aiinterview.common.enums.TargetRole;
import com.a05.aiinterview.interview.dto.CreateInterviewRequest;
import com.a05.aiinterview.interview.dto.CreateInterviewResponse;
import com.a05.aiinterview.interview.dto.InterviewDetailDto;
import com.a05.aiinterview.interview.dto.InterviewSyllabus;
import com.a05.aiinterview.interview.dto.QuestionDtoAssembler;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        plannerOrchestrationService.runAsync(session.getId());
        log.info("Planner 异步任务已触发, sessionId={}", session.getId());
        return new CreateInterviewResponse(session.getId(), session.getStatus());
    }

    /**
     * 获取面试会话详情，供加载页轮询使用。
     * 当 status=in_progress 时，响应中内嵌当前题（currentQuestion 字段）。
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

        if ("in_progress".equals(session.getStatus())) {
            dto.setCurrentQuestion(loadCurrentQuestion(session));
        }

        log.info("查询面试会话详情完成, sessionId={}, status={}", sessionId, session.getStatus());
        return dto;
    }

    // ==================== 私有方法 ====================
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

    private QuestionDto loadCurrentQuestion(InterviewSession session) {
        Integer currentQuestionNo = session.getCurrentQuestionNo();
        if (currentQuestionNo != null && currentQuestionNo > 0) {
            InterviewQuestion currentQuestion = findQuestionByNo(session.getId(), currentQuestionNo);
            if (currentQuestion != null) {
                return QuestionDtoAssembler.fromQuestion(currentQuestion, session);
            }
            if (currentQuestionNo == 1 && session.getFirstQuestionJson() != null) {
                return QuestionDtoAssembler.fromSnapshot(session.getFirstQuestionJson());
            }
        }

        InterviewQuestion latestQuestion = findLatestQuestion(session.getId());
        if (latestQuestion != null) {
            log.warn("当前题记录缺失，回退最新题, sessionId={}, currentQuestionNo={}, fallbackQuestionNo={}",
                    session.getId(), currentQuestionNo, latestQuestion.getQuestionNo());
            return QuestionDtoAssembler.fromQuestion(latestQuestion, session);
        }

        if (currentQuestionNo != null && currentQuestionNo == 1 && session.getFirstQuestionJson() != null) {
            return QuestionDtoAssembler.fromSnapshot(session.getFirstQuestionJson());
        }

        log.error("当前题快照缺失, sessionId={}, currentQuestionNo={}", session.getId(), currentQuestionNo);
        return null;
    }

    private InterviewQuestion findQuestionByNo(Long sessionId, Integer questionNo) {
        if (sessionId == null || questionNo == null || questionNo <= 0) {
            return null;
        }
        return interviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .eq(InterviewQuestion::getQuestionNo, questionNo)
                        .last("LIMIT 1")
        );
    }

    private InterviewQuestion findLatestQuestion(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return interviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByDesc(InterviewQuestion::getQuestionNo)
                        .last("LIMIT 1")
        );
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
