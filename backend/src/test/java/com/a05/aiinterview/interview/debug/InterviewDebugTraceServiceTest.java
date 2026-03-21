package com.a05.aiinterview.interview.debug;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewDebugTraceServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordQuestionStage_shouldWriteSingleTraceFileAndSummaryLog() throws Exception {
        InterviewDebugTraceService service = new InterviewDebugTraceService(
                objectMapper, tempDir, true, false, 20
        );
        ListAppender<ILoggingEvent> appender = startLogCapture();

        Map<String, Object> evaluationPayload = new LinkedHashMap<>();
        evaluationPayload.put("systemPrompt", "system prompt should be removed");
        evaluationPayload.put("userPrompt", "user prompt should be removed");
        evaluationPayload.put("rawResponse", "123456789012345678901234567890");
        evaluationPayload.put("parsedOutput", Map.of("interviewAction", "CONTINUE"));

        service.recordQuestionStage(
                123L,
                45L,
                "attempt-abc",
                "evaluationOutput",
                evaluationPayload,
                Map.of("action", "CONTINUE", "nextType", "PROJECT_DEEP_DIVE", "nextFocus", "缓存击穿")
        );

        service.recordQuestionStage(
                123L,
                45L,
                "attempt-abc",
                "ledgerPatch",
                Map.of(
                        "currentFocus", "缓存击穿",
                        "nextFocus", "缓存雪崩",
                        "diff", Map.of("covered_points", List.of("Redis / 缓存击穿"))
                ),
                Map.of("currentFocus", "缓存击穿", "nextFocus", "缓存雪崩", "diffKeys", "covered_points")
        );

        Path snapshotFile = tempDir.resolve("session-123").resolve("session-123_q-45_attempt-attempt-abc.json");
        assertThat(Files.exists(snapshotFile)).isTrue();

        JsonNode root = objectMapper.readTree(Files.readString(snapshotFile));
        assertThat(root.path("traceId").asText()).isEqualTo("session-123_q-45_attempt-attempt-abc");
        assertThat(root.path("sessionId").asLong()).isEqualTo(123L);
        assertThat(root.path("questionId").asLong()).isEqualTo(45L);
        assertThat(root.path("attemptId").asText()).isEqualTo("attempt-abc");
        assertThat(root.path("stages").has("evaluationOutput")).isTrue();
        assertThat(root.path("stages").has("ledgerPatch")).isTrue();
        assertThat(root.toString()).doesNotContain("systemPrompt");
        assertThat(root.toString()).doesNotContain("userPrompt");
        assertThat(root.path("stages").path("evaluationOutput").path("rawResponse").asText())
                .endsWith("...(truncated)");

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][evaluation-output]")
                && msg.contains("traceId=session-123_q-45_attempt-attempt-abc")
                && msg.contains("action=CONTINUE")
                && msg.contains(snapshotFile.toString()));
        assertThat(messages).anyMatch(msg -> msg.contains("[INTERVIEW-DEBUG][ledger-patch]")
                && msg.contains("diffKeys=covered_points"));
    }

    @Test
    void recordPlannerStage_shouldDoNothingWhenDisabled() {
        InterviewDebugTraceService service = new InterviewDebugTraceService(
                objectMapper, tempDir, false, false, 100
        );
        ListAppender<ILoggingEvent> appender = startLogCapture();

        service.recordPlannerStage(
                77L,
                "plannerInput",
                Map.of("positionCode", "JAVA_BACKEND"),
                Map.of("domainsCount", 3)
        );

        Path snapshotFile = tempDir.resolve("session-77").resolve("session-77_planner.json");
        assertThat(Files.exists(snapshotFile)).isFalse();
        assertThat(appender.list).isEmpty();
    }

    private ListAppender<ILoggingEvent> startLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(InterviewDebugTraceService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
