package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewHistoryItemDto;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

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
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, statusService);

        InterviewHistoryItemDto item = new InterviewHistoryItemDto();
        item.setSessionId(100L);

        when(mapper.countHistory(eq(7L), eq("completed"), eq("JAVA_BACKEND"), any(), any()))
                .thenReturn(1L);
        when(mapper.selectHistoryPage(eq(7L), eq("completed"), eq("JAVA_BACKEND"), any(), any(),
                eq("overallScore"), eq("asc"), eq(0), eq(100)))
                .thenReturn(List.of(item));
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
    }

    @Test
    void list_shouldFallbackToCreatedAtDescForUnknownSort() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, statusService);

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
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewHistoryService service = new InterviewHistoryService(mapper, statusService);

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
}
