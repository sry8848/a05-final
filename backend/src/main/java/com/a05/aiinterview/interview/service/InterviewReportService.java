package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewReportDto;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.engine.ReportGenerationService;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.service.support.AttemptEvaluationReader;
import com.a05.aiinterview.interview.service.support.InterviewOverallScoreSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面试报告应用服务。
 * 提供"手动结束面试"和"查询报告"两个能力，作为 Controller 和 ReportGenerationService 之间的协调层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewReportService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final ReportGenerationService reportGenerationService;
    private final InterviewSessionStatusService interviewSessionStatusService;

    /**
     * 手动结束面试并触发异步报告生成。
     * 用于候选人主动放弃或超时场景。
     *
     * <p>幂等设计：若会话状态已是 report_generating 或 completed，则只触发一次报告生成（由 ReportGenerationService 内部幂等保护）。
     *
     * @param sessionId 面试会话 ID
     * @param userId    当前登录用户 ID（用于归属校验）
     */
    public void finishAndGenerateReport(Long sessionId, Long userId) {
        log.info("手动结束面试, sessionId={}, userId={}", sessionId, userId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该面试会话");
        }

        // 只有 in_progress 和 planning 状态才允许手动结束
        String status = session.getStatus();
        if ("completed".equals(status) || "aborted".equals(status)) {
            throw new IllegalStateException("面试已结束，无法重复操作，当前状态: " + status);
        }

        // 若还不是 report_generating，先置状态
        if (!"report_generating".equals(status)) {
            interviewSessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                    .eq(InterviewSession::getId, sessionId)
                    .set(InterviewSession::getStatus, "report_generating")
                    .set(InterviewSession::getFinishedAt, LocalDateTime.now())
                    .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));
            log.info("面试会话状态 -> report_generating, sessionId={}", sessionId);
        }

        // 触发异步报告生成（@Async，立即返回）
        reportGenerationService.generateAsync(sessionId);
        log.info("报告生成任务已触发, sessionId={}", sessionId);
    }

    /**
     * 查询面试报告。
     * 报告生成中时返回占位响应（reportStatus=generating），就绪后返回完整报告。
     *
     * @param sessionId 面试会话 ID
     * @param userId    当前登录用户 ID（用于归属校验）
     * @return 报告 DTO
     */
    public InterviewReportDto getReport(Long sessionId, Long userId) {
        log.info("查询面试报告, sessionId={}, userId={}", sessionId, userId);

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试报告");
        }

        InterviewReport report = interviewReportMapper.selectBySessionId(sessionId);
        String effectiveStatus = interviewSessionStatusService.resolveAndSync(session);
        if (InterviewSessionStatusService.STATUS_ABORTED.equals(effectiveStatus)) {
            log.info("报告查询命中 failed 状态, sessionId={}", sessionId);
            return InterviewReportDto.failed(sessionId);
        }
        if (report == null || !InterviewSessionStatusService.STATUS_COMPLETED.equals(effectiveStatus)) {
            log.info("报告尚未就绪，返回 generating 状态, sessionId={}, effectiveStatus={}", sessionId, effectiveStatus);
            return InterviewReportDto.generating(sessionId);
        }

        log.info("报告已就绪, sessionId={}, overallScore={}", sessionId, report.getOverallScore());
        InterviewReportDto dto = InterviewReportDto.fromEntity(report);
        dto.setOverallScore(InterviewOverallScoreSupport.resolveOverallScore(report));
        dto.setMode(session.getMode());
        dto.setPositionCode(session.getPositionCode());
        if (!"professional".equalsIgnoreCase(session.getMode())) {
            dto.setComprehensiveRadarScores(null);
        }
        dto.setQuestions(buildQuestionSummaries(sessionId));
        return dto;
    }

    private List<InterviewReportDto.QuestionSummaryDto> buildQuestionSummaries(Long sessionId) {
        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)
        );
        if (questions.isEmpty()) {
            return List.of();
        }

        List<InterviewAttempt> attempts = interviewAttemptMapper.selectBySessionId(sessionId);
        Map<Long, List<InterviewAttempt>> attemptsByQuestion = attempts == null
                ? Collections.emptyMap()
                : attempts.stream().collect(Collectors.groupingBy(InterviewAttempt::getQuestionId));

        return questions.stream()
                .map(question -> buildQuestionSummary(question, attemptsByQuestion.get(question.getId())))
                .toList();
    }

    private InterviewReportDto.QuestionSummaryDto buildQuestionSummary(
            InterviewQuestion question,
            List<InterviewAttempt> attemptsForQuestion) {
        InterviewReportDto.QuestionSummaryDto summaryDto = new InterviewReportDto.QuestionSummaryDto();
        summaryDto.setQuestionId(question.getId());
        summaryDto.setQuestionNo(question.getQuestionNo());
        summaryDto.setQuestionStem(question.getStem());

        String status = "pending";
        BigDecimal score = null;
        String commentary = null;
        var latestFinalAttempt = AttemptEvaluationReader.selectLatestFinalAttempt(attemptsForQuestion);
        if (latestFinalAttempt.isPresent()) {
            InterviewAttempt attempt = latestFinalAttempt.get();
            status = "[skip]".equals(attempt.getAnswerText()) ? "skipped" : "answered";
            score = AttemptEvaluationReader.readScore(attempt.getDetailEvaluationJson());
            commentary = AttemptEvaluationReader.readCommentary(attempt.getDetailEvaluationJson());
        }

        summaryDto.setStatus(status);
        summaryDto.setScore(score);
        summaryDto.setCommentary(commentary);
        return summaryDto;
    }
}
