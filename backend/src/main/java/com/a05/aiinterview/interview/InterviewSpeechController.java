package com.a05.aiinterview.interview;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.interview.dto.QuestionAudioResponse;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.speech.service.TtsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试题目语音播报接口。
 *
 * <p>提供题目音频状态查询与音频文件拉取能力，供前端在 SSE done 后执行
 * "自动播报 / 手动播放 / 静音模式" 三档策略。
 */
@Slf4j
@Tag(name = "面试语音播报")
@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewSpeechController {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final TtsService ttsService;

    /**
     * 查询题目音频是否可播放。
     *
     * @param userId 当前登录用户 ID
     * @param sessionId 面试会话 ID
     * @param questionId 题目 ID
     * @return ready + audioUrl
     */
    @Operation(summary = "查询题目播报音频状态")
    @GetMapping("/{sessionId}/questions/{questionId}/audio")
    public ApiResponse<QuestionAudioResponse> getQuestionAudio(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {
        checkOwnership(sessionId, questionId, userId);
        boolean ready = ttsService.isQuestionAudioReady(sessionId, questionId);
        String audioUrl = ready
                ? String.format("/api/v1/interviews/%d/questions/%d/audio/file", sessionId, questionId)
                : null;
        log.info("查询题目音频状态, sessionId={}, questionId={}, ready={}", sessionId, questionId, ready);
        return ApiResponse.ok(QuestionAudioResponse.builder()
                .ready(ready)
                .audioUrl(audioUrl)
                .build());
    }

    /**
     * 拉取题目音频二进制。
     *
     * @param userId 当前登录用户 ID
     * @param sessionId 面试会话 ID
     * @param questionId 题目 ID
     * @return audio/mpeg 二进制内容
     */
    @Operation(summary = "获取题目播报音频文件")
    @GetMapping("/{sessionId}/questions/{questionId}/audio/file")
    public ResponseEntity<byte[]> downloadQuestionAudio(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {
        checkOwnership(sessionId, questionId, userId);
        byte[] bytes = ttsService.getQuestionAudioBytes(sessionId, questionId);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    /**
     * 拉取题目片段音频二进制。
     */
    @Operation(summary = "获取题目片段播报音频文件")
    @GetMapping("/{sessionId}/attempts/{attemptId}/audio/segments/{segmentIndex}/file")
    public ResponseEntity<byte[]> downloadAttemptSegmentAudio(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable String attemptId,
            @PathVariable Integer segmentIndex) {
        checkSessionOwnership(sessionId, userId);
        checkAttemptOwnership(sessionId, attemptId);
        byte[] bytes = ttsService.getAttemptSegmentAudioBytes(sessionId, attemptId, segmentIndex);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    private void checkOwnership(Long sessionId, Long questionId, Long userId) {
        checkSessionOwnership(sessionId, userId);
        checkQuestionOwnership(sessionId, questionId);
    }

    private void checkSessionOwnership(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }
    }

    private void checkQuestionOwnership(Long sessionId, Long questionId) {
        InterviewQuestion question = interviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getId, questionId)
                        .eq(InterviewQuestion::getSessionId, sessionId)
        );
        if (question == null) {
            throw new IllegalArgumentException("题目不存在");
        }
    }

    private void checkAttemptOwnership(Long sessionId, String attemptId) {
        InterviewAttempt attempt = interviewAttemptMapper.selectByAttemptId(attemptId);
        if (attempt == null || !sessionId.equals(attempt.getSessionId())) {
            throw new IllegalArgumentException("作答记录不存在");
        }
    }
}
