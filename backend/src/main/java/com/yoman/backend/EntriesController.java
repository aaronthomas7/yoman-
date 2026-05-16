package com.yoman.backend;

import com.yoman.backend.transcription.TranscriptionService;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class EntriesController {

    private final TranscriptionService transcription;

    public EntriesController(TranscriptionService transcription) {
        this.transcription = transcription;
    }

    @PostMapping("/entries")
    public Map<String, String> create(@RequestParam("audio") MultipartFile audio) throws IOException {
        String filename = audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.m4a";
        String contentType = audio.getContentType() != null ? audio.getContentType() : "audio/m4a";
        String transcript = transcription.transcribe(audio.getBytes(), filename, contentType);
        return Map.of("transcript", transcript);
    }
}
