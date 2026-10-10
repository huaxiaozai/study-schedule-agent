package com.studyplanner.importing;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.studyplanner.contract.ImportProposal;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    public static final String REQUEST_KEY_HEADER = "X-Request-Key";

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportJobView upload(
            @RequestParam("sourceType") ImportSourceType sourceType,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = REQUEST_KEY_HEADER, required = false) String requestKey) throws IOException {
        String resolvedRequestKey = requestKey == null || requestKey.isBlank()
                ? UUID.randomUUID().toString()
                : requestKey;
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? sourceType.name().toLowerCase() + "-upload"
                : file.getOriginalFilename();
        return importService.submit(new ImportSource(
                resolvedRequestKey,
                sourceType,
                fileName,
                file.getBytes()));
    }

    @GetMapping("/{requestKey}")
    public ImportJobView get(@PathVariable String requestKey) {
        return importService.get(requestKey);
    }

    @PostMapping("/{requestKey}/confirm")
    public ImportJobView confirm(
            @PathVariable String requestKey,
            @RequestBody ImportProposal proposal) {
        return importService.confirm(requestKey, proposal);
    }
}
