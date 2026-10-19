package com.studyplanner.importing;

import java.util.List;

import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;

public final class MockImportModelAdapter implements ImportModelPort {

    @Override
    public ImportProposal propose(ImportSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        String title = switch (source.sourceType()) {
            case SCREENSHOT -> "截图导入待核对";
            case AUDIO -> "语音导入待核对";
            case TEXT -> "文字导入待核对";
        };
        return new ImportProposal(
                1,
                "proposal-" + source.requestKey(),
                "ASSIGNMENT",
                title,
                null,
                null,
                null,
                List.of(source.fileName()),
                List.of("请核对任务名称、课程和截止时间"),
                ImportStatus.REVIEW,
                List.of());
    }
}
