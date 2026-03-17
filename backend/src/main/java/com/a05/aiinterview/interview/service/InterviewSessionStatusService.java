package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionStatusService {

    static final String STATUS_PLANNING = "planning";
    static final String STATUS_IN_PROGRESS = "in_progress";
    static final String STATUS_REPORT_GENERATING = "report_generating";
    static final String STATUS_COMPLETED = "completed";
    static final String STATUS_ABORTED = "aborted";

    private static final Duration REPORT_TIMEOUT = Duration.ofMinutes(10);

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;

    public String resolveAndSync(Long sessionId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            return null;
        }
        return resolveAndSync(session);
    }

    public String resolveAndSync(InterviewSession session) {
        if (session == null || session.getId() == null) {
            return null;
        }

        String current = normalizeSessionStatus(session.getStatus());
        if (STATUS_IN_PROGRESS.equals(current) && hasInProgressTimedOut(session)) {
            updateSessionStatus(session.getId(), STATUS_ABORTED);
            return STATUS_ABORTED;
        }
        if (STATUS_REPORT_GENERATING.equals(current) && hasReportTimedOut(session)) {
            updateSessionStatus(session.getId(), STATUS_ABORTED);
            return STATUS_ABORTED;
        }

        InterviewReport report = interviewReportMapper.selectBySessionId(session.getId());
        if (report == null) {
            return current;
        }

        List<InterviewAttempt> attempts = interviewAttemptMapper.selectBySessionId(session.getId());
        boolean hasFinalFailed = attempts.stream()
                .filter(this::isFinalAttempt)
                .map(InterviewAttempt::getDetailEvaluationStatus)
                .map(this::normalizeDetailStatus)
                .anyMatch("failed"::equals);
        if (hasFinalFailed) {
            updateSessionStatus(session.getId(), STATUS_ABORTED);
            return STATUS_ABORTED;
        }

        boolean hasFinalPending = attempts.stream()
                .filter(this::isFinalAttempt)
                .map(InterviewAttempt::getDetailEvaluationStatus)
                .map(this::normalizeDetailStatus)
                .anyMatch(status -> !"ready".equals(status));
        String target = hasFinalPending ? STATUS_REPORT_GENERATING : STATUS_COMPLETED;
        if (!target.equals(current)) {
            updateSessionStatus(session.getId(), target);
        }
        return target;
    }

    public String toDisplayStatus(String sessionStatus) {
        String normalized = normalizeSessionStatus(sessionStatus);
        if (STATUS_COMPLETED.equals(normalized)) {
            return "ready";
        }
        if (STATUS_ABORTED.equals(normalized)) {
            return "failed";
        }
        return "generating";
    }

    public String toReportStatus(String sessionStatus) {
        return toDisplayStatus(sessionStatus);
    }

    private boolean isFinalAttempt(InterviewAttempt attempt) {
        return attempt != null && Boolean.TRUE.equals(attempt.getIsFinal());
    }

    private boolean hasReportTimedOut(InterviewSession session) {
        LocalDateTime baseTime = session.getFinishedAt();
        if (baseTime == null) {
            baseTime = session.getUpdatedAt();
        }
        if (baseTime == null) {
            baseTime = session.getCreatedAt();
        }
        if (baseTime == null) {
            return false;
        }
        return Duration.between(baseTime, LocalDateTime.now()).compareTo(REPORT_TIMEOUT) >= 0;
    }

    private boolean hasInProgressTimedOut(InterviewSession session) {
        LocalDateTime baseTime = session.getUpdatedAt();
        if (baseTime == null) {
            baseTime = session.getStartedAt();
        }
        if (baseTime == null) {
            baseTime = session.getCreatedAt();
        }
        if (baseTime == null) {
            return false;
        }
        return Duration.between(baseTime, LocalDateTime.now()).compareTo(REPORT_TIMEOUT) >= 0;
    }

    private void updateSessionStatus(Long sessionId, String targetStatus) {
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStatus(targetStatus);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);
        log.info("同步面试会话状态, sessionId={}, status={}", sessionId, targetStatus);
    }

    private String normalizeSessionStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PLANNING;
        }
        String normalized = status.trim().toLowerCase();
        return switch (normalized) {
            case STATUS_IN_PROGRESS, STATUS_REPORT_GENERATING, STATUS_COMPLETED, STATUS_ABORTED -> normalized;
            default -> STATUS_PLANNING;
        };
    }

    private String normalizeDetailStatus(String status) {
        if (status == null || status.isBlank()) {
            return "pending";
        }
        String normalized = status.trim().toLowerCase();
        return switch (normalized) {
            case "ready", "failed", "generating" -> normalized;
            default -> "pending";
        };
    }
}
