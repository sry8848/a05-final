package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.InterviewQuestionReviewDto;
import com.a05.aiinterview.interview.engine.AnswerSubmitService;
import com.a05.aiinterview.interview.service.InterviewHistoryService;
import com.a05.aiinterview.interview.service.InterviewHintService;
import com.a05.aiinterview.interview.service.InterviewQuestionReviewService;
import com.a05.aiinterview.interview.service.InterviewReportService;
import com.a05.aiinterview.interview.service.InterviewSkipService;
import com.a05.aiinterview.interview.service.InterviewService;
import com.a05.aiinterview.interview.service.LearningRecommendationService;
import com.a05.aiinterview.interview.service.QuestionStreamService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewControllerQuestionDetailTest {

    @Test
    void getQuestionReview_shouldReturnApiResponseOk() {
        InterviewService interviewService = mock(InterviewService.class);
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        AnswerSubmitService answerSubmitService = mock(AnswerSubmitService.class);
        InterviewHintService hintService = mock(InterviewHintService.class);
        InterviewSkipService skipService = mock(InterviewSkipService.class);
        InterviewQuestionReviewService reviewService = mock(InterviewQuestionReviewService.class);
        InterviewReportService reportService = mock(InterviewReportService.class);
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
                recommendationService,
                streamService
        );

        InterviewQuestionReviewDto dto = new InterviewQuestionReviewDto();
        dto.setQuestionId(10L);
        when(reviewService.getQuestionReview(1L, 10L, 9L)).thenReturn(dto);

        ApiResponse<InterviewQuestionReviewDto> response = controller.getQuestionReview(9L, 1L, 10L);
        assertEquals(0, response.getCode());
        assertEquals(10L, response.getData().getQuestionId());
    }

    @Test
    void getQuestionReview_shouldPropagateBusinessException() {
        InterviewService interviewService = mock(InterviewService.class);
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        AnswerSubmitService answerSubmitService = mock(AnswerSubmitService.class);
        InterviewHintService hintService = mock(InterviewHintService.class);
        InterviewSkipService skipService = mock(InterviewSkipService.class);
        InterviewQuestionReviewService reviewService = mock(InterviewQuestionReviewService.class);
        InterviewReportService reportService = mock(InterviewReportService.class);
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
                recommendationService,
                streamService
        );

        when(reviewService.getQuestionReview(1L, 10L, 9L))
                .thenThrow(new IllegalArgumentException("无权访问该面试会话"));

        assertThrows(IllegalArgumentException.class,
                () -> controller.getQuestionReview(9L, 1L, 10L));
    }
}
