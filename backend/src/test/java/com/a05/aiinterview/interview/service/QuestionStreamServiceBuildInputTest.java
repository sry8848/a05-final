package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import com.a05.aiinterview.speech.service.TtsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class QuestionStreamServiceBuildInputTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildGenInput_shouldFallbackRagContextAndNormalizeDifficulty() throws Exception {
        QuestionStreamService service = new QuestionStreamService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(RagRetrievalService.class),
                mock(TtsService.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper()
        );

        InterviewSession session = new InterviewSession();
        session.setId(100L);
        session.setTargetRole("JAVA_BACKEND");
        session.setMode("professional");
        session.setExperienceLevel("SENIOR");
        session.setSyllabusJson(Map.of("domains", List.of()));

        EvaluationDecisionOutput.NextQuestionStrategy strategy =
                EvaluationDecisionOutput.NextQuestionStrategy.builder()
                        .nextDomainId(1L)
                        .nextDomainCode("concurrency")
                        .nextDomainName("并发编程")
                        .questionType("PRINCIPLE")
                        .targetDepth("L3")
                        .difficulty("medium")
                        .targetSkill("线程池调优")
                        .expectedPoints(List.of("核心参数", "拒绝策略"))
                        .focusPoint("线程池")
                        .build();

        Method method = QuestionStreamService.class.getDeclaredMethod(
                "buildGenInput",
                InterviewSession.class,
                EvaluationDecisionOutput.NextQuestionStrategy.class,
                List.class,
                RagContext.class
        );
        method.setAccessible(true);

        QuestionGenerationInput input = (QuestionGenerationInput) method.invoke(
                service,
                session,
                strategy,
                List.of(),
                RagContext.empty()
        );

        assertEquals("无外部参考资料，请严格依赖你自身的工程师知识库进行出题。", input.getRagContext());
        assertEquals("L3", input.getDifficulty());
    }
}
