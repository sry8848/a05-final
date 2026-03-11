package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewReportDto;
import com.a05.aiinterview.interview.engine.ReportGenerationService;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
    private final ReportGenerationService reportGenerationService;

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
        if (report == null) {
            // 报告尚未生成，返回占位响应
            log.info("报告尚未就绪，返回 generating 状态, sessionId={}", sessionId);
            return InterviewReportDto.generating(sessionId);
        }

        log.info("报告已就绪, sessionId={}, overallScore={}", sessionId, report.getOverallScore());
        return InterviewReportDto.fromEntity(report);
    }
}
