package com.yoman.backend.structuring;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Shape Claude is asked to return as JSON. Field names match the prompt
 * verbatim (snake_case) via @JsonProperty so we don't have to rely on
 * Jackson's naming-strategy config.
 *
 * Fields that may be absent or null when Claude can't infer them:
 *   work_section, personal_section, mood
 *
 * The {@code thread} field from the spec is omitted until Weekend 5+ when
 * we have recent_tags to make connections meaningful.
 */
public record StructuredEntry(
        String summary,
        @JsonProperty("work_section") String workSection,
        @JsonProperty("personal_section") String personalSection,
        String mood,
        @JsonProperty("people_mentioned") List<String> peopleMentioned,
        List<String> tags) {

    public List<String> peopleMentionedSafe() {
        return peopleMentioned == null ? List.of() : peopleMentioned;
    }

    public List<String> tagsSafe() {
        return tags == null ? List.of() : tags;
    }
}
