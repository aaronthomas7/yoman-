package com.yoman.backend;

import com.yoman.backend.entries.EntriesRepository;
import com.yoman.backend.entries.Entry;
import com.yoman.backend.entries.NewEntry;
import com.yoman.backend.structuring.StructuredEntry;
import com.yoman.backend.structuring.StructuringService;
import com.yoman.backend.transcription.TranscriptionService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/entries")
public class EntriesController {

    private static final Logger log = LoggerFactory.getLogger(EntriesController.class);

    /**
     * Hardcoded placeholder until Weekend 8 wires Supabase Auth and we read
     * the real user_id from a verified JWT. For now there's exactly one user
     * (Aaron) so a stable constant lets us still scope queries by user_id.
     */
    private static final UUID PLACEHOLDER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final int LIST_DEFAULT_LIMIT = 100;

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

    @PostMapping
    public Entry create(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds)
            throws IOException {
        long pipelineStart = System.currentTimeMillis();
        String filename = audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.m4a";
        String contentType = audio.getContentType() != null ? audio.getContentType() : "audio/m4a";
        long audioBytes = audio.getSize();
        log.info("POST /entries received: {} bytes, contentType={}, duration={}s",
                audioBytes, contentType, durationSeconds);

        long t0 = System.currentTimeMillis();
        String transcript = transcription.transcribe(audio.getBytes(), filename, contentType);
        log.info("transcription stage finished in {} ms", System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();
        StructuredEntry structured = structuring.structure(transcript);
        log.info("structuring stage finished in {} ms", System.currentTimeMillis() - t1);

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

        long t2 = System.currentTimeMillis();
        Entry saved;
        try {
            saved = entries.insert(toInsert);
        } catch (RuntimeException e) {
            log.error("persistence stage failed after {} ms", System.currentTimeMillis() - t2, e);
            throw new PipelineException(
                    PipelineException.Stage.PERSISTENCE,
                    "Failed to save entry: " + e.getMessage(),
                    false,
                    e);
        }
        log.info("persistence stage finished in {} ms", System.currentTimeMillis() - t2);
        log.info("POST /entries finished in {} ms (id={})",
                System.currentTimeMillis() - pipelineStart, saved.id());

        return saved;
    }

    @GetMapping
    public List<Entry> list(
            @RequestParam(value = "limit", required = false, defaultValue = "" + LIST_DEFAULT_LIMIT) int limit) {
        int clamped = Math.max(1, Math.min(limit, 500));
        return entries.listForUser(PLACEHOLDER_USER_ID, clamped);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Entry> get(@PathVariable UUID id) {
        return entries.findById(id, PLACEHOLDER_USER_ID)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
