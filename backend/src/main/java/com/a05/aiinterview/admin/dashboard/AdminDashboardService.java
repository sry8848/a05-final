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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter OFFSET_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AdminDashboardQueryMapper queryMapper;
    private final PromptProperties promptProperties;
    private final PromptTemplateService promptTemplateService;
    private final Clock clock;

    @Autowired
    public AdminDashboardService(
            AdminDashboardQueryMapper queryMapper,
            PromptProperties promptProperties,
            PromptTemplateService promptTemplateService
    ) {
        this(queryMapper, promptProperties, promptTemplateService, Clock.system(DASHBOARD_ZONE));
    }

    AdminDashboardService(
            AdminDashboardQueryMapper queryMapper,
            PromptProperties promptProperties,
            PromptTemplateService promptTemplateService,
            Clock clock
    ) {
        this.queryMapper = queryMapper;
        this.promptProperties = promptProperties;
        this.promptTemplateService = promptTemplateService;
        this.clock = clock;
    }

    public AdminDashboardOverviewDto getOverview() {
        LocalDateTime todayStart = today().atStartOfDay();
        LocalDateTime tomorrowStart = today().plusDays(1).atStartOfDay();
        return new AdminDashboardOverviewDto(
                safeLong(queryMapper.countTotalUsers()),
                safeLong(queryMapper.countNewUsersBetween(todayStart, tomorrowStart)),
                safeLong(queryMapper.countTotalInterviews()),
                safeLong(queryMapper.countInterviewsBetween(todayStart, tomorrowStart)),
                safeLong(queryMapper.countActiveInterviews()),
                safeLong(queryMapper.sumTokensBetween(todayStart, tomorrowStart))
        );
    }

    public AdminDashboardTrendsDto getTrends(int days) {
        int safeDays = Math.max(days, 1);
        LocalDate endDate = today();
        LocalDate startDate = endDate.minusDays(safeDays - 1L);
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> newUsersByDate = toMetricMap(queryMapper.selectDailyNewUsers(rangeStart, rangeEnd));
        Map<LocalDate, Long> interviewsByDate = toMetricMap(queryMapper.selectDailyInterviews(rangeStart, rangeEnd));
        Map<LocalDate, Long> tokensByDate = toMetricMap(queryMapper.selectDailyTokens(rangeStart, rangeEnd));

        List<String> dates = new ArrayList<>(safeDays);
        List<Long> newUsers = new ArrayList<>(safeDays);
        List<Long> interviews = new ArrayList<>(safeDays);
        List<Long> totalTokens = new ArrayList<>(safeDays);

        for (int i = 0; i < safeDays; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            dates.add(currentDate.format(DAY_LABEL_FORMATTER));
            newUsers.add(newUsersByDate.getOrDefault(currentDate, 0L));
            interviews.add(interviewsByDate.getOrDefault(currentDate, 0L));
            totalTokens.add(tokensByDate.getOrDefault(currentDate, 0L));
        }

        return new AdminDashboardTrendsDto(dates, newUsers, interviews, totalTokens);
    }

    public List<AdminDashboardModelStatusDto> getModels(int windowMinutes) {
        int safeWindowMinutes = Math.max(windowMinutes, 1);
        LocalDateTime rangeEnd = now();
        LocalDateTime rangeStart = rangeEnd.minusMinutes(safeWindowMinutes);

        List<ModelStatusRow> statusRows = queryMapper.selectModelStatuses(rangeStart, rangeEnd);
        if (statusRows == null || statusRows.isEmpty()) {
            return List.of();
        }

        Map<ModelKey, List<Long>> latencySamplesByModel = queryMapper.selectModelLatencySamples(rangeStart, rangeEnd)
                .stream()
                .filter(sample -> sample.getLatencyMs() != null)
                .collect(Collectors.groupingBy(
                        sample -> new ModelKey(sample.getModelProvider(), sample.getModelName()),
                        Collectors.mapping(ModelLatencySampleRow::getLatencyMs, Collectors.toList())
                ));

        return statusRows.stream()
                .map(row -> toModelStatusDto(row, latencySamplesByModel))
                .toList();
    }

    public List<AdminDashboardPromptSummaryDto> getPrompts() {
        LocalDateTime rangeEnd = now();
        LocalDateTime recentStart = rangeEnd.minusHours(24);
        Map<String, PromptUsageRow> usageByPromptCode = queryMapper.selectPromptUsages(recentStart, rangeEnd)
                .stream()
                .collect(Collectors.toMap(PromptUsageRow::getPromptCode, row -> row, (left, right) -> left, HashMap::new));

        return promptProperties.asVersionMap().entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .map(entry -> toPromptSummaryDto(entry.getKey(), entry.getValue(), usageByPromptCode.get(entry.getKey().code())))
                .toList();
    }

    private AdminDashboardModelStatusDto toModelStatusDto(
            ModelStatusRow row,
            Map<ModelKey, List<Long>> latencySamplesByModel
    ) {
        ModelKey modelKey = new ModelKey(row.getModelProvider(), row.getModelName());
        Long requestCount = safeLong(row.getRequestCount());
        Long successCount = safeLong(row.getSuccessCount());
        Long errorCount = safeLong(row.getErrorCount());
        Long avgLatencyMs = row.getAvgLatencyMs();
        Double successRate = calculateSuccessRate(successCount, requestCount);
        Long p95LatencyMs = calculateP95Latency(latencySamplesByModel.getOrDefault(modelKey, List.of()));

        return new AdminDashboardModelStatusDto(
                row.getModelProvider(),
                row.getModelName(),
                requestCount,
                successCount,
                errorCount,
                successRate,
                avgLatencyMs,
                p95LatencyMs,
                resolveModelStatus(successRate, avgLatencyMs)
        );
    }

    private AdminDashboardPromptSummaryDto toPromptSummaryDto(
            PromptCode promptCode,
            String configuredVersion,
            PromptUsageRow usageRow
    ) {
        PromptTemplateMetadata metadata = promptTemplateService.loadMetadata(promptCode.code());
        return new AdminDashboardPromptSummaryDto(
                promptCode.code(),
                configuredVersion,
                metadata.promptVersion(),
                metadata.sourcePath(),
                formatLastUsedAt(usageRow == null ? null : usageRow.getLastUsedAt()),
                usageRow == null ? 0L : safeLong(usageRow.getCallsLast24Hours())
        );
    }

    private Map<LocalDate, Long> toMetricMap(List<DailyMetricRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> row.getMetricDate() != null)
                .collect(Collectors.toMap(
                        DailyMetricRow::getMetricDate,
                        row -> safeLong(row.getMetricValue()),
                        Long::sum
                ));
    }

    private Double calculateSuccessRate(Long successCount, Long requestCount) {
        if (requestCount == null || requestCount <= 0L) {
            return 0.0d;
        }
        return BigDecimal.valueOf(successCount == null ? 0L : successCount)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Long calculateP95Latency(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return null;
        }
        List<Long> sortedLatencies = latencies.stream()
                .sorted()
                .toList();
        int rank = (int) Math.ceil(sortedLatencies.size() * 0.95d);
        return sortedLatencies.get(Math.max(rank - 1, 0));
    }

    private String resolveModelStatus(Double successRate, Long avgLatencyMs) {
        if (successRate == null || avgLatencyMs == null) {
            return "error";
        }
        if (successRate >= 0.98d && avgLatencyMs < 1500L) {
            return "healthy";
        }
        if (successRate >= 0.95d && avgLatencyMs < 3000L) {
            return "warning";
        }
        return "error";
    }

    private String formatLastUsedAt(LocalDateTime lastUsedAt) {
        if (lastUsedAt == null) {
            return null;
        }
        return lastUsedAt.atZone(DASHBOARD_ZONE).format(OFFSET_DATE_TIME_FORMATTER);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DASHBOARD_ZONE));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(DASHBOARD_ZONE));
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private record ModelKey(String modelProvider, String modelName) {
    }
}
