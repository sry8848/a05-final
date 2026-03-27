package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewHistoryItemDto;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.service.support.InterviewOverallScoreSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewHistoryService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewSessionStatusService interviewSessionStatusService;

    public InterviewHistoryPageDto list(Long userId,
                                        int page,
                                        int pageSize,
                                        String status,
                                        String positionCode,
                                        LocalDateTime dateFrom,
                                        LocalDateTime dateTo,
                                        String sortBy,
                                        String sortOrder) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        String normalizedStatus = normalize(status);
        String normalizedTargetRole = normalize(positionCode);
        String safeSortBy = normalizeSortBy(sortBy);
        String safeSortOrder = normalizeSortOrder(sortOrder);

        long total = interviewSessionMapper.countHistory(userId, normalizedStatus, normalizedTargetRole, dateFrom, dateTo);
        List<InterviewHistoryItemDto> items = "overallScore".equals(safeSortBy)
                ? selectAndSortByEffectiveOverallScore(
                userId,
                normalizedStatus,
                normalizedTargetRole,
                dateFrom,
                dateTo,
                safeSortOrder,
                safePageSize,
                offset,
                total)
                : interviewSessionMapper.selectHistoryPage(
                userId,
                normalizedStatus,
                normalizedTargetRole,
                dateFrom,
                dateTo,
                safeSortBy,
                safeSortOrder,
                offset,
                safePageSize
        );
        hydrateEffectiveOverallScores(items);

        InterviewHistoryPageDto dto = new InterviewHistoryPageDto();
        dto.setTotal(total);
        dto.setPage(safePage);
        dto.setPageSize(safePageSize);
        items.forEach(item -> item.setStatus(
                interviewSessionStatusService.toDisplayStatus(
                        interviewSessionStatusService.resolveAndSync(item.getSessionId())
                )));
        dto.setItems(items);
        return dto;
    }

    private List<InterviewHistoryItemDto> selectAndSortByEffectiveOverallScore(Long userId,
                                                                               String status,
                                                                               String positionCode,
                                                                               LocalDateTime dateFrom,
                                                                               LocalDateTime dateTo,
                                                                               String sortOrder,
                                                                               int pageSize,
                                                                               int offset,
                                                                               long total) {
        if (total <= 0) {
            return List.of();
        }
        int fetchLimit = (int) Math.min(total, Integer.MAX_VALUE);
        List<InterviewHistoryItemDto> candidates = new ArrayList<>(interviewSessionMapper.selectHistoryPage(
                userId,
                status,
                positionCode,
                dateFrom,
                dateTo,
                "createdAt",
                "desc",
                0,
                fetchLimit
        ));
        hydrateEffectiveOverallScores(candidates);
        candidates.sort(buildOverallScoreComparator(sortOrder));

        int fromIndex = Math.min(offset, candidates.size());
        int toIndex = Math.min(fromIndex + pageSize, candidates.size());
        return candidates.subList(fromIndex, toIndex);
    }

    private void hydrateEffectiveOverallScores(List<InterviewHistoryItemDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> sessionIds = items.stream()
                .map(InterviewHistoryItemDto::getSessionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (sessionIds.isEmpty()) {
            return;
        }

        Map<Long, InterviewReport> reportMap = new HashMap<>();
        interviewReportMapper.selectList(new LambdaQueryWrapper<InterviewReport>()
                        .in(InterviewReport::getSessionId, sessionIds))
                .forEach(report -> reportMap.put(report.getSessionId(), report));

        items.forEach(item -> {
            InterviewReport report = reportMap.get(item.getSessionId());
            BigDecimal effectiveScore = InterviewOverallScoreSupport.resolveOverallScore(report);
            if (effectiveScore != null) {
                item.setOverallScore(effectiveScore);
            }
        });
    }

    private Comparator<InterviewHistoryItemDto> buildOverallScoreComparator(String sortOrder) {
        return (left, right) -> {
            int scoreCompare = compareNullableScores(left.getOverallScore(), right.getOverallScore());
            if ("desc".equalsIgnoreCase(sortOrder)) {
                scoreCompare = -scoreCompare;
            }
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            LocalDateTime leftCreatedAt = left.getCreatedAt();
            LocalDateTime rightCreatedAt = right.getCreatedAt();
            int createdAtCompare = Comparator.nullsLast(LocalDateTime::compareTo).reversed()
                    .compare(leftCreatedAt, rightCreatedAt);
            if (createdAtCompare != 0) {
                return createdAtCompare;
            }
            return Comparator.nullsLast(Long::compareTo).reversed()
                    .compare(left.getSessionId(), right.getSessionId());
        };
    }

    private int compareNullableScores(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "createdAt";
        }
        String value = sortBy.trim().toLowerCase(Locale.ROOT);
        if ("overallscore".equals(value) || "overall_score".equals(value) || "score".equals(value)) {
            return "overallScore";
        }
        return "createdAt";
    }

    private String normalizeSortOrder(String sortOrder) {
        if (!StringUtils.hasText(sortOrder)) {
            return "desc";
        }
        return "asc".equalsIgnoreCase(sortOrder.trim()) ? "asc" : "desc";
    }
}
