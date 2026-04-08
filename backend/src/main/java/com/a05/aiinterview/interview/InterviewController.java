package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.CreateInterviewRequest;
import com.a05.aiinterview.interview.dto.CreateInterviewResponse;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageRequest;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageResponse;
import com.a05.aiinterview.interview.dto.InterviewDetailDto;
import com.a05.aiinterview.interview.dto.InterviewHistoryPageDto;
import com.a05.aiinterview.interview.dto.InterviewQuestionReviewDto;
import com.a05.aiinterview.interview.dto.InterviewReportDto;
import com.a05.aiinterview.interview.dto.LearningRecommendationDto;
import com.a05.aiinterview.interview.dto.QuestionConsultMessageDto;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptDto;
import com.a05.aiinterview.interview.dto.QuestionRedoAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI面试会话控制器
 *
 * 本控制器提供完整的面试会话管理API，包括：
 * - 面试会话创建与历史记录查询
 * - 面试问题流式推送（SSE）
 * - 回答提交与AI决策
 * - 面试报告生成与查看
 * - 题目重做功能
 * - AI题目咨询功能
 *
 * 所有接口均需要用户登录认证，用户ID通过@AuthenticationPrincipal注解从JWT令牌中提取
 *
 * @author AI Interview Team
 * @version 1.0
 */
