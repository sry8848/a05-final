package com.a05.aiinterview.rag.qdrant;

import java.util.List;
import java.util.Locale;

/**
 * 将 difficultyHint 解析为相邻一级扩窗。
 */
public class DifficultyWindowResolver {

    public List<String> resolve(String difficultyHint) {
        if (difficultyHint == null || difficultyHint.isBlank()) {
            return List.of();
        }

        String normalized = difficultyHint.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "L1" -> List.of("L1", "L2");
            case "L2" -> List.of("L1", "L2", "L3");
            case "L3" -> List.of("L2", "L3", "L4");
            case "L4" -> List.of("L3", "L4", "L5");
            case "L5" -> List.of("L4", "L5");
            default -> List.of();
        };
    }
}
