package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.LearningRecommendationDto;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
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
        LearningRecommendationService service = new LearningRecommendationService(sessionMapper, reportMapper);
        ReflectionTestUtils.setField(service, "recommendationEnabled", true);
        ReflectionTestUtils.setField(service, "topK", 3);

        InterviewSession session = new InterviewSession();
        session.setId(2001L);
        session.setUserId(99L);
        session.setTargetRole("JAVA_BACKEND");
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
}
