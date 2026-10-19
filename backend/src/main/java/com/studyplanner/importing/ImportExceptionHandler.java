package com.studyplanner.importing;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ImportExceptionHandler {

    @ExceptionHandler(ImportNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(ImportNotFoundException exception) {
        return Map.of(
                "code", "IMPORT_NOT_FOUND",
                "message", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidRequest(RuntimeException exception) {
        return Map.of(
                "code", "INVALID_IMPORT_REQUEST",
                "message", exception.getMessage());
    }
}
