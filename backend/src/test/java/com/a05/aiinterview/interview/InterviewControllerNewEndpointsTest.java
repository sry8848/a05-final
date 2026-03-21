package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptDto;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptRequest;
import com.a05.aiinterview.interview.engine.AnswerSubmitService;
import com.a05.aiinterview.interview.service.InterviewHistoryService;
import com.a05.aiinterview.interview.service.InterviewManagementService;
import com.a05.aiinterview.interview.service.InterviewQuestionReviewService;
import com.a05.aiinterview.interview.service.InterviewReportService;
import com.a05.aiinterview.interview.service.InterviewService;
import com.a05.aiinterview.interview.service.LearningRecommendationService;
import com.a05.aiinterview.interview.service.QuestionRedoService;
import com.a05.aiinterview.interview.service.QuestionStreamService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewControllerNewEndpointsTest {

    private InterviewController newController(InterviewHistoryService historyService,
                                             InterviewManagementService managementService,
                                             QuestionRedoService questionRedoService) {
        return new InterviewController(
                mock(InterviewService.class),
                historyService,
                mock(AnswerSubmitService.class),
                mock(InterviewQuestionReviewService.class),
                mock(InterviewReportService.class),
                managementService,
                mock(LearningRecommendationService.class),
                mock(QuestionStreamService.class),
                questionRedoService
        );
    }

    @Test
    void getInterviews_shouldReturnApiResponse() {
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        InterviewController controller = newController(
                historyService,
                mock(InterviewManagementService.class),
                mock(QuestionRedoService.class)
        );

        InterviewHistoryPageDto page = new InterviewHistoryPageDto();
        page.setTotal(1);
        when(historyService.list(9L, 1, 10, null, null, null, null, null, null)).thenReturn(page);

        ApiResponse<InterviewHistoryPageDto> response = controller.getInterviews(
                9L, 1, 10, null, null, null, null, null, null
        );
        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    void deleteInterview_shouldReturnWrappedResponse() {
        InterviewManagementService managementService = mock(InterviewManagementService.class);
        InterviewController controller = newController(
                mock(InterviewHistoryService.class),
                managementService,
                mock(QuestionRedoService.class)
        );

        ApiResponse<Void> response = controller.deleteInterview(9L, 101L);

        assertEquals(0, response.getCode());
    }

    @Test
    void createQuestionRedoAttempt_shouldReturnWrappedResponse() {
        QuestionRedoService questionRedoService = mock(QuestionRedoService.class);
        InterviewController controller = newController(
                mock(InterviewHistoryService.class),
                mock(InterviewManagementService.class),
                questionRedoService
        );

        QuestionRedoAttemptRequest request = new QuestionRedoAttemptRequest();
        request.setAnswerText("这是重答内容");
        QuestionRedoAttemptDto dto = new QuestionRedoAttemptDto();
        dto.setRedoAttemptId(7001L);
        dto.setEvaluationStatus("pending");
        when(questionRedoService.createRedoAttempt(1L, 2L, 9L, request)).thenReturn(dto);

        ApiResponse<QuestionRedoAttemptDto> response = controller.createQuestionRedoAttempt(9L, 1L, 2L, request);

        assertEquals(0, response.getCode());
        assertEquals(7001L, response.getData().getRedoAttemptId());
        assertEquals("pending", response.getData().getEvaluationStatus());
    }

    @Test
    void getLatestQuestionRedoAttempt_shouldReturnWrappedResponse() {
        QuestionRedoService questionRedoService = mock(QuestionRedoService.class);
        InterviewController controller = newController(
                mock(InterviewHistoryService.class),
                mock(InterviewManagementService.class),
                questionRedoService
        );

        QuestionRedoAttemptDto dto = new QuestionRedoAttemptDto();
        dto.setRedoAttemptId(7002L);
        dto.setEvaluationStatus("ready");
        when(questionRedoService.getLatestRedoAttempt(1L, 2L, 9L)).thenReturn(dto);

        ApiResponse<QuestionRedoAttemptDto> response = controller.getLatestQuestionRedoAttempt(9L, 1L, 2L);

        assertEquals(0, response.getCode());
        assertEquals(7002L, response.getData().getRedoAttemptId());
        assertEquals("ready", response.getData().getEvaluationStatus());
    }
}
