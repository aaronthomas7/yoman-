package com.yoman.backend;

import com.yoman.backend.entries.EntriesRepository;
import com.yoman.backend.entries.Entry;
import com.yoman.backend.entries.NewEntry;
import com.yoman.backend.structuring.StructuredEntry;
import com.yoman.backend.structuring.StructuringService;
import com.yoman.backend.transcription.TranscriptionService;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class EntriesController {

    /**
     * Hardcoded placeholder until Weekend 8 wires Supabase Auth and we read
     * the real user_id from a verified JWT. For now there's exactly one user
     * (Aaron) so a stable constant lets us still scope queries by user_id
     * for Weekend 5 (entry list).
     */
    private static final UUID PLACEHOLDER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TranscriptionService transcription;
    private final StructuringService structuring;
    private final EntriesRepository entries;

    public EntriesController(
            TranscriptionService transcription,
            StructuringService structuring,
            EntriesRepository entries) {
        this.transcription = transcription;
        this.structuring = structuring;
        this.entries = entries;
    }

    @PostMapping("/entries")
    public Entry create(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds)
            throws IOException {
        String filename = audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.m4a";
        String contentType = audio.getContentType() != null ? audio.getContentType() : "audio/m4a";

        String transcript = transcription.transcribe(audio.getBytes(), filename, contentType);
        StructuredEntry structured = structuring.structure(transcript);

        NewEntry toInsert = new NewEntry(
                PLACEHOLDER_USER_ID,
                null, // entry_date defaults to CURRENT_DATE in SQL
                transcript,
                structured.summary(),
                structured.workSection(),
                structured.personalSection(),
                structured.mood(),
                structured.peopleMentionedSafe(),
                structured.tagsSafe(),
                durationSeconds);

        return entries.insert(toInsert);
    }
}
