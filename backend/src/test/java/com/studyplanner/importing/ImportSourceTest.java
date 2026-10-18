package com.studyplanner.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImportSourceTest {

    @Test
    void rejectsMissingRequestKeyAndEmptyContent() {
        assertThatThrownBy(() -> new ImportSource(
                " ",
                ImportSourceType.SCREENSHOT,
                "capture.png",
                new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestKey is required");

        assertThatThrownBy(() -> new ImportSource(
                "request-1",
                ImportSourceType.AUDIO,
                "voice.m4a",
                new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content must not be empty");
    }

    @Test
    void copiesContentAtTheBoundary() {
        byte[] original = new byte[]{1, 2};
        ImportSource source = new ImportSource("request-1", ImportSourceType.SCREENSHOT, "capture.png", original);

        original[0] = 9;
        byte[] returned = source.content();
        returned[1] = 8;

        assertThat(source.content()).containsExactly(1, 2);
        assertThat(source.contentLength()).isEqualTo(2);
    }
}
