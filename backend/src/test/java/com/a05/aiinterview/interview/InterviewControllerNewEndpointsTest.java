package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.InterviewHintRequest;
import com.a05.aiinterview.interview.dto.InterviewHintResponse;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.dto.SkipAndNextRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.engine.AnswerSubmitService;
import com.a05.aiinterview.interview.service.InterviewHintService;
import com.a05.aiinterview.interview.service.InterviewHistoryService;
import com.a05.aiinterview.interview.service.InterviewManagementService;
import com.a05.aiinterview.interview.service.InterviewQuestionReviewService;
import com.a05.aiinterview.interview.service.InterviewReportService;
import com.a05.aiinterview.interview.service.InterviewService;
import com.a05.aiinterview.interview.service.InterviewSkipService;
import com.a05.aiinterview.interview.service.LearningRecommendationService;
import com.a05.aiinterview.interview.service.QuestionStreamService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewControllerNewEndpointsTest {

    @Test
    void getInterviews_shouldReturnApiResponse() {
        InterviewService interviewService = mock(InterviewService.class);
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        AnswerSubmitService answerSubmitService = mock(AnswerSubmitService.class);
        InterviewHintService hintService = mock(InterviewHintService.class);
        InterviewSkipService skipService = mock(InterviewSkipService.class);
        InterviewQuestionReviewService reviewService = mock(InterviewQuestionReviewService.class);
        InterviewReportService reportService = mock(InterviewReportService.class);
        InterviewManagementService managementService = mock(InterviewManagementService.class);
        LearningRecommendationService recommendationService = mock(LearningRecommendationService.class);
        QuestionStreamService streamService = mock(QuestionStreamService.class);

        InterviewController controller = new InterviewController(
                interviewService,
                historyService,
                answerSubmitService,
                hintService,
                skipService,
                reviewService,
                reportService,
                managementService,
                recommendationService,
                streamService
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
    void getHint_shouldPropagateBusinessException() {
        InterviewService interviewService = mock(InterviewService.class);
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        AnswerSubmitService answerSubmitService = mock(AnswerSubmitService.class);
        InterviewHintService hintService = mock(InterviewHintService.class);
        InterviewSkipService skipService = mock(InterviewSkipService.class);
        InterviewQuestionReviewService reviewService = mock(InterviewQuestionReviewService.class);
        InterviewReportService reportService = mock(InterviewReportService.class);
        InterviewManagementService managementService = mock(InterviewManagementService.class);
        LearningRecommendationService recommendationService = mock(LearningRecommendationService.class);
        QuestionStreamService streamService = mock(QuestionStreamService.class);

        InterviewController controller = new InterviewController(
                interviewService,
                historyService,
                answerSubmitService,
                hintService,
                skipService,
                reviewService,
                reportService,
                managementService,
                recommendationService,
                streamService
        );

        InterviewHintRequest req = new InterviewHintRequest();
        req.setQuestionId(20L);
        when(hintService.getHint(1L, 20L, 9L)).thenThrow(new IllegalArgumentException("题目不存在"));

        assertThrows(IllegalArgumentException.class, () -> controller.getHint(9L, 1L, req));
    }

    @Test
    void skipAndNext_shouldReturnWrappedResponse() {
        InterviewService interviewService = mock(InterviewService.class);
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        AnswerSubmitService answerSubmitService = mock(AnswerSubmitService.class);
        InterviewHintService hintService = mock(InterviewHintService.class);
        InterviewSkipService skipService = mock(InterviewSkipService.class);
        InterviewQuestionReviewService reviewService = mock(InterviewQuestionReviewService.class);
        InterviewReportService reportService = mock(InterviewReportService.class);
        InterviewManagementService managementService = mock(InterviewManagementService.class);
        LearningRecommendationService recommendationService = mock(LearningRecommendationService.class);
        QuestionStreamService streamService = mock(QuestionStreamService.class);

        InterviewController controller = new InterviewController(
                interviewService,
                historyService,
                answerSubmitService,
                hintService,
                skipService,
                reviewService,
                reportService,
                managementService,
                recommendationService,
                streamService
        );

        SkipAndNextRequest req = new SkipAndNextRequest();
        req.setAttemptId("a-1");
        SubmitAttemptResponse skipResp = SubmitAttemptResponse.builder()
                .attemptId("a-1")
                .decision("continue")
                .streamAttemptId("a-1")
                .sessionStatus("in_progress")
                .build();
        when(skipService.skipAndNext(1L, 2L, 9L, req)).thenReturn(skipResp);

        ApiResponse<SubmitAttemptResponse> response = controller.skipAndNext(9L, 1L, 2L, req);
        assertEquals(0, response.getCode());
        assertEquals("continue", response.getData().getDecision());
    }

    @Test
    void deleteInterview_shouldReturnWrappedResponse() {
        InterviewService interviewService = mock(InterviewService.class);
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        AnswerSubmitService answerSubmitService = mock(AnswerSubmitService.class);
        InterviewHintService hintService = mock(InterviewHintService.class);
        InterviewSkipService skipService = mock(InterviewSkipService.class);
        InterviewQuestionReviewService reviewService = mock(InterviewQuestionReviewService.class);
        InterviewReportService reportService = mock(InterviewReportService.class);
        InterviewManagementService managementService = mock(InterviewManagementService.class);
        LearningRecommendationService recommendationService = mock(LearningRecommendationService.class);
        QuestionStreamService streamService = mock(QuestionStreamService.class);

        InterviewController controller = new InterviewController(
                interviewService,
                historyService,
                answerSubmitService,
                hintService,
                skipService,
                reviewService,
                reportService,
                managementService,
                recommendationService,
                streamService
        );

        ApiResponse<Void> response = controller.deleteInterview(9L, 101L);

        assertEquals(0, response.getCode());
    }
}
