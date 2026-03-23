package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PlannerHistoryBuilderService tests")
class PlannerHistoryBuilderServiceTest {

    @Test
    @DisplayName("should query recent same-role sessions and map covered points into history items")
    void buildRecentHistory_shouldUseRecentSameRoleSessions() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        PlannerHistoryBuilderService service = new PlannerHistoryBuilderService(sessionMapper);

        InterviewSession currentSession = new InterviewSession();
        currentSession.setId(70L);
        currentSession.setUserId(1L);
        currentSession.setTargetRole("JAVA_BACKEND");

        InterviewSession historySession = new InterviewSession();
        historySession.setId(69L);
        historySession.setStatus("completed");
        historySession.setFinishedAt(LocalDateTime.of(2026, 3, 20, 12, 0));
        historySession.setUpdatedAt(LocalDateTime.of(2026, 3, 20, 12, 5));
        historySession.setCreatedAt(LocalDateTime.of(2026, 3, 20, 11, 0));
        historySession.setStateLedgerJson(new LinkedHashMap<>(Map.of(
                "covered_points", List.of("Redis / 缓存击穿", "MySQL / 索引优化")
        )));

        LocalDateTime now = LocalDateTime.of(2026, 3, 23, 10, 0);
        LocalDateTime expectedDateFrom = now.minusDays(30);
        when(sessionMapper.selectPlannerRecentSessions(
                1L,
                "JAVA_BACKEND",
                List.of("completed", "report_generating"),
                expectedDateFrom,
                70L,
                3
        )).thenReturn(List.of(historySession));

        List<PlannerInput.HistoryInterviewItem> result = service.buildRecentHistory(currentSession, now);

        verify(sessionMapper).selectPlannerRecentSessions(
                1L,
                "JAVA_BACKEND",
                List.of("completed", "report_generating"),
                expectedDateFrom,
                70L,
                3
        );
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoundType()).isEmpty();
        assertThat(result.get(0).getInterviewAt()).isEqualTo("2026-03-20T12:00");
        assertThat(result.get(0).getCoveredKnowledgePoints()).containsExactly("Redis / 缓存击穿", "MySQL / 索引优化");
        assertThat(result.get(0).getDiscussedItems()).isEmpty();
        assertThat(result.get(0).getStrongPoints()).isEmpty();
        assertThat(result.get(0).getWeakPoints()).isEmpty();
    }
}
