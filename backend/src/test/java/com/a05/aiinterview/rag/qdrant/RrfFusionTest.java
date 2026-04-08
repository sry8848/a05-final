package com.a05.aiinterview.rag.qdrant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RrfFusion tests")
class RrfFusionTest {

    private final RrfFusion fusion = new RrfFusion();

    @Test
    @DisplayName("fuse should prefer question ids hit by both branches")
    void fuse_shouldPreferQuestionIdsHitByBothBranches() {
        List<RrfFusion.FusedScore> fused = fusion.fuse(
                List.of(
                        List.of("q1", "q2", "q3"),
                        List.of("q2", "q4", "q1")
                ),
                10
        );

        assertThat(fused).extracting(RrfFusion.FusedScore::questionId)
                .containsExactly("q2", "q1", "q4", "q3");
    }

    @Test
    @DisplayName("fuse should decay single branch hits by rank")
    void fuse_shouldDecaySingleBranchHitsByRank() {
        List<RrfFusion.FusedScore> fused = fusion.fuse(
                List.of(
                        List.of("q1", "q3", "q5"),
                        List.of("q2")
                ),
                10
        );

        assertThat(fused).extracting(RrfFusion.FusedScore::questionId)
                .containsExactly("q1", "q2", "q3", "q5");
        assertThat(fused).extracting(RrfFusion.FusedScore::score)
                .isSortedAccordingTo((left, right) -> Double.compare(right, left));
    }

    @Test
    @DisplayName("fuse should sort same scores by question id for stable ordering")
    void fuse_shouldSortSameScoresByQuestionIdForStableOrdering() {
        List<RrfFusion.FusedScore> fused = fusion.fuse(
                List.of(
                        List.of("q-b"),
                        List.of("q-a")
                ),
                10
        );

        assertThat(fused).extracting(RrfFusion.FusedScore::questionId)
                .containsExactly("q-a", "q-b");
    }
}
