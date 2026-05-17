package com.yoman.backend.entries;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Insert-only projection of {@link Entry} — no id/createdAt yet. */
public record NewEntry(
        UUID userId,
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
