package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.ai.dto.QuestionGenerationOutput;
import com.a05.aiinterview.ai.prompt.ClasspathPromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

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
    @DisplayName("callQuestionGeneration should render prompt from template and return prompt metadata")
    void callQuestionGeneration_shouldRenderPromptFromTemplate() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {
                  "stem": "请解释线程池的核心参数。",
                  "targetSkill": "线程池",
                  "expectedPoints": ["corePoolSize"],
                  "difficulty": "medium",
                  "targetDepth": "L3"
                }
                """));

        OpenAiClient client = new OpenAiClient(
                chatModel,
                new ClasspathPromptTemplateService(new ObjectMapper()),
                new PromptProperties(),
                new ObjectMapper()
        );

        QuestionGenerationInput input = QuestionGenerationInput.builder()
                .positionCode("JAVA_BACKEND")
                .experienceLevel("SENIOR")
                .mode("professional")
                .nextDomainCode("concurrency")
                .nextDomainName("并发编程")
                .nextQuestionType("PRINCIPLE")
                .targetDepth("L3")
                .askedQuestions(List.of(QuestionGenerationInput.AskedQuestion.builder()
                        .questionId(1L)
                        .stemSummary("讲讲 synchronized")
                        .build()))
                .syllabus(Map.of("domains", List.of()))
                .build();

        AiCallResult<QuestionGenerationOutput> result = client.callQuestionGeneration(input);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertThat(((SystemMessage) prompt.getSystemMessage()).getText())
                .contains("你的任务是：根据当前考纲和出题策略，生成一道合适的面试题");
        assertThat(((UserMessage) prompt.getUserMessage()).getText())
                .contains("知识域：并发编程（concurrency）")
                .contains("已问过的题目（避免重复）");
        assertThat(result.getPromptCode()).isEqualTo("question_generation");
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
                .contains("You rewrite an interview opening prompt for self-introduction.");
        assertThat(((UserMessage) prompt.getUserMessage()).getText())
                .contains("Base prompt:")
                .contains("请先做一个自我介绍。");
        assertThat(result.getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(result.getPromptVersion()).isEqualTo("v1");
        assertThat(result.getOutput()).isEqualTo("请先做一个简短的自我介绍。");
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text))));
    }
}
