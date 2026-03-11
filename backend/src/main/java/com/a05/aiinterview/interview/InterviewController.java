package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.CreateInterviewRequest;
import com.a05.aiinterview.interview.dto.CreateInterviewResponse;
import com.a05.aiinterview.interview.dto.InterviewDetailDto;
import com.a05.aiinterview.interview.dto.InterviewReportDto;
import com.a05.aiinterview.interview.dto.LearningRecommendationDto;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.engine.AnswerSubmitService;
import com.a05.aiinterview.interview.service.InterviewReportService;
import com.a05.aiinterview.interview.service.InterviewService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Interview session APIs.
 */
@Tag(name = "Interview Session")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final AnswerSubmitService answerSubmitService;
    private final InterviewReportService interviewReportService;
    private final LearningRecommendationService learningRecommendationService;
    private final QuestionStreamService questionStreamService;

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

    @Operation(summary = "Get learning recommendations for report page")
    @GetMapping("/{sessionId}/report/learning-recommendations")
    public ApiResponse<LearningRecommendationDto> getLearningRecommendations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(learningRecommendationService.getRecommendations(sessionId, userId));
    }
}