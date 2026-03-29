package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlannerHistoryBuilderService {

    static final int RECENT_HISTORY_LIMIT = 3;
    static final int RECENT_HISTORY_DAYS = 30;
    static final int RECENT_PROJECT_HISTORY_LIMIT = 2;
    private static final String PROJECT_QUESTION_TYPE = "PROJECT_DEEP_DIVE";

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;

    public record ProjectIdentity(String itemType, String itemName) {
        public ProjectIdentity {
            itemType = normalize(itemType);
            itemName = trim(itemName);
        }
    }

    public List<PlannerInput.HistoryInterviewItem> buildRecentHistory(InterviewSession currentSession) {
        return buildRecentHistory(currentSession, LocalDateTime.now());
    }

    List<PlannerInput.HistoryInterviewItem> buildRecentHistory(InterviewSession currentSession, LocalDateTime now) {
        if (!isValidCurrentSession(currentSession)) {
            return List.of();
        }

        List<InterviewSession> knowledgeSessions = selectKnowledgeHistorySessions(currentSession, now);
        List<InterviewSession> projectSessions = selectProjectHistorySessions(currentSession);
        Map<Long, List<PlannerInput.HistoryExperienceItem>> discussedItemsBySession =
                buildDiscussedItemsBySession(projectSessions);

        LinkedHashMap<Long, InterviewSession> sessionsById = new LinkedHashMap<>();
        knowledgeSessions.stream()
                .filter(Objects::nonNull)
                .forEach(session -> sessionsById.put(session.getId(), session));
        projectSessions.stream()
                .filter(Objects::nonNull)
                .filter(session -> discussedItemsBySession.containsKey(session.getId()))
                .forEach(session -> sessionsById.putIfAbsent(session.getId(), session));

        return sessionsById.values().stream()
                .sorted((left, right) -> compareSessions(right, left))
                .map(session -> toHistoryInterviewItem(session, discussedItemsBySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    public List<String> buildCrossSessionBlockedKnowledgePoints(InterviewSession currentSession) {
        return buildCrossSessionBlockedKnowledgePoints(currentSession, LocalDateTime.now());
    }

    List<String> buildCrossSessionBlockedKnowledgePoints(InterviewSession currentSession, LocalDateTime now) {
        if (!isValidCurrentSession(currentSession)) {
            return List.of();
        }
        LinkedHashSet<String> blocked = new LinkedHashSet<>();
        for (InterviewSession session : selectKnowledgeHistorySessions(currentSession, now)) {
            blocked.addAll(extractCoveredKnowledgePoints(session.getStateLedgerJson()));
        }
        return new ArrayList<>(blocked);
    }

    public Map<ProjectIdentity, List<String>> buildBlockedProjectEntryPoints(InterviewSession currentSession) {
        if (!isValidCurrentSession(currentSession)) {
            return Map.of();
        }
        return aggregateProjectEntryPoints(selectProjectHistorySessions(currentSession));
    }

    private boolean isValidCurrentSession(InterviewSession currentSession) {
        return currentSession != null
                && currentSession.getUserId() != null
                && currentSession.getPositionCode() != null
                && !currentSession.getPositionCode().isBlank();
    }

    private List<InterviewSession> selectKnowledgeHistorySessions(InterviewSession currentSession, LocalDateTime now) {
        return defaultSessions(interviewSessionMapper.selectPlannerRecentSessions(
                currentSession.getUserId(),
                currentSession.getPositionCode(),
                null,
                now.minusDays(RECENT_HISTORY_DAYS),
                currentSession.getId(),
                RECENT_HISTORY_LIMIT
        )).stream()
                .filter(session -> !extractCoveredKnowledgePoints(session.getStateLedgerJson()).isEmpty())
                .toList();
    }

    private List<InterviewSession> selectProjectHistorySessions(InterviewSession currentSession) {
        return defaultSessions(interviewSessionMapper.selectPlannerRecentSessions(
                currentSession.getUserId(),
                currentSession.getPositionCode(),
                null,
                null,
                currentSession.getId(),
                RECENT_PROJECT_HISTORY_LIMIT
        ));
    }

    private List<InterviewSession> defaultSessions(List<InterviewSession> sessions) {
        return sessions == null ? List.of() : sessions;
    }

    private PlannerInput.HistoryInterviewItem toHistoryInterviewItem(
            InterviewSession session,
            List<PlannerInput.HistoryExperienceItem> discussedItems) {
        return PlannerInput.HistoryInterviewItem.builder()
                .roundType("")
                .interviewAt(resolveInterviewAt(session))
                .coveredKnowledgePoints(extractCoveredKnowledgePoints(session.getStateLedgerJson()))
                .discussedItems(discussedItems)
                .strongPoints(List.of())
                .weakPoints(List.of())
                .build();
    }

    private Map<Long, List<PlannerInput.HistoryExperienceItem>> buildDiscussedItemsBySession(List<InterviewSession> projectSessions) {
        if (projectSessions == null || projectSessions.isEmpty()) {
            return Map.of();
        }
        List<Long> sessionIds = projectSessions.stream()
                .map(InterviewSession::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }

        List<InterviewQuestion> projectQuestions = interviewQuestionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestion>()
                        .in(InterviewQuestion::getSessionId, sessionIds)
                        .eq(InterviewQuestion::getQuestionType, PROJECT_QUESTION_TYPE)
                        .orderByAsc(InterviewQuestion::getSessionId)
                        .orderByAsc(InterviewQuestion::getQuestionNo)
                        .orderByAsc(InterviewQuestion::getId)
        );
        if (projectQuestions == null || projectQuestions.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<Long, LinkedHashMap<ProjectIdentity, LinkedHashSet<String>>> grouped = new LinkedHashMap<>();
        for (InterviewQuestion question : projectQuestions) {
            if (question == null || question.getSessionId() == null) {
                continue;
            }
            Map<String, Object> context = question.getGenerationContextJson();
            if (context == null || context.isEmpty()) {
                continue;
            }
            ProjectIdentity identity = new ProjectIdentity(
                    asString(context.get("activeItemType")),
                    asString(context.get("activeItemName"))
            );
            String projectPoint = trim(asString(context.get("projectPoint")));
            if (identity.itemType().isBlank() || identity.itemName().isBlank() || projectPoint.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(question.getSessionId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(identity, ignored -> new LinkedHashSet<>())
                    .add(projectPoint);
        }

        LinkedHashMap<Long, List<PlannerInput.HistoryExperienceItem>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, LinkedHashMap<ProjectIdentity, LinkedHashSet<String>>> sessionEntry : grouped.entrySet()) {
            List<PlannerInput.HistoryExperienceItem> discussedItems = sessionEntry.getValue().entrySet().stream()
                    .map(entry -> PlannerInput.HistoryExperienceItem.builder()
                            .itemType(entry.getKey().itemType())
                            .itemName(entry.getKey().itemName())
                            .entryPoints(new ArrayList<>(entry.getValue()))
                            .build())
                    .toList();
            result.put(sessionEntry.getKey(), discussedItems);
        }
        return result;
    }

    private Map<ProjectIdentity, List<String>> aggregateProjectEntryPoints(List<InterviewSession> projectSessions) {
        Map<Long, List<PlannerInput.HistoryExperienceItem>> discussedItemsBySession = buildDiscussedItemsBySession(projectSessions);
        if (discussedItemsBySession.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<ProjectIdentity, LinkedHashSet<String>> aggregated = new LinkedHashMap<>();
        for (List<PlannerInput.HistoryExperienceItem> discussedItems : discussedItemsBySession.values()) {
            if (discussedItems == null) {
                continue;
            }
            for (PlannerInput.HistoryExperienceItem discussedItem : discussedItems) {
                if (discussedItem == null) {
                    continue;
                }
                ProjectIdentity identity = new ProjectIdentity(discussedItem.getItemType(), discussedItem.getItemName());
                if (identity.itemType().isBlank() || identity.itemName().isBlank()) {
                    continue;
                }
                aggregated.computeIfAbsent(identity, ignored -> new LinkedHashSet<>())
                        .addAll(safeStrings(discussedItem.getEntryPoints()));
            }
        }

        LinkedHashMap<ProjectIdentity, List<String>> result = new LinkedHashMap<>();
        aggregated.forEach((identity, entryPoints) -> result.put(identity, new ArrayList<>(entryPoints)));
        return result;
    }

    private List<String> extractCoveredKnowledgePoints(Map<String, Object> stateLedgerJson) {
        if (stateLedgerJson == null) {
            return List.of();
        }
        Object rawCoveredPoints = stateLedgerJson.get("covered_points");
        if (!(rawCoveredPoints instanceof List<?> coveredPoints)) {
            return List.of();
        }
        return coveredPoints.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> safeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String resolveInterviewAt(InterviewSession session) {
        LocalDateTime timestamp = resolveTimestamp(session);
        return timestamp == null ? "" : timestamp.toString();
    }

    private int compareSessions(InterviewSession left, InterviewSession right) {
        LocalDateTime leftTs = resolveTimestamp(left);
        LocalDateTime rightTs = resolveTimestamp(right);
        if (leftTs != null && rightTs != null) {
            int byTime = leftTs.compareTo(rightTs);
            if (byTime != 0) {
                return byTime;
            }
        } else if (leftTs != null) {
            return 1;
        } else if (rightTs != null) {
            return -1;
        }
        Long leftId = left != null ? left.getId() : null;
        Long rightId = right != null ? right.getId() : null;
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return -1;
        }
        if (rightId == null) {
            return 1;
        }
        return leftId.compareTo(rightId);
    }

    private LocalDateTime resolveTimestamp(InterviewSession session) {
        if (session == null) {
            return null;
        }
        if (session.getFinishedAt() != null) {
            return session.getFinishedAt();
        }
        if (session.getUpdatedAt() != null) {
            return session.getUpdatedAt();
        }
        return session.getCreatedAt();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return trim(value).toUpperCase();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
