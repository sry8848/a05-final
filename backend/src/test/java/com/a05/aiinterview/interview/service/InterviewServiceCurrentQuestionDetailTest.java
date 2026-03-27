package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewDetailDto;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewServiceCurrentQuestionDetailTest {

    @Test
    void getSessionDetail_shouldReturnCurrentQuestionByCurrentQuestionNo() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewService service = newService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(10L);
        session.setUserId(1L);
        session.setStatus("in_progress");
        session.setCurrentQuestionNo(2);
        session.setFirstQuestionJson(Map.of(
                "questionId", 101L,
                "questionNo", 1,
                "questionType", "INTRO",
                "stem", "请先做一个自我介绍"
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainCode", "redis", "domainName", "Redis")
                )
        ));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(202L);
        currentQuestion.setSessionId(10L);
        currentQuestion.setQuestionNo(2);
        currentQuestion.setQuestionType("PROJECT_DEEP_DIVE");
        currentQuestion.setDomainCode("redis");
        currentQuestion.setStem("结合项目讲讲缓存击穿的治理。");
        currentQuestion.setTargetSkill("缓存击穿");

        when(sessionMapper.selectById(10L)).thenReturn(session);
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion);

        InterviewDetailDto detail = service.getSessionDetail(10L, 1L);

        assertNotNull(detail.getCurrentQuestion());
        assertEquals(202L, detail.getCurrentQuestion().getQuestionId());
        assertEquals(2, detail.getCurrentQuestion().getQuestionNo());
        assertEquals("PROJECT_DEEP_DIVE", detail.getCurrentQuestion().getQuestionType());
        assertEquals("Redis", detail.getCurrentQuestion().getDomainName());
        assertEquals("缓存击穿", detail.getCurrentQuestion().getTargetSkill());
    }

    @Test
    void getSessionDetail_shouldUseQuestionRecordForSingleQuestionCurrentQuestion() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewService service = newService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(20L);
        session.setUserId(1L);
        session.setStatus("in_progress");
        session.setCurrentQuestionNo(1);
        session.setSyllabusJson(Map.of("domains", List.of()));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(301L);
        currentQuestion.setSessionId(20L);
        currentQuestion.setQuestionNo(1);
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setDomainCode(null);
        currentQuestion.setStem("请讲讲事件循环。");
        currentQuestion.setTargetSkill("前端基础");
        currentQuestion.setGenerationContextJson(Map.of("domainName", "前端基础"));

        when(sessionMapper.selectById(20L)).thenReturn(session);
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion);

        InterviewDetailDto detail = service.getSessionDetail(20L, 1L);

        assertNotNull(detail.getCurrentQuestion());
        assertEquals(301L, detail.getCurrentQuestion().getQuestionId());
        assertEquals("PRINCIPLE", detail.getCurrentQuestion().getQuestionType());
        assertEquals("前端基础", detail.getCurrentQuestion().getDomainName());
        assertEquals("前端基础", detail.getCurrentQuestion().getTargetSkill());
    }

    @Test
    void getSessionDetail_shouldExposeIntroDomainNameWhenFirstQuestionRecordExists() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewService service = newService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(25L);
        session.setUserId(1L);
        session.setStatus("in_progress");
        session.setCurrentQuestionNo(1);
        session.setSyllabusJson(Map.of("domains", List.of()));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(351L);
        currentQuestion.setSessionId(25L);
        currentQuestion.setQuestionNo(1);
        currentQuestion.setQuestionType("INTRO");
        currentQuestion.setDomainCode("intro");
        currentQuestion.setStem("请先做一个简短的自我介绍");
        currentQuestion.setTargetSkill("沟通表达与项目概述");
        currentQuestion.setGenerationContextJson(Map.of("domainCode", "intro"));

        when(sessionMapper.selectById(25L)).thenReturn(session);
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion);

        InterviewDetailDto detail = service.getSessionDetail(25L, 1L);

        assertNotNull(detail.getCurrentQuestion());
        assertEquals("intro", detail.getCurrentQuestion().getDomainCode());
        assertEquals("自我介绍", detail.getCurrentQuestion().getDomainName());
    }

    @Test
    void getSessionDetail_shouldFallbackToFirstQuestionSnapshotWhenCurrentQuestionOneMissing() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewService service = newService(sessionMapper, questionMapper);

        InterviewSession session = new InterviewSession();
        session.setId(30L);
        session.setUserId(1L);
        session.setStatus("in_progress");
        session.setCurrentQuestionNo(1);
        session.setFirstQuestionJson(Map.of(
                "questionId", 401L,
                "questionNo", 1,
                "questionType", "INTRO",
                "domainName", "",
                "stem", "请做一个简短自我介绍",
                "targetSkill", "沟通表达与项目概述",
                "hintAvailable", true
        ));

        when(sessionMapper.selectById(30L)).thenReturn(session);
        when(questionMapper.selectOne(any())).thenReturn(null);

        InterviewDetailDto detail = service.getSessionDetail(30L, 1L);

        assertNotNull(detail.getCurrentQuestion());
        assertEquals(401L, detail.getCurrentQuestion().getQuestionId());
        assertEquals("INTRO", detail.getCurrentQuestion().getQuestionType());
        assertEquals("请做一个简短自我介绍", detail.getCurrentQuestion().getStem());
    }

    private InterviewService newService(InterviewSessionMapper sessionMapper, InterviewQuestionMapper questionMapper) {
        InterviewService service = new InterviewService(
                sessionMapper,
                questionMapper,
                mock(ResumeMapper.class),
                mock(InterviewPreferenceService.class),
                mock(com.a05.aiinterview.interview.engine.PlannerOrchestrationService.class)
        );
        setField(service, InterviewService.class, "aiModel", "mock");
        setField(service, InterviewService.class, "mockEnabled", true);
        return service;
    }

    private void setField(Object target, Class<?> type, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
