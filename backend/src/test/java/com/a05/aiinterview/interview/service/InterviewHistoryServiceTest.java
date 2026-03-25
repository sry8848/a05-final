package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.dto.InterviewHistoryItemDto;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewHistoryServiceTest {

    @Test
    void list_shouldNormalizePagingAndSort() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, reportMapper, statusService);

        InterviewHistoryItemDto item = new InterviewHistoryItemDto();
        item.setSessionId(100L);
        item.setOverallScore(BigDecimal.valueOf(70));

        when(mapper.countHistory(eq(7L), eq("completed"), eq("JAVA_BACKEND"), any(), any()))
                .thenReturn(1L);
        when(mapper.selectHistoryPage(eq(7L), eq("completed"), eq("JAVA_BACKEND"), any(), any(),
                eq("createdAt"), eq("desc"), eq(0), eq(1)))
                .thenReturn(List.of(item));
        InterviewReport report = new InterviewReport();
        report.setSessionId(100L);
        report.setOverallScore(BigDecimal.valueOf(70));
        when(reportMapper.selectList(any())).thenReturn(List.of(report));
        when(statusService.resolveAndSync(100L)).thenReturn("completed");
        when(statusService.toDisplayStatus("completed")).thenReturn("ready");

        InterviewHistoryPageDto page = service.list(
                7L, 0, 999,
                "completed",
                "JAVA_BACKEND",
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now(),
                "score",
                "asc"
        );

        assertEquals(1, page.getPage());
        assertEquals(100, page.getPageSize());
        assertEquals(1, page.getTotal());
        assertEquals(100L, page.getItems().get(0).getSessionId());
        assertEquals(BigDecimal.valueOf(70), page.getItems().get(0).getOverallScore());
    }

    @Test
    void list_shouldFallbackToCreatedAtDescForUnknownSort() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, reportMapper, statusService);

        when(mapper.countHistory(eq(1L), eq(null), eq(null), eq(null), eq(null))).thenReturn(0L);
        when(mapper.selectHistoryPage(eq(1L), eq(null), eq(null), eq(null), eq(null),
                eq("createdAt"), eq("desc"), eq(10), eq(10)))
                .thenReturn(List.of());

        service.list(1L, 2, 10, "", "", null, null, "unknown", "down");

        ArgumentCaptor<String> sortByCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sortOrderCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).selectHistoryPage(eq(1L), eq(null), eq(null), eq(null), eq(null),
                sortByCaptor.capture(), sortOrderCaptor.capture(), eq(10), eq(10));
        assertEquals("createdAt", sortByCaptor.getValue());
        assertEquals("desc", sortOrderCaptor.getValue());
    }

    @Test
    void list_shouldMapSessionStatusToDisplayStatus() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, reportMapper, statusService);

        InterviewHistoryItemDto completed = new InterviewHistoryItemDto();
        completed.setSessionId(1L);
        completed.setStatus("completed");
        InterviewHistoryItemDto generating = new InterviewHistoryItemDto();
        generating.setSessionId(2L);
        generating.setStatus("report_generating");
        InterviewHistoryItemDto failed = new InterviewHistoryItemDto();
        failed.setSessionId(3L);
        failed.setStatus("aborted");

        when(mapper.countHistory(eq(9L), eq(null), eq(null), eq(null), eq(null))).thenReturn(3L);
        when(mapper.selectHistoryPage(eq(9L), eq(null), eq(null), eq(null), eq(null),
                eq("createdAt"), eq("desc"), eq(0), eq(10)))
                .thenReturn(List.of(completed, generating, failed));
        when(statusService.resolveAndSync(1L)).thenReturn("completed");
        when(statusService.resolveAndSync(2L)).thenReturn("report_generating");
        when(statusService.resolveAndSync(3L)).thenReturn("aborted");
        when(statusService.toDisplayStatus("completed")).thenReturn("ready");
        when(statusService.toDisplayStatus("report_generating")).thenReturn("generating");
        when(statusService.toDisplayStatus("aborted")).thenReturn("failed");

        InterviewHistoryPageDto page = service.list(9L, 1, 10, null, null, null, null, null, null);

        assertEquals("ready", page.getItems().get(0).getStatus());
        assertEquals("generating", page.getItems().get(1).getStatus());
        assertEquals("failed", page.getItems().get(2).getStatus());
    }

    @Test
    void list_shouldSortByEffectiveOverallScoreInsteadOfStoredColumn() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, reportMapper, statusService);

        InterviewHistoryItemDto lowerStored = new InterviewHistoryItemDto();
        lowerStored.setSessionId(1L);
        lowerStored.setOverallScore(BigDecimal.valueOf(20));
        lowerStored.setCreatedAt(LocalDateTime.of(2026, 3, 20, 10, 0));
        InterviewHistoryItemDto higherStored = new InterviewHistoryItemDto();
        higherStored.setSessionId(2L);
        higherStored.setOverallScore(BigDecimal.valueOf(30));
        higherStored.setCreatedAt(LocalDateTime.of(2026, 3, 21, 10, 0));

        when(mapper.countHistory(eq(9L), eq(null), eq(null), eq(null), eq(null))).thenReturn(2L);
        when(mapper.selectHistoryPage(eq(9L), eq(null), eq(null), eq(null), eq(null),
                eq("createdAt"), eq("desc"), eq(0), eq(2)))
                .thenReturn(List.of(higherStored, lowerStored));
        when(statusService.resolveAndSync(1L)).thenReturn("completed");
        when(statusService.resolveAndSync(2L)).thenReturn("completed");
        when(statusService.toDisplayStatus("completed")).thenReturn("ready");

        InterviewReport report1 = new InterviewReport();
        report1.setSessionId(1L);
        report1.setOverallScore(BigDecimal.valueOf(20));
        report1.setComprehensiveRadarScores(Map.of("dimensions", List.of(
                Map.of("dimensionKey", "fundamentals", "dimensionName", "基础原理掌握", "score", 80),
                Map.of("dimensionKey", "engineering_practice", "dimensionName", "工程实践与项目落地", "score", 70),
                Map.of("dimensionKey", "scenario_tradeoff", "dimensionName", "场景分析与方案取舍", "score", 60),
                Map.of("dimensionKey", "debugging", "dimensionName", "问题定位与排查思路", "score", 90),
                Map.of("dimensionKey", "communication", "dimensionName", "沟通表达与结构化呈现", "score", 100)
        )));
        InterviewReport report2 = new InterviewReport();
        report2.setSessionId(2L);
        report2.setOverallScore(BigDecimal.valueOf(30));
        when(reportMapper.selectList(any())).thenReturn(List.of(report1, report2));

        InterviewHistoryPageDto page = service.list(9L, 1, 10, null, null, null, null, "overallScore", "desc");

        assertEquals(1L, page.getItems().get(0).getSessionId());
        assertEquals(BigDecimal.valueOf(79.0), page.getItems().get(0).getOverallScore());
        assertEquals(2L, page.getItems().get(1).getSessionId());
    }
}
