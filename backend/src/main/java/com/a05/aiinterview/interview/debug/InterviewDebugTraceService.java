package com.a05.aiinterview.interview.debug;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 面试主链路调试快照服务。
 * 调试开启时：
 * 1. 控制台只输出一行摘要；
 * 2. 详细 payload 以单 trace JSON 文件形式落到 logs/interview-debug 目录。
 */
@Slf4j
@Service
public class InterviewDebugTraceService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Object> traceLocks = new ConcurrentHashMap<>();

    private Path debugRootDir;

    @Value("${interview.debug.enabled:false}")
    private boolean enabled = false;

    @Value("${interview.debug.include-prompts:false}")
    private boolean includePrompts = false;

    @Value("${interview.debug.max-text-chars:1200}")
    private int maxTextChars = 1200;

    @Autowired
    public InterviewDebugTraceService(ObjectMapper objectMapper) {
        this(objectMapper, resolveDefaultDebugRootDir(), false, false, 1200);
    }

    InterviewDebugTraceService(ObjectMapper objectMapper,
                               Path debugRootDir,
                               boolean enabled,
                               boolean includePrompts,
                               int maxTextChars) {
        this.objectMapper = objectMapper;
        this.debugRootDir = debugRootDir.toAbsolutePath().normalize();
        this.enabled = enabled;
        this.includePrompts = includePrompts;
        this.maxTextChars = maxTextChars;
    }

    public void recordPlannerStage(Long sessionId,
                                   String stageKey,
                                   Object payload,
                                   Map<String, Object> summaryFields) {
        recordStage(buildPlannerTrace(sessionId), stageKey, payload, summaryFields);
    }

    public void recordQuestionStage(Long sessionId,
                                    Long questionId,
                                    String attemptId,
                                    String stageKey,
                                    Object payload,
                                    Map<String, Object> summaryFields) {
        recordStage(buildQuestionTrace(sessionId, questionId, attemptId), stageKey, payload, summaryFields);
    }

    private void recordStage(TraceRef trace,
                             String stageKey,
                             Object payload,
                             Map<String, Object> summaryFields) {
        if (!enabled || trace == null || stageKey == null || stageKey.isBlank()) {
            return;
        }

        Object sanitizedPayload = sanitize(payload);
        Object lock = traceLocks.computeIfAbsent(trace.traceId(), ignored -> new Object());
        try {
            synchronized (lock) {
                Files.createDirectories(trace.filePath().getParent());
                LinkedHashMap<String, Object> root = readExisting(trace.filePath());
                root.putIfAbsent("traceId", trace.traceId());
                root.putIfAbsent("sessionId", trace.sessionId());
                putIfNotNull(root, "questionId", trace.questionId());
                putIfNotNull(root, "attemptId", trace.attemptId());
                root.putIfAbsent("createdAt", LocalDateTime.now().toString());
                root.put("updatedAt", LocalDateTime.now().toString());

                @SuppressWarnings("unchecked")
                Map<String, Object> stages = (Map<String, Object>) root.computeIfAbsent("stages", ignored -> new LinkedHashMap<>());
                stages.put(stageKey, sanitizedPayload);

                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
                Files.writeString(trace.filePath(), prettyJson);
            }
            log.info(buildSummary(stageKey, trace, summaryFields));
        } catch (Exception e) {
            log.warn("写入面试调试快照失败, traceId={}, stageKey={}, file={}",
                    trace.traceId(), stageKey, trace.filePath(), e);
        }
    }

    private LinkedHashMap<String, Object> readExisting(Path filePath) throws IOException {
        if (!Files.exists(filePath) || Files.size(filePath) == 0L) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(Files.readString(filePath), MAP_TYPE);
    }

    private String buildSummary(String stageKey,
                                TraceRef trace,
                                Map<String, Object> summaryFields) {
        StringBuilder builder = new StringBuilder();
        builder.append("[INTERVIEW-DEBUG][")
                .append(toKebabCase(stageKey))
                .append("] ")
                .append("traceId=").append(trace.traceId())
                .append(" sessionId=").append(trace.sessionId());
        if (trace.questionId() != null) {
            builder.append(" questionId=").append(trace.questionId());
        }
        if (trace.attemptId() != null && !trace.attemptId().isBlank()) {
            builder.append(" attemptId=").append(trace.attemptId());
        }
        if (summaryFields != null) {
            for (Map.Entry<String, Object> entry : summaryFields.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                builder.append(' ')
                        .append(entry.getKey())
                        .append('=')
                        .append(summaryValue(entry.getValue()));
            }
        }
        builder.append(" file=").append(trace.filePath());
        return builder.toString();
    }

    private Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence chars) {
            return clip(chars.toString());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!includePrompts && isPromptKey(key)) {
                    continue;
                }
                sanitized.put(key, sanitize(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                sanitized.add(sanitize(Array.get(value, i)));
            }
            return sanitized;
        }
        try {
            Object converted = objectMapper.convertValue(value, Object.class);
            if (converted == value) {
                return clip(String.valueOf(value));
            }
            return sanitize(converted);
        } catch (IllegalArgumentException e) {
            return clip(String.valueOf(value));
        }
    }

    private boolean isPromptKey(String key) {
        return "systemPrompt".equals(key) || "userPrompt".equals(key);
    }

    private String summaryValue(Object value) {
        Object sanitized = sanitize(value);
        if (sanitized instanceof Collection<?> collection) {
            return clip(collection.toString());
        }
        if (sanitized instanceof Map<?, ?> map) {
            return clip(map.toString());
        }
        return sanitized == null ? "null" : String.valueOf(sanitized);
    }

    private String clip(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (maxTextChars <= 0 || text.length() <= maxTextChars) {
            return text;
        }
        return text.substring(0, maxTextChars) + "...(truncated)";
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String toKebabCase(String stageKey) {
        return stageKey.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private TraceRef buildPlannerTrace(Long sessionId) {
        String traceId = "session-" + sessionId + "_planner";
        Path filePath = debugRootDir
                .resolve("session-" + sessionId)
                .resolve(traceId + ".json");
        return new TraceRef(traceId, sessionId, null, null, filePath);
    }

    private TraceRef buildQuestionTrace(Long sessionId, Long questionId, String attemptId) {
        String traceId = "session-" + sessionId + "_q-" + questionId + "_attempt-" + attemptId;
        Path filePath = debugRootDir
                .resolve("session-" + sessionId)
                .resolve(traceId + ".json");
        return new TraceRef(traceId, sessionId, questionId, attemptId, filePath);
    }

    private static Path resolveDefaultDebugRootDir() {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("pom.xml"))) {
            return cwd.resolve("logs").resolve("interview-debug");
        }
        Path backendDir = cwd.resolve("backend");
        if (Files.exists(backendDir.resolve("pom.xml"))) {
            return backendDir.resolve("logs").resolve("interview-debug");
        }
        return cwd.resolve("logs").resolve("interview-debug");
    }

    private record TraceRef(String traceId,
                            Long sessionId,
                            Long questionId,
                            String attemptId,
                            Path filePath) {
    }
}
