package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.LearningRecommendationDto;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import com.a05.aiinterview.position.service.PositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningRecommendationServiceTest {

    @Test
    void getRecommendations_shouldNotExposeDifficultyField() throws Exception {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        LearningRecommendationService service = new LearningRecommendationService(sessionMapper, reportMapper, positionService);
        ReflectionTestUtils.setField(service, "recommendationEnabled", true);
        ReflectionTestUtils.setField(service, "topK", 3);

        InterviewSession session = new InterviewSession();
        session.setId(2001L);
        session.setUserId(99L);
        session.setPositionCode("JAVA_BACKEND");
        when(sessionMapper.selectById(2001L)).thenReturn(session);

        InterviewReport report = new InterviewReport();
        report.setSkillDomainScores(List.of(
                Map.of("domainCode", "redis", "domainName", "Redis", "score", 58),
                Map.of("domainCode", "mysql", "domainName", "MySQL", "score", 72)
        ));
        report.setImprovementSuggestions(List.of("补齐缓存击穿和击穿保护方案"));
        report.setRecommendedTopics(List.of("Redis 缓存高可用"));
        when(reportMapper.selectBySessionId(2001L)).thenReturn(report);

        LearningRecommendationDto dto = service.getRecommendations(2001L, 99L);

        assertEquals("ready", dto.getRecommendationStatus());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(dto);
        assertFalse(json.contains("\"difficulty\""), json);
    }

    @Test
    void getRecommendations_shouldIgnoreLegacyFrontendDomainCodes() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        PositionService positionService = mock(PositionService.class);
        LearningRecommendationService service = new LearningRecommendationService(sessionMapper, reportMapper, positionService);
        ReflectionTestUtils.setField(service, "recommendationEnabled", true);
        ReflectionTestUtils.setField(service, "topK", 3);

        InterviewSession session = new InterviewSession();
        session.setId(3001L);
        session.setUserId(99L);
        session.setPositionCode("FRONTEND");
        when(sessionMapper.selectById(3001L)).thenReturn(session);
        when(positionService.listSkillDomainEntities("FRONTEND")).thenReturn(List.of(
                buildDomainEntity("browser_runtime", "浏览器运行时"),
                buildDomainEntity("ui_foundation", "HTML / CSS / UI 基础")
        ));

        InterviewReport report = new InterviewReport();
        report.setSkillDomainScores(List.of(
                Map.of("domainCode", "browser", "domainName", "浏览器原理", "score", 20),
                Map.of("domainCode", "ui_foundation", "domainName", "HTML / CSS / UI 基础", "score", 40),
                Map.of("domainCode", "browser_runtime", "domainName", "浏览器运行时", "score", 60)
        ));
        report.setImprovementSuggestions(List.of("补齐浏览器渲染与无障碍基础"));
        report.setRecommendedTopics(List.of("浏览器运行时核心机制"));
        when(reportMapper.selectBySessionId(3001L)).thenReturn(report);

        LearningRecommendationDto dto = service.getRecommendations(3001L, 99L);

        assertEquals("ready", dto.getRecommendationStatus());
        List<LearningRecommendationDto.Item> immediateItems = dto.getSections().stream()
                .filter(section -> "immediate".equals(section.getSectionKey()))
                .findFirst()
                .orElseThrow()
                .getItems();
        assertEquals(2, immediateItems.size());
        assertEquals("ui_foundation", immediateItems.get(0).getDomainCode());
        assertEquals("HTML / CSS / UI 基础", immediateItems.get(0).getDomainName());
        assertEquals("browser_runtime", immediateItems.get(1).getDomainCode());
        assertEquals("浏览器运行时", immediateItems.get(1).getDomainName());
        assertFalse(immediateItems.stream().anyMatch(item -> "browser".equals(item.getDomainCode())));
    }

    private PositionSkillDomain buildDomainEntity(String code, String name) {
        PositionSkillDomain entity = new PositionSkillDomain();
        entity.setPositionCode("FRONTEND");
        entity.setDomainCode(code);
        entity.setDomainName(name);
        entity.setVersion(1);
        return entity;
    }
}
