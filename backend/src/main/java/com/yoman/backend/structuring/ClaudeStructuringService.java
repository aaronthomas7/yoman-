package com.yoman.backend.structuring;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Turns a raw transcript into a {@link StructuredEntry} with one Claude call.
 *
 * Written for Claude Sonnet 5.5 (claude-sonnet-5-5). Things that changed from
 * Sonnet 4.5 and that this class handles:
 *   - Assistant-message prefill (the old "{" trick) returns a 400, so the
 *     request ends with the user message and JSON-only output is asked for
 *     in the system prompt instead.
 *   - Thinking runs by default and counts toward max_tokens. The request asks
 *     for the lowest thinking setting and low effort (configurable), and the
 *     reply is read from its "text" blocks only, because a "thinking" block
 *     can come first.
 *   - stop_reason can be "refusal" or "max_tokens"; both become a clear
 *     STRUCTURING error instead of a JSON parse failure.
 */
@Service
public class ClaudeStructuringService implements StructuringService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeStructuringService.class);

    // Raw-HTTP callers are told to keep sending the version they already
    // send; 2026-10-21 changes nothing this request uses.
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    static final String SYSTEM_PROMPT = """
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
            * Output JSON ONLY. Start your reply with { and end it with }.
              No preamble, no markdown fence, no commentary.

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
    private final String effort;
    private final String thinkingType;

    public ClaudeStructuringService(
            ObjectMapper mapper,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-tokens:4096}") int maxTokens,
            @Value("${anthropic.base-url}") String baseUrl,
            @Value("${anthropic.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${anthropic.read-timeout-ms}") int readTimeoutMs,
            @Value("${anthropic.effort:}") String effort,
            @Value("${anthropic.thinking:}") String thinkingType) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.effort = effort == null ? "" : effort.trim();
        this.thinkingType = thinkingType == null ? "" : thinkingType.trim();

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
        ObjectNode request = buildRequest(transcript);

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = client.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
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

        log.info("Claude structure ok in {} ms (model={}, stop_reason={})",
                System.currentTimeMillis() - start, model,
                response == null ? null : response.path("stop_reason").asText(null));
        return parseResponse(response);
    }

    /** Builds the Messages API body. Package-private for tests. */
    ObjectNode buildRequest(String transcript) {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", model);
        request.put("max_tokens", maxTokens);
        request.put("system", SYSTEM_PROMPT);

        // The conversation must end with the user message: no prefill.
        ArrayNode messages = request.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Transcript:\n\n" + transcript);

        // Structuring a diary entry needs no deliberation. "between_tools" is
        // Sonnet 5.5's lowest thinking setting (no thinking before answering);
        // low effort keeps latency and cost down. Either can be switched off
        // with an empty ANTHROPIC_THINKING / ANTHROPIC_EFFORT.
        if (!thinkingType.isEmpty()) {
            request.putObject("thinking").put("type", thinkingType);
        }
        if (!effort.isEmpty()) {
            request.putObject("output_config").put("effort", effort);
        }
        return request;
    }

    /** Reads the structured entry out of a Messages API response. Package-private for tests. */
    StructuredEntry parseResponse(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            throw structuringError("Claude returned an empty response", null);
        }

        String stopReason = response.path("stop_reason").asText("");
        if ("refusal".equals(stopReason)) {
            throw structuringError("Claude declined to structure this entry", null);
        }

        // Thinking blocks can appear anywhere, including first, so only
        // "text" blocks are read.
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText(""));
            }
        }

        if ("max_tokens".equals(stopReason)) {
            throw structuringError(
                    "Claude's reply was cut off at max_tokens=" + maxTokens + "; raise ANTHROPIC_MAX_TOKENS", null);
        }

        String json = extractJson(text.toString());
        if (json == null) {
            throw structuringError("Claude returned no JSON: " + truncate(text.toString(), 300), null);
        }

        try {
            return normalise(mapper.readValue(json, StructuredEntry.class));
        } catch (Exception e) {
            throw structuringError("Claude returned non-parseable JSON: " + truncate(json, 300), e);
        }
    }

    /**
     * Returns the outermost {...} object in the text, tolerating a markdown
     * fence or stray words around it, or null if there is none.
     */
    static String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    private static PipelineException structuringError(String message, Throwable cause) {
        return new PipelineException(PipelineException.Stage.STRUCTURING, message, false, cause);
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
