package com.studyplanner.profile;

import java.util.Comparator;
import java.util.List;

public final class DurationEstimator {

    public static final int MINIMUM_SAMPLE_COUNT = 3;
    public static final double MINIMUM_MULTIPLIER = 0.5;
    public static final double MAXIMUM_MULTIPLIER = 2.0;

    private static final double PRIOR_MULTIPLIER = 1.0;
    private static final int PRIOR_WEIGHT = 2;

    public DurationEstimate estimate(
            int originalMinutes,
            List<DurationSample> samples,
            boolean remainingManuallyEdited) {
        if (originalMinutes <= 0) {
            throw new IllegalArgumentException("originalMinutes must be positive");
        }
        if (samples == null) {
            throw new IllegalArgumentException("samples is required");
        }

        List<DurationSample> orderedSamples = samples.stream()
                .sorted(Comparator.comparing(DurationSample::evidenceId))
                .toList();
        List<String> evidenceIds = orderedSamples.stream()
                .map(DurationSample::evidenceId)
                .toList();
        if (remainingManuallyEdited || orderedSamples.size() < MINIMUM_SAMPLE_COUNT) {
            return new DurationEstimate(originalMinutes, 1.0, orderedSamples.size(), evidenceIds);
        }

        double observedRatioSum = orderedSamples.stream()
                .mapToDouble(sample -> (double) sample.actualMinutes() / sample.estimatedMinutes())
                .sum();
        double smoothed = (observedRatioSum + PRIOR_MULTIPLIER * PRIOR_WEIGHT)
                / (orderedSamples.size() + PRIOR_WEIGHT);
        double multiplier = Math.clamp(smoothed, MINIMUM_MULTIPLIER, MAXIMUM_MULTIPLIER);
        int adjustedMinutes = Math.max(1, Math.toIntExact(Math.round(originalMinutes * multiplier)));
        return new DurationEstimate(adjustedMinutes, multiplier, orderedSamples.size(), evidenceIds);
    }

    public record DurationSample(
            int estimatedMinutes,
            int actualMinutes,
            String evidenceId) {

        public DurationSample {
            if (estimatedMinutes <= 0) {
                throw new IllegalArgumentException("estimatedMinutes must be positive");
            }
            if (actualMinutes <= 0) {
                throw new IllegalArgumentException("actualMinutes must be positive");
            }
            if (evidenceId == null || evidenceId.isBlank()) {
                throw new IllegalArgumentException("evidenceId is required");
            }
        }
    }

    public record DurationEstimate(
            int minutes,
            double multiplier,
            int sampleCount,
            List<String> evidenceIds) {

        public DurationEstimate {
            if (minutes <= 0) {
                throw new IllegalArgumentException("minutes must be positive");
            }
            if (!Double.isFinite(multiplier)
                    || multiplier < MINIMUM_MULTIPLIER
                    || multiplier > MAXIMUM_MULTIPLIER) {
                throw new IllegalArgumentException("multiplier must be between 0.5 and 2.0");
            }
            if (sampleCount < 0) {
                throw new IllegalArgumentException("sampleCount must not be negative");
            }
            if (evidenceIds == null || evidenceIds.size() != sampleCount) {
                throw new IllegalArgumentException("evidenceIds must match sampleCount");
            }
            evidenceIds = List.copyOf(evidenceIds);
        }
    }
}