@Tag(name = "Interview Session", description = "AI面试会话管理接口")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    /**
     * 核心面试服务
     * 负责面试会话的创建、详情查询、状态管理以及状态账本维护
     */
    private final InterviewService interviewService;

    /**
     * 面试历史服务
     * 负责分页查询用户的面试历史记录，支持多维度筛选和排序
     */
    private final InterviewHistoryService interviewHistoryService;

    /**
     * 回答提交服务
     * 负责处理用户提交的面试回答，调用AI引擎进行评估并返回下一步决策
     */
    private final AnswerSubmitService answerSubmitService;

    /**
     * 面试题目回顾服务
     * 负责提供单个题目的详细信息，包括题目内容、用户回答、AI评价等
     */
    private final InterviewQuestionReviewService interviewQuestionReviewService;

    /**
     * 面试报告服务
     * 负责生成、存储和查询面试报告，包括整体评价、能力雷达图等
     */
    private final InterviewReportService interviewReportService;

    /**
     * 面试管理服务
     * 负责面试会话的删除、归档等管理操作
     */
    private final InterviewManagementService interviewManagementService;

    /**
     * 学习推荐服务
     * 基于面试表现生成个性化的学习资源推荐，帮助用户提升技能
     */
    private final LearningRecommendationService learningRecommendationService;

    /**
     * 问题流式推送服务
     * 通过Server-Sent Events（SSE）技术流式推送面试问题给客户端
     */
    private final QuestionStreamService questionStreamService;

    /**
     * 题目重做服务
     * 允许用户对特定题目进行重新回答，用于强化练习
     */
    private final QuestionRedoService questionRedoService;

    /**
     * 题目咨询服务
     * 提供AI辅助的题目咨询服务，用户可以针对特定题目提问并获得解答
     */
    private final QuestionConsultService questionConsultService;

    /**
     * 获取用户的面试历史记录列表
     *
     * 支持多维度筛选、分页和排序功能，便于用户查看和管理自己的面试历史
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param page 页码，从1开始，默认为1
     * @param pageSize 每页记录数，默认为10
     * @param status 可选参数，筛选特定状态的面试（如进行中、已完成、已放弃）
     * @param positionCode 可选参数，按岗位代码筛选面试记录
     * @param dateFrom 可选参数，筛选指定开始时间之后的面试记录
     * @param dateTo 可选参数，筛选指定结束时间之前的面试记录
     * @param sortBy 可选参数，指定排序字段（如创建时间、评分等）
     * @param sortOrder 可选参数，指定排序顺序（asc或desc）
     * @return 包含分页信息的面试历史记录列表
     */
    @Operation(summary = "获取面试历史记录列表", description = "分页查询用户的面试历史，支持多维度筛选和排序")
    @GetMapping
    public ApiResponse<InterviewHistoryPageDto> getInterviews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "positionCode", required = false) String positionCode,
            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        return ApiResponse.ok(interviewHistoryService.list(
                userId, page, pageSize, status, positionCode, dateFrom, dateTo, sortBy, sortOrder));
    }

    /**
     * 创建新的AI面试会话
     *
     * 用户发起一次新的面试练习，系统会根据请求参数初始化面试场景，
     * 包括选择岗位类型、面试难度等配置
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param request 创建面试的请求参数，包含岗位信息、难度配置等
     * @return 创建成功的面试会话信息，包括sessionId等关键标识
     */
    @Operation(summary = "创建面试会话", description = "初始化一个新的AI面试会话，返回会话ID和初始配置信息")
    @PostMapping
    public ApiResponse<CreateInterviewResponse> createInterview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(interviewService.createInterview(userId, request));
    }

    /**
     * 获取面试会话的详细信息
     *
     * 返回指定面试会话的完整信息，包括当前进度、已回答的问题列表、
     * 总体状态等，用于前端展示面试详情页面
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @return 面试会话的详细信息和当前状态
     */
    @Operation(summary = "获取面试会话详情", description = "查询指定面试会话的完整信息，包括进度、问题列表等")
    @GetMapping("/{sessionId}")
    public ApiResponse<InterviewDetailDto> getSessionDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getSessionDetail(sessionId, userId));
    }

    /**
     * 提交用户回答并获取AI下一步决策
     *
     * 用户提交对当前问题的回答后，系统会调用AI引擎进行实时评估，
     * AI根据回答质量决定下一步操作：继续提问、追问细节、或结束面试
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param request 包含用户回答内容的请求对象
     * @return AI的评估结果及下一步决策，包含是否继续、追问方向等
     */
    @Operation(summary = "提交回答并获取下一步决策", description = "用户提交回答后，AI评估并返回继续追问或结束面试等决策")
    @PostMapping("/{sessionId}/attempts")
    public ApiResponse<SubmitAttemptResponse> submitAttempt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitAttemptRequest request) {
        return ApiResponse.ok(answerSubmitService.submitAnswer(sessionId, userId, request));
    }

    /**
     * 通过SSE流式获取下一个面试问题
     *
     * 使用Server-Sent Events技术将AI生成的问题流式推送给客户端，
     * 提供实时的问题推送体验，支持断线重连（通过Last-Event-ID）
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param attemptId 当前答题记录的ID，用于关联问题和回答
     * @param lastEventId 可选参数，SSE重连时使用的最后事件ID，确保不遗漏或重复问题
     * @return SSE事件流，包含问题内容事件和结束事件
     */
    @Operation(summary = "流式获取面试问题（SSE）", description = "通过Server-Sent Events流式推送AI生成的面试问题")
    @GetMapping(value = "/{sessionId}/questions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQuestion(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Parameter(description = "当前答题记录的ID，来自POST /attempts接口响应")
            @RequestParam String attemptId,
            @RequestHeader(value = "Last-Event-ID", required = false)
            @Parameter(description = "SSE重连时使用的最后事件ID，确保问题推送的连续性")
            String lastEventId) {
        return questionStreamService.streamQuestion(sessionId, userId, attemptId, lastEventId);
    }

    /**
     * 结束面试并触发报告生成
     *
     * 用户主动结束面试时调用此接口，系统会停止面试流程，
     * 并异步触发面试报告的生成，包括能力评估、改进建议等
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @return 操作结果（无具体返回数据）
     */
    @Operation(summary = "结束面试并生成报告", description = "用户完成面试后调用，触发异步报告生成流程")
    @PostMapping("/{sessionId}/finish")
    public ApiResponse<Void> finishInterview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        interviewReportService.finishAndGenerateReport(sessionId, userId);
        return ApiResponse.ok(null);
    }

    /**
     * 获取面试报告
     *
     * 查看指定面试会话的完整报告，包括：
     * - 整体评分和能力雷达图
     * - 各维度（技术知识、表达能力、逻辑思维等）的详细评价
     * - 改进建议和学习资源推荐
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @return 包含评分、评价、建议等完整面试报告
     */
    @Operation(summary = "获取面试报告", description = "查询指定面试会话的完整评估报告")
    @GetMapping("/{sessionId}/report")
    public ApiResponse<InterviewReportDto> getReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewReportService.getReport(sessionId, userId));
    }

    /**
     * 删除指定的面试会话
     *
     * 用户可以删除不需要的面试记录，系统会执行软删除或硬删除操作
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @return 操作结果（无具体返回数据）
     */
    @Operation(summary = "删除面试会话", description = "删除指定的面试会话记录")
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteInterview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        interviewManagementService.deleteInterview(sessionId, userId);
        return ApiResponse.ok(null);
    }

    /**
     * 获取特定题目的回顾详情
     *
     * 查看面试中某个具体题目的完整信息，包括：
     * - 题目内容和要求
     * - 用户的原始回答
     * - AI的评估和反馈
     * - 改进建议
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param questionId 面试题目的唯一标识ID
     * @return 题目的详细信息和AI评价
     */
    @Operation(summary = "获取题目回顾详情", description = "查看面试中某个题目的详细信息和AI评价")
    @GetMapping("/{sessionId}/questions/{questionId}")
    public ApiResponse<InterviewQuestionReviewDto> getQuestionReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {
        return ApiResponse.ok(interviewQuestionReviewService.getQuestionReview(sessionId, questionId, userId));
    }

    /**
     * 获取学习资源推荐
     *
     * 基于用户在面试中的表现和薄弱环节，智能推荐适合的学习资源，
     * 帮助用户针对性地提升技能
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @return 个性化的学习资源推荐列表
     */
    @Operation(summary = "获取学习推荐", description = "基于面试表现获取个性化的学习资源推荐")
    @GetMapping("/{sessionId}/report/learning-recommendations")
    public ApiResponse<LearningRecommendationDto> getLearningRecommendations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(learningRecommendationService.getRecommendations(sessionId, userId));
    }

    /**
     * 创建题目重做尝试
     *
     * 用户可以选择对某个题目进行重新回答，系统会记录重做历史，
     * 用于对比进步情况和强化练习
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param questionId 要重做的题目ID
     * @param request 重做尝试的请求参数
     * @return 创建的重做记录信息
     */
    @Operation(summary = "创建题目重做", description = "针对特定题目创建重新回答的尝试记录")
    @PostMapping("/{sessionId}/questions/{questionId}/redo-attempts")
    public ApiResponse<QuestionRedoAttemptDto> createQuestionRedoAttempt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionRedoAttemptRequest request) {
        return ApiResponse.ok(questionRedoService.createRedoAttempt(sessionId, questionId, userId, request));
    }

    /**
     * 获取题目的最新重做尝试
     *
     * 查看某个题目最近一次的重做记录，包括重做后的回答和AI评价
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param questionId 面试题目的唯一标识ID
     * @return 该题目的最新重做记录
     */
    @Operation(summary = "获取最新重做记录", description = "查询某个题目的最近一次重做尝试")
    @GetMapping("/{sessionId}/questions/{questionId}/redo-attempts/latest")
    public ApiResponse<QuestionRedoAttemptDto> getLatestQuestionRedoAttempt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {
        return ApiResponse.ok(questionRedoService.getLatestRedoAttempt(sessionId, questionId, userId));
    }

    /**
     * 获取题目的AI咨询消息历史
     *
     * 查看针对某个题目的所有AI咨询对话记录，包括用户提问和AI解答
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param questionId 面试题目的唯一标识ID
     * @return 该题目的所有AI咨询消息列表
     */
    @Operation(summary = "获取AI咨询消息历史", description = "查询针对特定题目的所有AI咨询对话记录")
    @GetMapping("/{sessionId}/questions/{questionId}/ai-consult/messages")
    public ApiResponse<List<QuestionConsultMessageDto>> getQuestionConsultMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {
        return ApiResponse.ok(questionConsultService.listMessages(sessionId, questionId, userId));
    }

    /**
     * 创建新的AI咨询消息
     *
     * 用户针对特定题目向AI助手提问，获取题目的详细解释、解题思路等
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param questionId 面试题目的唯一标识ID
     * @param request 包含用户提问内容的请求对象
     * @return AI回复的消息记录
     */
    @Operation(summary = "创建AI咨询消息", description = "向AI助手针对特定题目提问并获取解答")
    @PostMapping("/{sessionId}/questions/{questionId}/ai-consult/messages")
    public ApiResponse<CreateQuestionConsultMessageResponse> createQuestionConsultMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @Valid @RequestBody CreateQuestionConsultMessageRequest request) {
        return ApiResponse.ok(questionConsultService.createMessage(sessionId, questionId, userId, request));
    }

    /**
     * 流式获取AI咨询助手消息
     *
     * 通过SSE技术流式获取AI助手的回复，支持打字机效果，
     * 提升用户体验
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @param questionId 面试题目的唯一标识ID
     * @param assistantMessageId AI助手消息的唯一标识ID
     * @return SSE事件流，包含AI回复的增量内容
     */
    @Operation(summary = "流式获取AI咨询回复（SSE）", description = "通过Server-Sent Events流式推送AI助手的回复内容")
    @GetMapping(
            value = "/{sessionId}/questions/{questionId}/ai-consult/messages/{assistantMessageId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQuestionConsultMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @PathVariable Long assistantMessageId) {
        return questionConsultService.streamAssistantMessage(sessionId, questionId, assistantMessageId, userId);
    }

    /**
     * 获取面试状态账本（调试用）
     *
     * 返回面试会话的内部状态变更记录，用于开发调试和问题排查
     *
     * @param userId 当前登录用户的ID，从JWT认证令牌中自动提取
     * @param sessionId 面试会话的唯一标识ID
     * @return 状态变更的完整记录
     */
    @Operation(summary = "获取状态账本（调试）", description = "查看面试会话的内部状态变更记录，仅用于开发调试")
    @GetMapping("/{sessionId}/debug/ledger")
    public ApiResponse<Object> getStateLedger(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getStateLedger(sessionId, userId));
    }
}
