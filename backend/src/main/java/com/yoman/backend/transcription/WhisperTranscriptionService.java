package com.yoman.backend.transcription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class WhisperTranscriptionService implements TranscriptionService {

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String prompt;

    public WhisperTranscriptionService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.whisper.model}") String model,
            @Value("${openai.whisper.prompt:}") String prompt,
            @Value("${openai.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.prompt = prompt;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String transcribe(byte[] audio, String filename, String contentType) {
        ByteArrayResource fileResource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("model", model);
        body.add("response_format", "text");
        if (prompt != null && !prompt.isBlank()) {
            body.add("prompt", prompt);
        }

        String response = client.post()
                .uri("/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        return response == null ? "" : response.trim();
    }
}
