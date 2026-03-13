package com.a05.aiinterview.profile.service;

import com.a05.aiinterview.auth.mapper.UserMapper;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.profile.config.ProfileAvatarStorageConfig;
import com.a05.aiinterview.profile.dto.ProfileStatisticsDto;
import com.a05.aiinterview.profile.dto.SkillOverviewDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    @Test
    void getStatistics_shouldAggregateFromSessionsAndReports() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(userMapper, sessionMapper, reportMapper, storageConfig);

        InterviewSession s1 = new InterviewSession();
        s1.setId(1L);
        s1.setCreatedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        s1.setStartedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        s1.setFinishedAt(LocalDateTime.of(2026, 3, 1, 9, 30));

        InterviewSession s2 = new InterviewSession();
        s2.setId(2L);
        s2.setCreatedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        s2.setStartedAt(LocalDateTime.of(2026, 3, 2, 9, 0));
        s2.setFinishedAt(LocalDateTime.of(2026, 3, 2, 9, 45));

        when(sessionMapper.selectList(any())).thenReturn(List.of(s1, s2));

        InterviewReport r1 = new InterviewReport();
        r1.setSessionId(1L);
        r1.setOverallScore(BigDecimal.valueOf(80));
        InterviewReport r2 = new InterviewReport();
        r2.setSessionId(2L);
        r2.setOverallScore(BigDecimal.valueOf(90));
        when(reportMapper.selectList(any())).thenReturn(List.of(r1, r2));

        ProfileStatisticsDto dto = service.getStatistics(9L, null);
        assertEquals(2, dto.getTotalSessions());
        assertEquals(75, dto.getTotalMinutes());
        assertEquals(BigDecimal.valueOf(85.0), dto.getAverageScore());
        assertEquals(2, dto.getScoreTrend().size());
        assertEquals("2026-03-01", dto.getScoreTrend().get(0).getDate());
    }

    @Test
    void getStatistics_shouldReturnExplicitEmptyValuesWhenNoFacts() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(userMapper, sessionMapper, reportMapper, storageConfig);

        when(sessionMapper.selectList(any())).thenReturn(List.of());

        ProfileStatisticsDto dto = service.getStatistics(9L, "FRONTEND");
        assertEquals(0, dto.getTotalSessions());
        assertEquals(0, dto.getTotalMinutes());
        assertNull(dto.getAverageScore());
        assertEquals(0, dto.getScoreTrend().size());
    }

    @Test
    void getSkillOverview_shouldAggregateByDomainFromReportFacts() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(userMapper, sessionMapper, reportMapper, storageConfig);

        InterviewSession s1 = new InterviewSession();
        s1.setId(1L);
        s1.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        InterviewSession s2 = new InterviewSession();
        s2.setId(2L);
        s2.setCreatedAt(LocalDateTime.of(2026, 3, 2, 11, 0));
        when(sessionMapper.selectList(any())).thenReturn(List.of(s1, s2));

        InterviewReport r1 = new InterviewReport();
        r1.setSessionId(1L);
        r1.setSkillDomainScores(List.of(
                Map.of("domainCode", "frontend_core", "domainName", "前端基础", "score", 80),
                Map.of("domainCode", "engineering", "domainName", "工程实践", "score", 70)
        ));
        InterviewReport r2 = new InterviewReport();
        r2.setSessionId(2L);
        r2.setSkillDomainScores(List.of(
                Map.of("domainCode", "frontend_core", "domainName", "前端基础", "score", 90)
        ));
        when(reportMapper.selectList(any())).thenReturn(List.of(r1, r2));

        SkillOverviewDto dto = service.getSkillOverview(9L, "FRONTEND");
        assertEquals("FRONTEND", dto.getPositionCode());
        assertEquals(2, dto.getDomains().size());
        assertEquals("frontend_core", dto.getDomains().get(0).getDomainCode());
        assertEquals(BigDecimal.valueOf(85.0), dto.getDomains().get(0).getAverageScore());
        assertEquals(2, dto.getDomains().get(0).getSampleCount());
    }
}

