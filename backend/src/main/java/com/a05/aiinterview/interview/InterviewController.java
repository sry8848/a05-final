package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.CreateInterviewRequest;
import com.a05.aiinterview.interview.dto.CreateInterviewResponse;
import com.a05.aiinterview.interview.dto.InterviewDetailDto;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.dto.InterviewHintRequest;
import com.a05.aiinterview.interview.dto.InterviewHintResponse;
import com.a05.aiinterview.interview.dto.InterviewQuestionReviewDto;
import com.a05.aiinterview.interview.dto.InterviewReportDto;
import com.a05.aiinterview.interview.dto.LearningRecommendationDto;
import com.a05.aiinterview.interview.dto.SkipAndNextRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.engine.AnswerSubmitService;
import com.a05.aiinterview.interview.service.InterviewHistoryService;
import com.a05.aiinterview.interview.service.InterviewQuestionReviewService;
import com.a05.aiinterview.interview.service.InterviewReportService;
import com.a05.aiinterview.interview.service.InterviewService;
import com.a05.aiinterview.interview.service.InterviewHintService;
import com.a05.aiinterview.interview.service.InterviewSkipService;
import com.a05.aiinterview.interview.service.LearningRecommendationService;
import com.a05.aiinterview.interview.service.QuestionStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * Interview session APIs.
 */
@Tag(name = "Interview Session")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewHistoryService interviewHistoryService;
    private final AnswerSubmitService answerSubmitService;
    private final InterviewHintService interviewHintService;
    private final InterviewSkipService interviewSkipService;
    private final InterviewQuestionReviewService interviewQuestionReviewService;
    private final InterviewReportService interviewReportService;
    private final LearningRecommendationService learningRecommendationService;
    private final QuestionStreamService questionStreamService;

    @Operation(summary = "Get interview history list")
    @GetMapping
    public ApiResponse<InterviewHistoryPageDto> getInterviews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "targetRole", required = false) String targetRole,
            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        return ApiResponse.ok(interviewHistoryService.list(
                userId, page, pageSize, status, targetRole, dateFrom, dateTo, sortBy, sortOrder));
    }

    @Operation(summary = "Create interview session")
    @PostMapping
    public ApiResponse<CreateInterviewResponse> createInterview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(interviewService.createInterview(userId, request));
    }

    @Operation(summary = "Get interview session detail")
    @GetMapping("/{sessionId}")
    public ApiResponse<InterviewDetailDto> getSessionDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getSessionDetail(sessionId, userId));
    }

    /**
     * 提交面试回答并获取下一步信号
     * @param userId
     * @param sessionId
     * @param request
     * @return
     */
    @Operation(summary = "Submit answer and get next-step signal")
    @PostMapping("/{sessionId}/attempts")
    public ApiResponse<SubmitAttemptResponse> submitAttempt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitAttemptRequest request) {
        return ApiResponse.ok(answerSubmitService.submitAnswer(sessionId, userId, request));
    }

    @Operation(summary = "Get interview hint")
    @PostMapping("/{sessionId}/hint")
    public ApiResponse<InterviewHintResponse> getHint(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody InterviewHintRequest request) {
        return ApiResponse.ok(interviewHintService.getHint(sessionId, request.getQuestionId(), userId));
    }

    @Operation(summary = "Skip question and continue to next")
    @PostMapping("/{sessionId}/questions/{questionId}/skip-and-next")
    public ApiResponse<SubmitAttemptResponse> skipAndNext(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @Valid @RequestBody SkipAndNextRequest request) {
        return ApiResponse.ok(interviewSkipService.skipAndNext(sessionId, questionId, userId, request));
    }

    /**
     * 流式获取下一个问题
     * @param userId
     * @param sessionId
     * @param attemptId
     * @param lastEventId
     * @return
     */
    @Operation(summary = "Stream next question by SSE")
    @GetMapping(value = "/{sessionId}/questions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQuestion(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Parameter(description = "attemptId from POST /attempts response")
            @RequestParam String attemptId,
            @RequestHeader(value = "Last-Event-ID", required = false)
            @Parameter(description = "Last SSE event id for reconnect")
            String lastEventId) {
        return questionStreamService.streamQuestion(sessionId, userId, attemptId, lastEventId);
    }

    @Operation(summary = "Finish interview and trigger report generation")
    @PostMapping("/{sessionId}/finish")
    public ApiResponse<Void> finishInterview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        interviewReportService.finishAndGenerateReport(sessionId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Get interview report")
    @GetMapping("/{sessionId}/report")
    public ApiResponse<InterviewReportDto> getReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewReportService.getReport(sessionId, userId));
    }

    @Operation(summary = "Get interview question review detail")
    @GetMapping("/{sessionId}/questions/{questionId}")
    public ApiResponse<InterviewQuestionReviewDto> getQuestionReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {
        return ApiResponse.ok(interviewQuestionReviewService.getQuestionReview(sessionId, questionId, userId));
    }

    @Operation(summary = "Get learning recommendations for report page")
    @GetMapping("/{sessionId}/report/learning-recommendations")
    public ApiResponse<LearningRecommendationDto> getLearningRecommendations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(learningRecommendationService.getRecommendations(sessionId, userId));
    }
}
