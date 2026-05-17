package com.yoman.backend.entries;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Entry(
        UUID id,
        UUID userId,
        Instant createdAt,
        LocalDate entryDate,
        String transcript,
        String summary,
        String workSection,
        String personalSection,
        String mood,
        List<String> peopleMentioned,
        List<String> tags,
        Integer durationSeconds) {
}
