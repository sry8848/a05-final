package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.QuestionConsultInput;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageRequest;
import com.a05.aiinterview.interview.dto.CreateQuestionConsultMessageResponse;
import com.a05.aiinterview.interview.dto.QuestionConsultMessageDto;
import com.a05.aiinterview.interview.dto.QuestionConsultSseDoneEvent;
import com.a05.aiinterview.interview.dto.QuestionConsultSseErrorEvent;
import com.a05.aiinterview.interview.dto.QuestionConsultSseStartEvent;
import com.a05.aiinterview.interview.dto.SseDeltaEvent;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.QuestionConsultMessage;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.QuestionConsultMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单题追问服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionConsultService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_READY = "ready";
    private static final String STATUS_GENERATING = "generating";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_CANCELLED = "cancelled";

    private final QuestionConsultMessageMapper questionConsultMessageMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public List<QuestionConsultMessageDto> listMessages(Long sessionId, Long questionId, Long userId) {
        assertAccessible(sessionId, questionId, userId);
        return questionConsultMessageMapper.selectConversation(userId, sessionId, questionId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CreateQuestionConsultMessageResponse createMessage(
            Long sessionId, Long questionId, Long userId, CreateQuestionConsultMessageRequest request) {
        assertAccessible(sessionId, questionId, userId);
        QuestionConsultMessage generating =
                questionConsultMessageMapper.selectLatestGenerating(userId, sessionId, questionId);
        if (generating != null) {
            throw new IllegalStateException("当前题目已有进行中的 AI 追问，请等待本轮完成");
        }

        QuestionConsultMessage userMessage = new QuestionConsultMessage();
        userMessage.setUserId(userId);
        userMessage.setSessionId(sessionId);
        userMessage.setQuestionId(questionId);
        userMessage.setRole(ROLE_USER);
        userMessage.setStatus(STATUS_READY);
        userMessage.setContent(request.getContent().trim());
        questionConsultMessageMapper.insert(userMessage);

        QuestionConsultMessage assistantMessage = new QuestionConsultMessage();
        assistantMessage.setUserId(userId);
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setQuestionId(questionId);
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setStatus(STATUS_GENERATING);
        assistantMessage.setReplyToMessageId(userMessage.getId());
        assistantMessage.setContent("");
        questionConsultMessageMapper.insert(assistantMessage);

        CreateQuestionConsultMessageResponse response = new CreateQuestionConsultMessageResponse();
        response.setUserMessageId(userMessage.getId());
        response.setAssistantMessageId(assistantMessage.getId());
        return response;
    }

    public Flux<ServerSentEvent<String>> streamAssistantMessage(
            Long sessionId, Long questionId, Long assistantMessageId, Long userId) {
        InterviewSession session = assertAccessible(sessionId, questionId, userId);
        InterviewQuestion question = interviewQuestionMapper.selectById(questionId);
        QuestionConsultMessage assistantMessage = questionConsultMessageMapper.selectById(assistantMessageId);
        if (assistantMessage == null
                || !Objects.equals(assistantMessage.getUserId(), userId)
                || !Objects.equals(assistantMessage.getSessionId(), sessionId)
                || !Objects.equals(assistantMessage.getQuestionId(), questionId)
                || !ROLE_ASSISTANT.equals(assistantMessage.getRole())) {
            throw new IllegalArgumentException("追问消息不存在或无权访问");
        }

        if (!STATUS_GENERATING.equals(normalizeStatus(assistantMessage.getStatus()))) {
            return Flux.concat(
                    Flux.just(buildStartEvent(assistantMessageId)),
                    Flux.just(buildDoneEvent(assistantMessageId, safeString(assistantMessage.getContent()), assistantMessage.getStatus()))
            );
        }

        List<QuestionConsultMessage> conversation =
                questionConsultMessageMapper.selectConversation(userId, sessionId, questionId);
        QuestionConsultInput input = buildInput(session, question, assistantMessage, conversation);
        StringBuilder fullContent = new StringBuilder();
        AtomicBoolean finalized = new AtomicBoolean(false);

        return Flux.concat(
                Flux.just(buildStartEvent(assistantMessageId)),
                aiClient.callQuestionConsultStream(input)
                        .map(token -> {
                            fullContent.append(token);
                            return buildDeltaEvent(token);
                        }),
                Flux.defer(() -> {
                    patchMessage(assistantMessageId, STATUS_READY, fullContent.toString(), null, finalized);
                    return Flux.just(buildDoneEvent(assistantMessageId, fullContent.toString(), STATUS_READY));
                })
        ).onErrorResume(error -> {
            String status = isCancellationLike(error) ? STATUS_CANCELLED : STATUS_FAILED;
            patchMessage(assistantMessageId, status, fullContent.toString(), error.getMessage(), finalized);
            return Flux.just(buildErrorEvent(
                    assistantMessageId,
                    STATUS_CANCELLED.equals(status) ? "当前追问已取消，请重新发起。" : "AI 追问暂时失败，请稍后重试。",
                    status
            ));
        }).doOnCancel(() -> patchMessage(
                assistantMessageId,
                STATUS_CANCELLED,
                fullContent.toString(),
                "client disconnected",
                finalized
        ));
    }

    private QuestionConsultInput buildInput(
            InterviewSession session,
            InterviewQuestion question,
            QuestionConsultMessage assistantMessage,
            List<QuestionConsultMessage> conversation) {
        InterviewAttempt attempt = interviewAttemptMapper.selectLatestFinalAttempt(session.getId(), question.getId());
        Map<String, Object> detailEvaluation = attempt != null ? attempt.getDetailEvaluationJson() : null;
        boolean readyEvaluation = attempt != null
                && "ready".equals(normalizeStatus(attempt.getDetailEvaluationStatus()))
                && detailEvaluation != null;

        List<QuestionConsultInput.ConsultTurn> history = new ArrayList<>();
        String latestUserQuestion = "";
        for (QuestionConsultMessage message : conversation) {
            String content = safeString(message.getContent()).trim();
            if (content.isBlank()) {
                continue;
            }
            history.add(QuestionConsultInput.ConsultTurn.builder()
                    .role(message.getRole())
                    .content(content)
                    .build());
            if (ROLE_USER.equals(message.getRole())) {
                latestUserQuestion = content;
            }
        }
        if (latestUserQuestion.isBlank() && assistantMessage.getReplyToMessageId() != null) {
            latestUserQuestion = conversation.stream()
                    .filter(item -> Objects.equals(item.getId(), assistantMessage.getReplyToMessageId()))
                    .map(QuestionConsultMessage::getContent)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .findFirst()
                    .orElse("");
        }

        return QuestionConsultInput.builder()
                .interviewId(session.getId())
                .questionId(question.getId())
                .assistantMessageId(assistantMessage.getId())
                .positionCode(session.getPositionCode())
                .experienceLevel(session.getExperienceLevel())
                .mode(session.getMode())
                .questionStem(question.getStem())
                .questionType(question.getQuestionType())
                .domainCode(QuestionDetailEvaluationInputFactory.extractDomainCode(question.getGenerationContextJson()))
                .domainName(resolveDomainName(session, question))
                .originalAnswerText(attempt != null ? safeString(attempt.getAnswerText()) : "")
                .evaluationScore(readyEvaluation ? readBigDecimal(detailEvaluation, "score") : null)
                .evaluationCommentary(readyEvaluation ? safeString(detailEvaluation.get("commentary")) : "")
                .strengthPoints(readyEvaluation ? readStringList(detailEvaluation, "strengthPoints") : List.of())
                .weakPoints(readyEvaluation ? readStringList(detailEvaluation, "weakPoints") : List.of())
                .idealAnswerOutline(readyEvaluation ? readStringList(detailEvaluation, "idealAnswerOutline") : List.of())
                .rewrittenAnswer(readyEvaluation ? safeString(detailEvaluation.get("rewrittenAnswer")) : "")
                .consultHistory(List.copyOf(history))
                .latestUserQuestion(latestUserQuestion)
                .build();
    }

    private String resolveDomainName(InterviewSession session, InterviewQuestion question) {
        return firstNonBlank(
                QuestionDetailEvaluationInputFactory.extractDomainName(question.getGenerationContextJson()),
                QuestionDetailEvaluationInputFactory.resolveDomainName(
                        session == null ? null : session.getSyllabusJson(),
                        QuestionDetailEvaluationInputFactory.extractDomainCode(question.getGenerationContextJson())
                ),
                question.getDomainCode()
        );
    }

    private void patchMessage(Long assistantMessageId,
                              String status,
                              String content,
                              String errorMessage,
                              AtomicBoolean finalized) {
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        QuestionConsultMessage update = new QuestionConsultMessage();
        update.setId(assistantMessageId);
        update.setStatus(status);
        update.setContent(content);
        update.setErrorMessage(errorMessage);
        questionConsultMessageMapper.updateById(update);
    }

    private InterviewSession assertAccessible(Long sessionId, Long questionId, Long userId) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该面试会话");
        }
        InterviewQuestion question = interviewQuestionMapper.selectById(questionId);
        if (question == null || !Objects.equals(question.getSessionId(), sessionId)) {
            throw new IllegalArgumentException("题目不存在或不属于该会话");
        }
        return session;
    }

    private QuestionConsultMessageDto toDto(QuestionConsultMessage message) {
        QuestionConsultMessageDto dto = new QuestionConsultMessageDto();
        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setStatus(normalizeStatus(message.getStatus()));
        dto.setContent(message.getContent());
        dto.setReplyToMessageId(message.getReplyToMessageId());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private ServerSentEvent<String> buildStartEvent(Long assistantMessageId) {
        return ServerSentEvent.<String>builder()
                .event("start")
                .data(toJson(QuestionConsultSseStartEvent.builder()
                        .assistantMessageId(assistantMessageId)
                        .build()))
                .build();
    }

    private ServerSentEvent<String> buildDeltaEvent(String text) {
        return ServerSentEvent.<String>builder()
                .event("delta")
                .data(toJson(SseDeltaEvent.builder().text(text).build()))
                .build();
    }

    private ServerSentEvent<String> buildDoneEvent(Long assistantMessageId, String content, String status) {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(toJson(QuestionConsultSseDoneEvent.builder()
                        .assistantMessageId(assistantMessageId)
                        .content(content)
                        .status(normalizeStatus(status))
                        .build()))
                .build();
    }

    private ServerSentEvent<String> buildErrorEvent(Long assistantMessageId, String message, String status) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(toJson(QuestionConsultSseErrorEvent.builder()
                        .assistantMessageId(assistantMessageId)
                        .message(message)
                        .status(normalizeStatus(status))
                        .build()))
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.error("单题追问 SSE 事件序列化失败", ex);
            return "{}";
        }
    }

    private List<String> readStringList(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(item -> !item.isBlank())
                .toList();
    }

    private BigDecimal readBigDecimal(Map<String, Object> source, String key) {
        if (source == null || source.get(key) == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof BigDecimal number) {
            return number;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCancellationLike(Throwable error) {
        String message = safeString(error == null ? null : error.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("cancel");
    }

    private String normalizeStatus(String status) {
        String normalized = safeString(status).trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? STATUS_READY : normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
