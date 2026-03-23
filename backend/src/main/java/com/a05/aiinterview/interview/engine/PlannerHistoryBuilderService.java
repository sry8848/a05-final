package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlannerHistoryBuilderService {

    static final int RECENT_HISTORY_LIMIT = 3;
    static final int RECENT_HISTORY_DAYS = 30;
    private static final List<String> INCLUDED_STATUSES = List.of("completed", "report_generating");

    private final InterviewSessionMapper interviewSessionMapper;

    public List<PlannerInput.HistoryInterviewItem> buildRecentHistory(InterviewSession currentSession) {
        return buildRecentHistory(currentSession, LocalDateTime.now());
    }

    List<PlannerInput.HistoryInterviewItem> buildRecentHistory(InterviewSession currentSession, LocalDateTime now) {
        if (currentSession == null
                || currentSession.getUserId() == null
                || currentSession.getTargetRole() == null
                || currentSession.getTargetRole().isBlank()) {
            return List.of();
        }

        List<InterviewSession> historySessions = interviewSessionMapper.selectPlannerRecentSessions(
                currentSession.getUserId(),
                currentSession.getTargetRole(),
                INCLUDED_STATUSES,
                now.minusDays(RECENT_HISTORY_DAYS),
                currentSession.getId(),
                RECENT_HISTORY_LIMIT
        );
        if (historySessions == null || historySessions.isEmpty()) {
            return List.of();
        }

        return historySessions.stream()
                .filter(Objects::nonNull)
                .map(this::toHistoryInterviewItem)
                .toList();
    }

    private PlannerInput.HistoryInterviewItem toHistoryInterviewItem(InterviewSession session) {
        return PlannerInput.HistoryInterviewItem.builder()
                .roundType("")
                .interviewAt(resolveInterviewAt(session))
                .coveredKnowledgePoints(extractCoveredKnowledgePoints(session.getStateLedgerJson()))
                .discussedItems(List.of())
                .strongPoints(List.of())
                .weakPoints(List.of())
                .build();
    }

    private String resolveInterviewAt(InterviewSession session) {
        LocalDateTime timestamp = session.getFinishedAt() != null
                ? session.getFinishedAt()
                : session.getUpdatedAt() != null
                ? session.getUpdatedAt()
                : session.getCreatedAt();
        return timestamp == null ? "" : timestamp.toString();
    }

    private List<String> extractCoveredKnowledgePoints(Map<String, Object> stateLedgerJson) {
        if (stateLedgerJson == null) {
            return List.of();
        }
        Object rawCoveredPoints = stateLedgerJson.get("covered_points");
        if (!(rawCoveredPoints instanceof List<?> coveredPoints)) {
            return List.of();
        }
        return coveredPoints.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
