package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AnswerSubmitService evaluation input tests")
class AnswerSubmitServiceEvaluationInputTest {

    @Test
    @DisplayName("buildCurrentQuestionContext should keep domain and focus empty for intro question without explicit binding")
    void buildCurrentQuestionContext_shouldKeepDomainAndFocusEmptyForIntro() {
        AnswerSubmitService service = new AnswerSubmitService(
                mock(AiClient.class),
                mock(InterviewSessionMapper.class),
                mock(InterviewQuestionMapper.class),
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper())
        );

        InterviewSession session = new InterviewSession();
        session.setId(65L);
        session.setTargetRole("JAVA_BACKEND");
        session.setSyllabusJson(Map.of("domains", java.util.List.of()));
        session.setStateLedgerJson(Map.of());

        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setQuestionType("INTRO");
        question.setStem("请先做一个简短的自我介绍。");
        question.setTargetSkill("沟通表达与项目概述");

        EvaluationDecisionInput.CurrentQuestionContext currentQuestion =
                ReflectionTestUtils.invokeMethod(service, "buildCurrentQuestionContext", session, question);

        assertThat(currentQuestion.getQuestionType()).isEqualTo("INTRO");
        assertThat(currentQuestion.getDomainId()).isNull();
        assertThat(currentQuestion.getDomainName()).isBlank();
        assertThat(currentQuestion.getCurrentFocus()).isBlank();
    }
}
