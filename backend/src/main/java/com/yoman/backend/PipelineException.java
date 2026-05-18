package com.yoman.backend;

/**
 * Thrown by any stage of POST /entries (transcription, structuring,
 * persistence) so the controller can surface a structured error with
 * which stage failed instead of Spring Boot's default 500 page.
 *
 * Mapped to an HTTP response by {@link GlobalExceptionHandler}.
 */
public class PipelineException extends RuntimeException {

    public enum Stage {
        TRANSCRIPTION,
        STRUCTURING,
        PERSISTENCE
    }

    private final Stage stage;
    private final boolean timeout;

    public PipelineException(Stage stage, String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.timeout = timeout;
    }

    public Stage stage() {
        return stage;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
