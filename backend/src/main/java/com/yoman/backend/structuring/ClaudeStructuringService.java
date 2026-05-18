package com.yoman.backend.structuring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yoman.backend.PipelineException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ClaudeStructuringService implements StructuringService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeStructuringService.class);

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String SYSTEM_PROMPT = """
            You are Yoman, a warm, conservative diary-structuring assistant.

            Given a raw voice-journal transcript, produce a clean structured
            diary entry as JSON. Use a warm conversational mirror tone in second
            person ("you spent the morning..."). Use the user's first name as
            occasional direct address when one is provided.

            Strict rules:
            * Light interpretation only. Never invent details. Never moralize.
              Never diagnose moods clinically.
            * Preserve non-English terms (Malayalam, theological references,
              Indian names, project codes) verbatim.
            * If a field cannot be inferred from the transcript, return null
              for that field rather than guessing.
            * people_mentioned and tags are arrays of strings; empty array if
              none. Tags are short lowercase phrases, max 4 items.
            * Output JSON ONLY. No preamble, no markdown fence, no commentary.

            Return this exact JSON shape:
            {
              "summary": string,
              "work_section": string | null,
              "personal_section": string | null,
              "mood": string | null,
              "people_mentioned": string[],
              "tags": string[]
            }
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClaudeStructuringService(
            ObjectMapper mapper,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-tokens:1024}") int maxTokens,
            @Value("${anthropic.base-url}") String baseUrl,
            @Value("${anthropic.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${anthropic.read-timeout-ms}") int readTimeoutMs) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public StructuredEntry structure(String transcript) {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", model);
        request.put("max_tokens", maxTokens);
        request.put("system", SYSTEM_PROMPT);

        ArrayNode messages = request.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Transcript:\n\n" + transcript);

        // Prefill the assistant turn with "{" so Claude is forced to start
        // a JSON object and can't preamble.
        ObjectNode assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "{");

        long start = System.currentTimeMillis();
        ObjectNode response;
        try {
            response = client.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ObjectNode.class);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Claude IO failure after {} ms (timeout={}): {}",
                    elapsed, timeout, e.getMessage());
            throw new PipelineException(
                    PipelineException.Stage.STRUCTURING,
                    timeout ? "Structuring timed out" : "Structuring network error: " + e.getMessage(),
                    timeout,
                    e);
        } catch (RestClientResponseException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Claude returned HTTP {} after {} ms: {}",
                    e.getStatusCode(), elapsed, e.getResponseBodyAsString());
            throw new PipelineException(
                    PipelineException.Stage.STRUCTURING,
                    "Claude returned " + e.getStatusCode() + ": " + truncate(e.getResponseBodyAsString(), 200),
                    false,
                    e);
        }

        long elapsed = System.currentTimeMillis() - start;
        if (response == null) {
            throw new PipelineException(
                    PipelineException.Stage.STRUCTURING,
                    "Claude returned an empty response",
                    false,
                    null);
        }
        log.info("Claude structure ok in {} ms", elapsed);

        // Response shape: { content: [{ type: "text", text: "..." }], ... }
        String text = response.path("content").path(0).path("text").asText("");
        String json = "{" + text;
        json = json.substring(0, json.lastIndexOf('}') + 1);

        try {
            StructuredEntry parsed = mapper.readValue(json, StructuredEntry.class);
            return normalise(parsed);
        } catch (Exception e) {
            throw new PipelineException(
                    PipelineException.Stage.STRUCTURING,
                    "Claude returned non-parseable JSON: " + truncate(json, 300),
                    false,
                    e);
        }
    }

    private static StructuredEntry normalise(StructuredEntry e) {
        return new StructuredEntry(
                e.summary() == null ? "" : e.summary(),
                e.workSection(),
                e.personalSection(),
                e.mood(),
                e.peopleMentioned() == null ? List.of() : e.peopleMentioned(),
                e.tags() == null ? List.of() : e.tags());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
