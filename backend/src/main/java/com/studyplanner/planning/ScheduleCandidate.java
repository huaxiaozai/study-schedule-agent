package com.studyplanner.planning;

import java.util.List;

public record ScheduleCandidate(
        List<CandidateBlock> blocks,
        List<PlanningGap> gaps) {

    public ScheduleCandidate {
        if (blocks == null) {
            throw new IllegalArgumentException("blocks is required");
        }
        if (gaps == null) {
            throw new IllegalArgumentException("gaps is required");
        }
        blocks = List.copyOf(blocks);
        gaps = List.copyOf(gaps);
    }
}
