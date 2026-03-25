package com.a05.aiinterview.profile.service;

import com.a05.aiinterview.auth.mapper.UserMapper;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.service.InterviewSessionStatusService;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.service.PositionService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    @Test
    void getStatistics_shouldAggregateProfessionalRadarFromLatestEightSessions() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        List<InterviewSession> sessions = List.of(
                buildSession(1L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 1, 9, 0), 30),
                buildSession(2L, "JAVA_BACKEND", "practice", LocalDateTime.of(2026, 3, 2, 9, 0), 45),
                buildSession(3L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 3, 9, 0), 40),
                buildSession(4L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 4, 9, 0), 35)
        );
        when(sessionMapper.selectList(any())).thenReturn(sessions);

        InterviewReport r1 = new InterviewReport();
        r1.setSessionId(1L);
        r1.setOverallScore(BigDecimal.valueOf(80));
        r1.setComprehensiveRadarScores(Map.of("dimensions", List.of(
                Map.of("dimensionKey", "fundamentals", "dimensionName", "基础原理掌握", "score", 80),
                Map.of("dimensionKey", "communication", "dimensionName", "沟通表达与结构化呈现", "score", 70)
        )));

        InterviewReport r2 = new InterviewReport();
        r2.setSessionId(2L);
        r2.setOverallScore(BigDecimal.valueOf(90));
        r2.setComprehensiveRadarScores(null);

        InterviewReport r3 = new InterviewReport();
        r3.setSessionId(3L);
        r3.setOverallScore(BigDecimal.valueOf(84));
        r3.setComprehensiveRadarScores(Map.of("dimensions", List.of(
                Map.of("dimensionKey", "fundamentals", "dimensionName", "基础原理掌握", "score", 90),
                Map.of("dimensionKey", "communication", "dimensionName", "沟通表达与结构化呈现", "score", 80)
        )));

        InterviewReport r4 = new InterviewReport();
        r4.setSessionId(4L);
        r4.setOverallScore(BigDecimal.valueOf(88));
        r4.setComprehensiveRadarScores(Map.of("dimensions", List.of(
                Map.of("dimensionKey", "fundamentals", "dimensionName", "基础原理掌握", "score", 70),
                Map.of("dimensionKey", "communication", "dimensionName", "沟通表达与结构化呈现", "score", 85)
        )));
        when(reportMapper.selectList(any())).thenReturn(List.of(r1, r2, r3, r4));

        ProfileStatisticsDto dto = service.getStatistics(9L, "JAVA_BACKEND");
        assertEquals(4, dto.getTotalSessions());
        assertEquals(150, dto.getTotalMinutes());
        assertEquals(BigDecimal.valueOf(85.5), dto.getAverageScore());
        assertEquals(4, dto.getScoreTrend().size());
        assertEquals(3, dto.getProfessionalSampleCount());
        assertEquals(2, dto.getProfessionalRadarScores().size());
        assertEquals("fundamentals", dto.getProfessionalRadarScores().get(0).getDimensionKey());
        assertEquals(BigDecimal.valueOf(80.0), dto.getProfessionalRadarScores().get(0).getScore());
        assertEquals(BigDecimal.valueOf(78.3), dto.getProfessionalRadarScores().get(1).getScore());
    }

    @Test
    void getStatistics_shouldReturnExplicitEmptyValuesWhenNoFacts() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        when(sessionMapper.selectList(any())).thenReturn(List.of());

        ProfileStatisticsDto dto = service.getStatistics(9L, "FRONTEND");
        assertEquals(0, dto.getTotalSessions());
        assertEquals(0, dto.getTotalMinutes());
        assertNull(dto.getAverageScore());
        assertEquals(0, dto.getScoreTrend().size());
        assertEquals(0, dto.getProfessionalSampleCount());
        assertEquals(0, dto.getProfessionalRadarScores().size());
    }

    @Test
    void getSkillOverview_shouldAggregateRecentEightSessionsAndBuildRankingsAndTrends() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        List<InterviewSession> sessions = List.of(
                buildSession(1L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 1, 10, 0), 30),
                buildSession(2L, "JAVA_BACKEND", "practice", LocalDateTime.of(2026, 3, 2, 10, 0), 30),
                buildSession(3L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 3, 10, 0), 30),
                buildSession(4L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 4, 10, 0), 30)
        );
        when(sessionMapper.selectList(any())).thenReturn(sessions);
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(List.of(
                buildDomainEntity("java_core", "Java 核心基础"),
                buildDomainEntity("project_delivery", "项目落地"),
                buildDomainEntity("debugging", "问题排查"),
                buildDomainEntity("cache", "缓存")
        ));

        InterviewReport s1 = new InterviewReport();
        s1.setSessionId(1L);
        s1.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 60, "commentary", "集合与并发基础还需补强"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 55, "commentary", "项目职责描述较真实"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 42, "commentary", "排查步骤偏跳跃")
        ));

        InterviewReport s2 = new InterviewReport();
        s2.setSessionId(2L);
        s2.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 75, "commentary", "原理掌握有提升"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 65, "commentary", "项目落地说明仍可更细"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 45, "commentary", "问题定位仍缺验证闭环")
        ));

        InterviewReport s3 = new InterviewReport();
        s3.setSessionId(3L);
        s3.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 90, "commentary", "关键机制回答准确"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 80, "commentary", "项目链路表达完整"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 40, "commentary", "仍缺少分层定位思路"),
                Map.of("domainCode", "cache", "domainName", "缓存", "score", 88, "commentary", "样本不足不应上榜")
        ));

        InterviewReport s4 = new InterviewReport();
        s4.setSessionId(4L);
        s4.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 82, "commentary", "回答更稳定"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 85, "commentary", "方案说明扎实"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 35, "commentary", "缺少验证与回归意识")
        ));
        when(reportMapper.selectList(any())).thenReturn(List.of(s1, s2, s3, s4));

        SkillOverviewDto dto = service.getSkillOverview(9L, "JAVA_BACKEND");

        assertEquals("JAVA_BACKEND", dto.getPositionCode());
        assertEquals(4, dto.getDomains().size());

        var javaCore = dto.getDomains().stream()
                .filter(item -> "java_core".equals(item.getDomainCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(76.8), javaCore.getScore());
        assertEquals(BigDecimal.valueOf(76.8), javaCore.getAverageScore());
        assertEquals(BigDecimal.valueOf(22.0), javaCore.getScoreDelta());
        assertEquals(4, javaCore.getAppearanceCount());
        assertEquals(4, javaCore.getRecentScores().size());

        var debugging = dto.getDomains().stream()
                .filter(item -> "debugging".equals(item.getDomainCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(40.5), debugging.getScore());
        assertEquals(BigDecimal.valueOf(40.5), debugging.getAverageScore());
        assertEquals(BigDecimal.valueOf(-7.0), debugging.getScoreDelta());
        assertTrue(debugging.isRankingEligible());
        assertEquals(List.of("缺少验证与回归意识", "缺少分层定位思路", "问题定位缺验证闭环"), debugging.getWeaknessPoints());
        assertEquals("缺少验证与回归意识 · 缺少分层定位思路 · 问题定位缺验证闭环", debugging.getWeaknessSummary());

        var cache = dto.getDomains().stream()
                .filter(item -> "cache".equals(item.getDomainCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, cache.getAppearanceCount());
        assertTrue(!cache.isRankingEligible());

        assertEquals(2, dto.getTopStrengths().size());
        assertEquals("java_core", dto.getTopStrengths().get(0).getDomainCode());
        assertEquals(1, dto.getTopWeaknesses().size());
        assertEquals("debugging", dto.getTopWeaknesses().get(0).getDomainCode());
        assertEquals(4, dto.getTopWeaknesses().get(0).getRecentScores().size());
    }

    @Test
    void getSkillOverview_shouldIgnoreUnassessedDomainsWhenRanking() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        List<InterviewSession> sessions = List.of(
                buildSession(1L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 1, 10, 0), 30),
                buildSession(2L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 2, 10, 0), 30),
                buildSession(3L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 3, 10, 0), 30)
        );
        when(sessionMapper.selectList(any())).thenReturn(sessions);
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(List.of(
                buildDomainEntity("java_core", "Java 核心基础"),
                buildDomainEntity("concurrency", "并发编程")
        ));

        InterviewReport s1 = new InterviewReport();
        s1.setSessionId(1L);
        s1.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 62, "commentary", "集合原理仍有遗漏"),
                Map.of("domainCode", "concurrency", "domainName", "并发编程", "score", 50, "commentary", "未被提问，无作答证据，无法评估掌握程度")
        ));

        InterviewReport s2 = new InterviewReport();
        s2.setSessionId(2L);
        s2.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 68, "commentary", "关键机制理解更完整"),
                Map.of("domainCode", "concurrency", "domainName", "并发编程", "score", 50, "commentary", "未被提问，无作答证据，深度不可评估")
        ));

        InterviewReport s3 = new InterviewReport();
        s3.setSessionId(3L);
        s3.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 74, "commentary", "回答更稳定"),
                Map.of("domainCode", "concurrency", "domainName", "并发编程", "score", 50, "commentary", "未被提问，无作答证据，无法评估掌握程度")
        ));
        when(reportMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));

        SkillOverviewDto dto = service.getSkillOverview(9L, "JAVA_BACKEND");

        var concurrency = dto.getDomains().stream()
                .filter(item -> "concurrency".equals(item.getDomainCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, concurrency.getAppearanceCount());
        assertTrue(!concurrency.isRankingEligible());
        assertNull(concurrency.getScore());
        assertEquals(List.of(), concurrency.getWeaknessPoints());

        assertEquals(1, dto.getTopStrengths().size());
        assertEquals("java_core", dto.getTopStrengths().get(0).getDomainCode());
        assertTrue(dto.getTopStrengths().stream().noneMatch(item -> "concurrency".equals(item.getDomainCode())));
        assertTrue(dto.getTopWeaknesses().isEmpty());
    }

    @Test
    void getStatistics_shouldIgnoreUnfinishedSessionsInTotals() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        InterviewSession completed = buildSession(1L, "JAVA_BACKEND", "professional",
                LocalDateTime.of(2026, 3, 17, 15, 0), 30);

        InterviewSession unfinished = new InterviewSession();
        unfinished.setId(2L);
        unfinished.setTargetRole("JAVA_BACKEND");
        unfinished.setMode("practice");
        unfinished.setStatus("in_progress");
        unfinished.setCreatedAt(LocalDateTime.of(2026, 3, 17, 16, 0));
        unfinished.setStartedAt(LocalDateTime.of(2026, 3, 17, 16, 0));

        when(sessionMapper.selectList(any())).thenReturn(List.of(completed, unfinished));
        when(statusService.resolveAndSync(completed)).thenReturn("completed");
        when(statusService.resolveAndSync(unfinished)).thenReturn("in_progress");

        InterviewReport report = new InterviewReport();
        report.setSessionId(1L);
        report.setOverallScore(BigDecimal.valueOf(88));
        when(reportMapper.selectList(any())).thenReturn(List.of(report));

        ProfileStatisticsDto dto = service.getStatistics(9L, "JAVA_BACKEND");

        assertEquals(1, dto.getTotalSessions());
        assertEquals(30, dto.getTotalMinutes());
        assertEquals(1, dto.getScoreTrend().size());
    }

    @Test
    void getStatistics_shouldPreferEffectiveOverallScoreFromRadarWhenAvailable() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        List<InterviewSession> sessions = List.of(
                buildSession(1L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 1, 9, 0), 30),
                buildSession(2L, "JAVA_BACKEND", "practice", LocalDateTime.of(2026, 3, 2, 9, 0), 45)
        );
        when(sessionMapper.selectList(any())).thenReturn(sessions);

        InterviewReport r1 = new InterviewReport();
        r1.setSessionId(1L);
        r1.setOverallScore(BigDecimal.valueOf(10));
        r1.setComprehensiveRadarScores(Map.of("dimensions", List.of(
                Map.of("dimensionKey", "fundamentals", "dimensionName", "基础原理掌握", "score", 80),
                Map.of("dimensionKey", "engineering_practice", "dimensionName", "工程实践与项目落地", "score", 70),
                Map.of("dimensionKey", "scenario_tradeoff", "dimensionName", "场景分析与方案取舍", "score", 60),
                Map.of("dimensionKey", "debugging", "dimensionName", "问题定位与排查思路", "score", 90),
                Map.of("dimensionKey", "communication", "dimensionName", "沟通表达与结构化呈现", "score", 100)
        )));

        InterviewReport r2 = new InterviewReport();
        r2.setSessionId(2L);
        r2.setOverallScore(BigDecimal.valueOf(88));
        r2.setComprehensiveRadarScores(null);
        when(reportMapper.selectList(any())).thenReturn(List.of(r1, r2));

        ProfileStatisticsDto dto = service.getStatistics(9L, "JAVA_BACKEND");

        assertEquals(BigDecimal.valueOf(83.5), dto.getAverageScore());
        assertEquals(BigDecimal.valueOf(79.0), dto.getScoreTrend().get(0).getScore());
        assertEquals(BigDecimal.valueOf(88), dto.getScoreTrend().get(1).getScore());
    }

    @Test
    void getSkillOverview_shouldUseAverageReportScoreAsRankScore() {
        UserMapper userMapper = mock(UserMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ProfileAvatarStorageConfig storageConfig = new ProfileAvatarStorageConfig();
        ProfileService service = new ProfileService(
                userMapper, sessionMapper, reportMapper, positionService, storageConfig, statusService
        );

        List<InterviewSession> sessions = List.of(
                buildSession(1L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 1, 10, 0), 30),
                buildSession(2L, "JAVA_BACKEND", "practice", LocalDateTime.of(2026, 3, 2, 10, 0), 30),
                buildSession(3L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 3, 10, 0), 30),
                buildSession(4L, "JAVA_BACKEND", "professional", LocalDateTime.of(2026, 3, 4, 10, 0), 30)
        );
        when(sessionMapper.selectList(any())).thenReturn(sessions);
        when(positionService.listSkillDomainEntities("JAVA_BACKEND")).thenReturn(List.of(
                buildDomainEntity("java_core", "Java 核心基础"),
                buildDomainEntity("project_delivery", "项目落地"),
                buildDomainEntity("debugging", "问题排查")
        ));

        InterviewReport s1 = new InterviewReport();
        s1.setSessionId(1L);
        s1.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 60, "commentary", "集合与并发基础还需补强"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 55, "commentary", "项目职责描述较真实"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 42, "commentary", "排查步骤偏跳跃")
        ));

        InterviewReport s2 = new InterviewReport();
        s2.setSessionId(2L);
        s2.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 75, "commentary", "原理掌握有提升"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 65, "commentary", "项目落地说明仍可更细"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 45, "commentary", "问题定位仍缺验证闭环")
        ));

        InterviewReport s3 = new InterviewReport();
        s3.setSessionId(3L);
        s3.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 90, "commentary", "关键机制回答准确"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 80, "commentary", "项目链路表达完整"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 40, "commentary", "仍缺少分层定位思路")
        ));

        InterviewReport s4 = new InterviewReport();
        s4.setSessionId(4L);
        s4.setSkillDomainScores(List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础", "score", 82, "commentary", "回答更稳定"),
                Map.of("domainCode", "project_delivery", "domainName", "项目落地", "score", 85, "commentary", "方案说明扎实"),
                Map.of("domainCode", "debugging", "domainName", "问题排查", "score", 35, "commentary", "缺少验证与回归意识")
        ));
        when(reportMapper.selectList(any())).thenReturn(List.of(s1, s2, s3, s4));

        SkillOverviewDto dto = service.getSkillOverview(9L, "JAVA_BACKEND");

        var javaCore = dto.getDomains().stream()
                .filter(item -> "java_core".equals(item.getDomainCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(76.8), javaCore.getScore());
        assertEquals(BigDecimal.valueOf(76.8), javaCore.getAverageScore());
        assertEquals(BigDecimal.valueOf(22.0), javaCore.getScoreDelta());

        var debugging = dto.getDomains().stream()
                .filter(item -> "debugging".equals(item.getDomainCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(40.5), debugging.getScore());
        assertEquals(BigDecimal.valueOf(-7.0), debugging.getScoreDelta());

        assertEquals("java_core", dto.getTopStrengths().get(0).getDomainCode());
        assertEquals("debugging", dto.getTopWeaknesses().get(0).getDomainCode());
    }

    private InterviewSession buildSession(
            Long id, String targetRole, String mode, LocalDateTime createdAt, int durationMinutes) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setTargetRole(targetRole);
        session.setMode(mode);
        session.setStatus("completed");
        session.setCreatedAt(createdAt);
        session.setStartedAt(createdAt);
        session.setFinishedAt(createdAt.plusMinutes(durationMinutes));
        return session;
    }

    private PositionSkillDomain buildDomainEntity(String code, String name) {
        PositionSkillDomain entity = new PositionSkillDomain();
        entity.setPositionCode("JAVA_BACKEND");
        entity.setDomainCode(code);
        entity.setDomainName(name);
        entity.setVersion(1);
        return entity;
    }
}
