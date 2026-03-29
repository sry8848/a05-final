package com.a05.aiinterview.admin.dashboard;

import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardModelStatusDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardOverviewDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardPromptSummaryDto;
import com.a05.aiinterview.admin.dashboard.dto.AdminDashboardTrendsDto;
import com.a05.aiinterview.admin.dashboard.query.AdminDashboardQueryMapper;
import com.a05.aiinterview.admin.dashboard.query.DailyMetricRow;
import com.a05.aiinterview.admin.dashboard.query.ModelLatencySampleRow;
import com.a05.aiinterview.admin.dashboard.query.ModelStatusRow;
import com.a05.aiinterview.admin.dashboard.query.PromptUsageRow;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.prompt.PromptCode;
import com.a05.aiinterview.ai.prompt.PromptTemplateMetadata;
import com.a05.aiinterview.ai.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void getOverview_shouldUseShanghaiNaturalDayBoundaries() {
        AdminDashboardQueryMapper queryMapper = mock(AdminDashboardQueryMapper.class);
        PromptProperties promptProperties = mock(PromptProperties.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        Clock clock = fixedClock("2026-03-22T11:30:00+08:00");
        AdminDashboardService service = new AdminDashboardService(queryMapper, promptProperties, promptTemplateService, clock);

        when(queryMapper.countTotalUsers()).thenReturn(120L);
        when(queryMapper.countNewUsersBetween(any(), any())).thenReturn(8L);
        when(queryMapper.countTotalInterviews()).thenReturn(86L);
        when(queryMapper.countInterviewsBetween(any(), any())).thenReturn(13L);
        when(queryMapper.countActiveInterviews()).thenReturn(5L);
        when(queryMapper.sumTokensBetween(any(), any())).thenReturn(8096L);

        AdminDashboardOverviewDto dto = service.getOverview();

        assertThat(dto.getTotalUsers()).isEqualTo(120L);
        assertThat(dto.getNewUsersToday()).isEqualTo(8L);
        assertThat(dto.getTotalInterviews()).isEqualTo(86L);
        assertThat(dto.getInterviewsToday()).isEqualTo(13L);
        assertThat(dto.getActiveInterviews()).isEqualTo(5L);
        assertThat(dto.getTotalTokensToday()).isEqualTo(8096L);

        LocalDateTime expectedStart = LocalDateTime.of(2026, 3, 22, 0, 0);
        LocalDateTime expectedEnd = LocalDateTime.of(2026, 3, 23, 0, 0);
        verify(queryMapper).countNewUsersBetween(expectedStart, expectedEnd);
        verify(queryMapper).countInterviewsBetween(expectedStart, expectedEnd);
        verify(queryMapper).sumTokensBetween(expectedStart, expectedEnd);
    }

    @Test
    void getTrends_shouldBackfillMissingDatesWithZero() {
        AdminDashboardQueryMapper queryMapper = mock(AdminDashboardQueryMapper.class);
        PromptProperties promptProperties = mock(PromptProperties.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        Clock clock = fixedClock("2026-03-22T11:30:00+08:00");
        AdminDashboardService service = new AdminDashboardService(queryMapper, promptProperties, promptTemplateService, clock);

        when(queryMapper.selectDailyNewUsers(any(), any())).thenReturn(List.of(
                new DailyMetricRow(LocalDate.of(2026, 3, 20), 3L),
                new DailyMetricRow(LocalDate.of(2026, 3, 22), 5L)
        ));
        when(queryMapper.selectDailyInterviews(any(), any())).thenReturn(List.of(
                new DailyMetricRow(LocalDate.of(2026, 3, 16), 2L),
                new DailyMetricRow(LocalDate.of(2026, 3, 21), 6L)
        ));
        when(queryMapper.selectDailyTokens(any(), any())).thenReturn(List.of(
                new DailyMetricRow(LocalDate.of(2026, 3, 17), 200L),
                new DailyMetricRow(LocalDate.of(2026, 3, 22), 900L)
        ));

        AdminDashboardTrendsDto dto = service.getTrends(7);

        assertThat(dto.getDates()).containsExactly("03-16", "03-17", "03-18", "03-19", "03-20", "03-21", "03-22");
        assertThat(dto.getNewUsers()).containsExactly(0L, 0L, 0L, 0L, 3L, 0L, 5L);
        assertThat(dto.getInterviews()).containsExactly(2L, 0L, 0L, 0L, 0L, 6L, 0L);
        assertThat(dto.getTotalTokens()).containsExactly(0L, 200L, 0L, 0L, 0L, 0L, 900L);
    }

    @Test
    void getModels_shouldCalculateSuccessRateStatusAndP95() {
        AdminDashboardQueryMapper queryMapper = mock(AdminDashboardQueryMapper.class);
        PromptProperties promptProperties = mock(PromptProperties.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        Clock clock = fixedClock("2026-03-22T11:30:00+08:00");
        AdminDashboardService service = new AdminDashboardService(queryMapper, promptProperties, promptTemplateService, clock);

        when(queryMapper.selectModelStatuses(any(), any())).thenReturn(List.of(
                new ModelStatusRow("openai", "gpt-4o-mini", 100L, 99L, 1L, 1400L),
                new ModelStatusRow("openai", "gpt-4o", 100L, 96L, 4L, 2400L),
                new ModelStatusRow("unknown", "unknown", 100L, 80L, 20L, 3200L)
        ));
        when(queryMapper.selectModelLatencySamples(any(), any())).thenReturn(List.of(
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 100L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 200L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 300L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 400L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 500L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 600L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 700L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 800L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 900L),
                new ModelLatencySampleRow("openai", "gpt-4o-mini", 1000L),
                new ModelLatencySampleRow("openai", "gpt-4o", 2400L),
                new ModelLatencySampleRow("unknown", "unknown", 3200L)
        ));

        List<AdminDashboardModelStatusDto> list = service.getModels(15);

        assertThat(list).hasSize(3);
        assertThat(list.get(0).getModelProvider()).isEqualTo("openai");
        assertThat(list.get(0).getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(list.get(0).getSuccessRate()).isEqualTo(0.99d);
        assertThat(list.get(0).getAvgLatencyMs()).isEqualTo(1400L);
        assertThat(list.get(0).getP95LatencyMs()).isEqualTo(1000L);
        assertThat(list.get(0).getStatus()).isEqualTo("healthy");

        assertThat(list.get(1).getSuccessRate()).isEqualTo(0.96d);
        assertThat(list.get(1).getStatus()).isEqualTo("warning");

        assertThat(list.get(2).getSuccessRate()).isEqualTo(0.8d);
        assertThat(list.get(2).getStatus()).isEqualTo("error");
    }

    @Test
    void getModels_shouldReturnEmptyListWhenWindowHasNoTraffic() {
        AdminDashboardQueryMapper queryMapper = mock(AdminDashboardQueryMapper.class);
        PromptProperties promptProperties = mock(PromptProperties.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        Clock clock = fixedClock("2026-03-22T11:30:00+08:00");
        AdminDashboardService service = new AdminDashboardService(queryMapper, promptProperties, promptTemplateService, clock);

        when(queryMapper.selectModelStatuses(any(), any())).thenReturn(List.of());

        assertThat(service.getModels(15)).isEmpty();
    }

    @Test
    void getPrompts_shouldUseConfiguredVersionAsSourceOfTruthAndBackfillUsageDefaults() {
        AdminDashboardQueryMapper queryMapper = mock(AdminDashboardQueryMapper.class);
        PromptProperties promptProperties = mock(PromptProperties.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        Clock clock = fixedClock("2026-03-22T11:30:00+08:00");
        AdminDashboardService service = new AdminDashboardService(queryMapper, promptProperties, promptTemplateService, clock);

        when(promptProperties.asVersionMap()).thenReturn(Map.of(
                PromptCode.PLANNER, "v2",
                PromptCode.REPORT_GENERATION, "v1"
        ));
        when(promptTemplateService.loadMetadata("planner"))
                .thenReturn(new PromptTemplateMetadata("planner", "v2", "classpath:prompts/planner.md"));
        when(promptTemplateService.loadMetadata("report_generation"))
                .thenReturn(new PromptTemplateMetadata("report_generation", "v1", "classpath:prompts/report-generation.md"));
        when(queryMapper.selectPromptUsages(any(), any())).thenReturn(List.of(
                new PromptUsageRow("planner", 7L, LocalDateTime.of(2026, 3, 22, 9, 30)),
                new PromptUsageRow("unknown_prompt", 99L, LocalDateTime.of(2026, 3, 21, 10, 0))
        ));

        List<AdminDashboardPromptSummaryDto> list = service.getPrompts();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getPromptCode()).isEqualTo("planner");
        assertThat(list.get(0).getConfiguredVersion()).isEqualTo("v2");
        assertThat(list.get(0).getTemplateVersion()).isEqualTo("v2");
        assertThat(list.get(0).getSourcePath()).isEqualTo("classpath:prompts/planner.md");
        assertThat(list.get(0).getCallsLast24Hours()).isEqualTo(7L);
        assertThat(list.get(0).getLastUsedAt()).isEqualTo("2026-03-22T09:30:00+08:00");

        assertThat(list.get(1).getPromptCode()).isEqualTo("report_generation");
        assertThat(list.get(1).getConfiguredVersion()).isEqualTo("v1");
        assertThat(list.get(1).getTemplateVersion()).isEqualTo("v1");
        assertThat(list.get(1).getCallsLast24Hours()).isEqualTo(0L);
        assertThat(list.get(1).getLastUsedAt()).isNull();
    }

    private Clock fixedClock(String isoOffsetDateTime) {
        return Clock.fixed(OffsetDateTime.parse(isoOffsetDateTime).toInstant(), SHANGHAI);
    }
}
