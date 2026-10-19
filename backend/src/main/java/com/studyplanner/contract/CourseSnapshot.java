package com.studyplanner.contract;

import java.util.List;

public record CourseSnapshot(
        String id,
        String name,
        List<TimeBlock> meetingBlocks) {

    public CourseSnapshot {
        id = ContractSupport.requireText(id, "id");
        name = ContractSupport.requireText(name, "name");
        meetingBlocks = ContractSupport.immutableList(meetingBlocks, "meetingBlocks");
    }
}
