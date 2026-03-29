package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageRequest;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageResponse;
import com.a05.aiinterview.interview.dto.QuestionConsultMessageDto;
import com.a05.aiinterview.interview.engine.AnswerSubmitService;
import com.a05.aiinterview.interview.service.InterviewHistoryService;
import com.a05.aiinterview.interview.service.InterviewManagementService;
import com.a05.aiinterview.interview.service.InterviewQuestionReviewService;
import com.a05.aiinterview.interview.service.InterviewReportService;
import com.a05.aiinterview.interview.service.InterviewService;
import com.a05.aiinterview.interview.service.LearningRecommendationService;
import com.a05.aiinterview.interview.service.QuestionConsultService;
import com.a05.aiinterview.interview.service.QuestionRedoService;
import com.a05.aiinterview.interview.service.QuestionStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewControllerAiConsultTest {

    private InterviewController newController(QuestionConsultService questionConsultService) {
        return new InterviewController(
                mock(InterviewService.class),
                mock(InterviewHistoryService.class),
                mock(AnswerSubmitService.class),
                mock(InterviewQuestionReviewService.class),
                mock(InterviewReportService.class),
                mock(InterviewManagementService.class),
                mock(LearningRecommendationService.class),
                mock(QuestionStreamService.class),
                mock(QuestionRedoService.class),
                questionConsultService
        );
    }

    @Test
    void getQuestionConsultMessages_shouldReturnWrappedResponse() {
        QuestionConsultService consultService = mock(QuestionConsultService.class);
        InterviewController controller = newController(consultService);

        QuestionConsultMessageDto dto = new QuestionConsultMessageDto();
        dto.setId(101L);
        dto.setRole("assistant");
        dto.setStatus("ready");
        dto.setContent("建议先补齐边界条件。");
        dto.setCreatedAt(LocalDateTime.of(2026, 3, 29, 15, 0));
        when(consultService.listMessages(1L, 2L, 9L)).thenReturn(List.of(dto));

        ApiResponse<List<QuestionConsultMessageDto>> response =
                controller.getQuestionConsultMessages(9L, 1L, 2L);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("assistant", response.getData().getFirst().getRole());
    }

    @Test
    void createQuestionConsultMessage_shouldReturnWrappedResponse() {
        QuestionConsultService consultService = mock(QuestionConsultService.class);
        InterviewController controller = newController(consultService);

        CreateQuestionConsultMessageRequest request = new CreateQuestionConsultMessageRequest();
        request.setContent("为什么这题失分？");

        CreateQuestionConsultMessageResponse responseDto = new CreateQuestionConsultMessageResponse();
        responseDto.setUserMessageId(201L);
        responseDto.setAssistantMessageId(202L);
        when(consultService.createMessage(1L, 2L, 9L, request)).thenReturn(responseDto);

        ApiResponse<CreateQuestionConsultMessageResponse> response =
                controller.createQuestionConsultMessage(9L, 1L, 2L, request);

        assertEquals(0, response.getCode());
        assertEquals(201L, response.getData().getUserMessageId());
        assertEquals(202L, response.getData().getAssistantMessageId());
    }

    @Test
    void streamQuestionConsultMessage_shouldDelegateToService() {
        QuestionConsultService consultService = mock(QuestionConsultService.class);
        InterviewController controller = newController(consultService);

        Flux<ServerSentEvent<String>> stream = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("start")
                        .data("{\"assistantMessageId\":202}")
                        .build()
        );
        when(consultService.streamAssistantMessage(1L, 2L, 202L, 9L)).thenReturn(stream);

        ServerSentEvent<String> event = controller
                .streamQuestionConsultMessage(9L, 1L, 2L, 202L)
                .blockFirst();

        assertNotNull(event);
        assertEquals("start", event.event());
    }
}
