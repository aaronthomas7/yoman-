package com.yoman.backend.transcription;

import com.yoman.backend.PipelineException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class WhisperTranscriptionService implements TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(WhisperTranscriptionService.class);

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String prompt;

    public WhisperTranscriptionService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.whisper.model}") String model,
            @Value("${openai.whisper.prompt:}") String prompt,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${openai.read-timeout-ms}") int readTimeoutMs) {
        this.apiKey = apiKey;
        this.model = model;
        this.prompt = prompt;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
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

        long start = System.currentTimeMillis();
        try {
            String response = client.post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            long elapsed = System.currentTimeMillis() - start;
            log.info("Whisper transcribe ok: {} bytes -> {} chars in {} ms",
                    audio.length, response == null ? 0 : response.length(), elapsed);
            return response == null ? "" : response.trim();

        } catch (ResourceAccessException e) {
            // ResourceAccessException wraps IO failures - notably SocketTimeoutException.
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Whisper transcribe IO failure after {} ms (timeout={}): {}",
                    elapsed, timeout, e.getMessage());
            throw new PipelineException(
                    PipelineException.Stage.TRANSCRIPTION,
                    timeout ? "Transcription timed out" : "Transcription network error: " + e.getMessage(),
                    timeout,
                    e);

        } catch (RestClientResponseException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Whisper returned HTTP {} after {} ms: {}",
                    e.getStatusCode(), elapsed, e.getResponseBodyAsString());
            throw new PipelineException(
                    PipelineException.Stage.TRANSCRIPTION,
                    "Whisper returned " + e.getStatusCode() + ": " + truncate(e.getResponseBodyAsString(), 200),
                    false,
                    e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
