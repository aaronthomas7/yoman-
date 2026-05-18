package com.yoman.backend;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PipelineException.class)
    public ResponseEntity<Map<String, Object>> handlePipeline(PipelineException ex) {
        log.error("Pipeline failed at stage={} timeout={}: {}",
                ex.stage(), ex.isTimeout(), ex.getMessage(), ex.getCause());

        // 504 for timeouts upstream, 502 for any other upstream-attributable
        // failure (Whisper / Claude returned non-2xx, returned garbage, etc.).
        // Persistence failures are 500 - they're ours.
        HttpStatus status;
        if (ex.stage() == PipelineException.Stage.PERSISTENCE) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (ex.isTimeout()) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else {
            status = HttpStatus.BAD_GATEWAY;
        }

        return ResponseEntity.status(status).body(Map.of(
                "stage", ex.stage().name().toLowerCase(),
                "timeout", ex.isTimeout(),
                "message", ex.getMessage()
        ));
    }
}
