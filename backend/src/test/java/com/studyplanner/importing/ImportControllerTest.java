package com.studyplanner.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.contract.ImportProposal;
import com.studyplanner.contract.ImportStatus;

class ImportControllerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryImportCommitPort commits = new InMemoryImportCommitPort();
    private final AtomicInteger calls = new AtomicInteger();
    private final ImportService service = new ImportService(source -> {
        if (calls.incrementAndGet() == 1) throw new IllegalStateException("model temporarily unavailable");
        return new MockImportModelAdapter().propose(source);
    }, new InMemoryImportJobRepository(), commits,
            Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void uploadRetryReviewValidationAndIdempotentConfirmationWorkThroughHttp() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ImportController(service))
                .setControllerAdvice(new ImportExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        mvc.perform(multipart("/api/v1/imports")
                .file(file()).param("sourceType", "SCREENSHOT").header("X-Request-Key", "http-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(multipart("/api/v1/imports")
                .file(file()).param("sourceType", "SCREENSHOT").header("X-Request-Key", "http-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"))
                .andExpect(jsonPath("$.errors").isEmpty());
        ImportProposal unresolved = service.get("http-test").proposal();
        mvc.perform(post("/api/v1/imports/http-test/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(unresolved)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMPORT_REQUEST"));
        assertThat(commits.committedProposals()).isEmpty();
        ImportProposal corrected = new ImportProposal(1, unresolved.proposalId(), "ASSIGNMENT", "Homework", null,
                Instant.parse("2026-10-20T00:00:00Z"), 60, unresolved.sourceEvidence(), List.of(),
                ImportStatus.REVIEW, List.of());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/imports/http-test/confirm").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(corrected)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"))
                    .andExpect(jsonPath("$.errors").isEmpty());
        }
        assertThat(commits.committedProposals()).hasSize(1);
        assertThat(calls).hasValue(2);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "sample.png", "image/png", new byte[]{1});
    }
}
