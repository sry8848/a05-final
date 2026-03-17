package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewHistoryItemDto;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InterviewHistoryService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewSessionStatusService interviewSessionStatusService;

    public InterviewHistoryPageDto list(Long userId,
                                        int page,
                                        int pageSize,
                                        String status,
                                        String targetRole,
                                        LocalDateTime dateFrom,
                                        LocalDateTime dateTo,
                                        String sortBy,
                                        String sortOrder) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        String safeSortBy = normalizeSortBy(sortBy);
        String safeSortOrder = normalizeSortOrder(sortOrder);

        long total = interviewSessionMapper.countHistory(userId, normalize(status), normalize(targetRole), dateFrom, dateTo);
        List<InterviewHistoryItemDto> items = interviewSessionMapper.selectHistoryPage(
                userId,
                normalize(status),
                normalize(targetRole),
                dateFrom,
                dateTo,
                safeSortBy,
                safeSortOrder,
                offset,
                safePageSize
        );

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
