package com.a05.aiinterview.rag.qdrant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DifficultyWindowResolver tests")
class DifficultyWindowResolverTest {

    private final DifficultyWindowResolver resolver = new DifficultyWindowResolver();

    @Test
    @DisplayName("resolve should expand L1 to adjacent upper level")
    void resolve_shouldExpandL1ToAdjacentUpperLevel() {
        assertThat(resolver.resolve("L1")).containsExactly("L1", "L2");
    }

    @Test
    @DisplayName("resolve should expand L2 to adjacent window")
    void resolve_shouldExpandL2ToAdjacentWindow() {
        assertThat(resolver.resolve("L2")).containsExactly("L1", "L2", "L3");
    }

    @Test
    @DisplayName("resolve should expand L3 to adjacent window")
    void resolve_shouldExpandL3ToAdjacentWindow() {
        assertThat(resolver.resolve("L3")).containsExactly("L2", "L3", "L4");
    }

    @Test
    @DisplayName("resolve should expand L4 to adjacent window")
    void resolve_shouldExpandL4ToAdjacentWindow() {
        assertThat(resolver.resolve("L4")).containsExactly("L3", "L4", "L5");
    }

    @Test
    @DisplayName("resolve should expand L5 to adjacent lower level")
    void resolve_shouldExpandL5ToAdjacentLowerLevel() {
        assertThat(resolver.resolve("L5")).containsExactly("L4", "L5");
    }

    @Test
    @DisplayName("resolve should return empty for blank value")
    void resolve_shouldReturnEmptyForBlankValue() {
        assertThat(resolver.resolve("")).isEmpty();
    }

    @Test
    @DisplayName("resolve should return empty for unknown difficulty")
    void resolve_shouldReturnEmptyForUnknownDifficulty() {
        assertThat(resolver.resolve("UNKNOWN")).isEmpty();
    }
}
