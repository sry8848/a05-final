package com.a05.aiinterview.interview.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class PlannerFocusPointSupport {

    private PlannerFocusPointSupport() {
    }

    static List<String> sanitizeFocusPoints(List<String> focusPoints) {
        if (focusPoints == null) {
            return List.of();
        }
        return focusPoints.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    static List<String> buildFallbackFocusPoints(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        String[] parts = description.trim().split("[、，,；;]");
        List<String> focusPoints = new ArrayList<>(2);
        for (String part : parts) {
            String candidate = part == null ? "" : part.trim();
            if (candidate.isBlank()) {
                continue;
            }
            focusPoints.add(candidate);
            if (focusPoints.size() >= 2) {
                break;
            }
        }
        return focusPoints;
    }

    static String normalizeForHistoryMatch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim()
                .replace('\u3000', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
