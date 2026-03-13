package com.a05.aiinterview.ai.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.prompt.ClasspathPromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OpenAiClient prompt tests")
class OpenAiClientPromptTest {

    @Test
    @DisplayName("callPlanner should render prompt from template and return prompt metadata")
    void callPlanner_shouldRenderPromptFromTemplate() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {
                  "title": "Java 后端模拟面试",
                  "questionMixPlan": {"INTRO": 1},
                  "domains": [],
                  "projects": [],
                  "focusAreas": []
                }
                """));

        OpenAiClient client = new OpenAiClient(
                chatModel,
                new ClasspathPromptTemplateService(new ObjectMapper()),
                new PromptProperties(),
                new ObjectMapper()
        );

        PlannerInput input = PlannerInput.builder()
                .positionName("Java 后端开发")
                .positionCode("JAVA_BACKEND")
                .experienceLevel("SENIOR")
                .mode("professional")
                .jobDescription("负责核心交易系统开发")
                .resumeText("熟悉 JVM 和并发")
                .focusTopics("JVM")
                .domains(List.of(PlannerInput.DomainInfo.builder()
                        .domainId(1L)
                        .domainCode("jvm")
                        .domainName("JVM 原理")
                        .build()))
                .build();

        AiCallResult<PlannerOutput> result = client.callPlanner(input);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertThat(((SystemMessage) prompt.getSystemMessage()).getText())
                .contains("你的任务是：根据候选人的岗位、工作年限、JD、简历和侧重知识点");
        assertThat(((UserMessage) prompt.getUserMessage()).getText())
                .contains("岗位：Java 后端开发（JAVA_BACKEND）")
                .contains("可考察的知识域列表");
        assertThat(result.getPromptCode()).isEqualTo("planner");
        assertThat(result.getPromptVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("callIntroRewrite should render prompt from template and return prompt metadata")
    void callIntroRewrite_shouldRenderPromptFromTemplate() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("请先做一个简短的自我介绍。"));

        OpenAiClient client = new OpenAiClient(
                chatModel,
                new ClasspathPromptTemplateService(new ObjectMapper()),
                new PromptProperties(),
                new ObjectMapper()
        );

        IntroRewriteInput input = IntroRewriteInput.builder()
                .positionCode("JAVA_BACKEND")
                .experienceLevel("SENIOR")
                .mode("professional")
                .basePrompt("请先做一个自我介绍。")
                .recentPrompts(List.of("请先介绍你的技术背景。"))
                .avoidPhrases(List.of("请先介绍你的技术背景。"))
                .build();

        AiCallResult<String> result = client.callIntroRewrite(input);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertThat(((SystemMessage) prompt.getSystemMessage()).getText())
                .contains("你负责将“自我介绍首题”改写成更自然的中文面试话术");
        assertThat(((UserMessage) prompt.getUserMessage()).getText())
                .contains("【底稿】")
                .contains("请先做一个自我介绍。");
        assertThat(result.getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(result.getPromptVersion()).isEqualTo("v1");
        assertThat(result.getOutput()).isEqualTo("请先做一个简短的自我介绍。");
    }

    @Test
    @DisplayName("lite audit should contain success fields and avoid leaking full user answer")
    void audit_shouldContainSuccessFieldsAndAvoidSensitiveInput() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {
                  "domainCode": "java",
                  "depthReached": "L3",
                  "saturated": true,
                  "signal": "NEXT_DOMAIN",
                  "reasoning": "ok"
                }
                """));
        ListAppender<ILoggingEvent> appender = startLogCapture();

        OpenAiClient client = new OpenAiClient(
                chatModel,
                new ClasspathPromptTemplateService(new ObjectMapper()),
                new PromptProperties(),
                new ObjectMapper()
        );

        client.callEvaluationDecision(com.a05.aiinterview.ai.dto.EvaluationDecisionInput.builder()
                .interviewId(999L)
                .currentQuestionId(1001L)
                .currentDomainCode("java")
                .currentDomainName("Java")
                .currentQuestionType("PRINCIPLE")
                .currentTargetDepth("L3")
                .currentQuestionStem("请解释线程池")
                .answerText("这是用户完整回答，不应该出现在审计日志中")
                .build());

        String audit = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("ai_lite_audit=") && msg.contains("\"status\":\"success\""))
                .findFirst()
                .orElse("");
        assertThat(audit).contains("\"traceId\":");
        assertThat(audit).contains("\"requestId\":");
        assertThat(audit).contains("\"interviewId\":999");
        assertThat(audit).contains("\"questionId\":1001");
        assertThat(audit).contains("\"promptCode\":\"evaluation_decision\"");
        assertThat(audit).contains("\"status\":\"success\"");
        assertThat(audit).contains("\"promptVersion\":\"v1\"");
        assertThat(audit).doesNotContain("这是用户完整回答，不应该出现在审计日志中");
    }

    @Test
    @DisplayName("lite audit should contain error fields when model call fails")
    void audit_shouldContainErrorFieldsWhenModelFails() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("mock model unavailable"));
        ListAppender<ILoggingEvent> appender = startLogCapture();

        OpenAiClient client = new OpenAiClient(
                chatModel,
                new ClasspathPromptTemplateService(new ObjectMapper()),
                new PromptProperties(),
                new ObjectMapper()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.callPlanner(PlannerInput.builder()
                        .interviewId(123L)
                        .positionCode("JAVA_BACKEND")
                        .positionName("Java 后端")
                        .experienceLevel("SENIOR")
                        .mode("professional")
                        .build()))
                .isInstanceOf(RuntimeException.class);

        String audit = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("ai_lite_audit=") && msg.contains("\"status\":\"error\""))
                .findFirst()
                .orElse("");
        assertThat(audit).contains("\"interviewId\":123");
        assertThat(audit).contains("\"promptCode\":\"planner\"");
        assertThat(audit).contains("\"status\":\"error\"");
        assertThat(audit).contains("\"errorType\":\"RuntimeException\"");
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text))));
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
