package com.studyplanner.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DurationEstimatorTest {

    private final DurationEstimator estimator = new DurationEstimator();

    @Test
    void usesOriginalEstimateBeforeThreeSamples() {
        DurationEstimator.DurationEstimate result = estimator.estimate(
                100,
                List.of(sample(100, 150, "task-1"), sample(100, 200, "task-2")),
                false);

        assertThat(result.minutes()).isEqualTo(100);
        assertThat(result.multiplier()).isEqualTo(1.0);
        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.evidenceIds()).containsExactly("task-1", "task-2");
    }

    @Test
    void appliesSmoothedMultiplierFromThreeSamples() {
        DurationEstimator.DurationEstimate result = estimator.estimate(
                100,
                List.of(
                        sample(100, 200, "task-1"),
                        sample(100, 200, "task-2"),
                        sample(100, 200, "task-3")),
                false);

        assertThat(result.minutes()).isEqualTo(160);
        assertThat(result.multiplier()).isEqualTo(1.6);
        assertThat(result.sampleCount()).isEqualTo(3);
    }

    @Test
    void clampsMultiplierBetweenHalfAndDouble() {
        List<DurationEstimator.DurationSample> verySlow = List.of(
                sample(10, 100, "slow-1"),
                sample(10, 100, "slow-2"),
                sample(10, 100, "slow-3"));
        List<DurationEstimator.DurationSample> veryFast = List.of(
                sample(100, 1, "fast-1"),
                sample(100, 1, "fast-2"),
                sample(100, 1, "fast-3"));

        assertThat(estimator.estimate(100, verySlow, false).multiplier()).isEqualTo(2.0);
        assertThat(estimator.estimate(100, veryFast, false).multiplier()).isEqualTo(0.5);
    }

    @Test
    void doesNotApplyMultiplierAfterManualRemainingEdit() {
        DurationEstimator.DurationEstimate result = estimator.estimate(
                75,
                List.of(
                        sample(100, 200, "task-1"),
                        sample(100, 200, "task-2"),
                        sample(100, 200, "task-3")),
                true);

        assertThat(result.minutes()).isEqualTo(75);
        assertThat(result.multiplier()).isEqualTo(1.0);
        assertThat(result.sampleCount()).isEqualTo(3);
    }

    private static DurationEstimator.DurationSample sample(
            int estimatedMinutes,
            int actualMinutes,
            String evidenceId) {
        return new DurationEstimator.DurationSample(estimatedMinutes, actualMinutes, evidenceId);
    }
}
