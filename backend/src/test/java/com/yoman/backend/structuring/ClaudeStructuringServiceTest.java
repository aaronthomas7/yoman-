package com.yoman.backend.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yoman.backend.PipelineException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeStructuringServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ClaudeStructuringService service(String effort, String thinking) {
        return new ClaudeStructuringService(
                mapper, "sk-test", "claude-sonnet-5-5", 4096,
                "https://api.anthropic.com/v1", 1000, 1000, effort, thinking);
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void requestEndsWithUserMessageAndAsksForMinimalThinking() {
        ObjectNode req = service("low", "between_tools").buildRequest("Had dosa with Amma.");

        assertEquals("claude-sonnet-5-5", req.path("model").asText());
        assertEquals(4096, req.path("max_tokens").asInt());
        assertEquals(1, req.path("messages").size());
        assertEquals("user", req.path("messages").get(0).path("role").asText());
        assertTrue(req.path("messages").get(0).path("content").asText().contains("Had dosa with Amma."));
        assertEquals("between_tools", req.path("thinking").path("type").asText());
        assertEquals("low", req.path("output_config").path("effort").asText());
        assertFalse(req.has("temperature"));
    }

    @Test
    void emptySettingsAreLeftOutOfTheRequest() {
        ObjectNode req = service("", " ").buildRequest("x");

        assertFalse(req.has("thinking"));
        assertFalse(req.has("output_config"));
    }

    @Test
    void readsTextBlocksAndSkipsAThinkingBlockThatComesFirst() throws Exception {
        JsonNode res = json("""
                {"stop_reason":"end_turn","content":[
                  {"type":"thinking","thinking":"","signature":"abc"},
                  {"type":"text","text":"{\\"summary\\":\\"You met Biju.\\",\\"work_section\\":null,\\"personal_section\\":\\"Lunch\\",\\"mood\\":\\"calm\\",\\"people_mentioned\\":[\\"Biju\\"],\\"tags\\":[\\"family\\"]}"}
                ]}""");

        StructuredEntry e = service("low", "between_tools").parseResponse(res);

        assertEquals("You met Biju.", e.summary());
        assertNull(e.workSection());
        assertEquals("Lunch", e.personalSection());
        assertEquals(List.of("Biju"), e.peopleMentioned());
        assertEquals(List.of("family"), e.tags());
    }

    @Test
    void toleratesAMarkdownFenceAndExtraKeys() throws Exception {
        JsonNode res = json("""
                {"stop_reason":"end_turn","content":[{"type":"text","text":"```json\\n{\\"summary\\":\\"ok\\",\\"people_mentioned\\":[],\\"tags\\":[],\\"thread\\":\\"x\\"}\\n```"}]}""");

        StructuredEntry e = service("low", "between_tools").parseResponse(res);

        assertEquals("ok", e.summary());
        assertTrue(e.tags().isEmpty());
    }

    @Test
    void missingListsBecomeEmpty() throws Exception {
        JsonNode res = json("""
                {"stop_reason":"end_turn","content":[{"type":"text","text":"{\\"summary\\":null}"}]}""");

        StructuredEntry e = service("low", "between_tools").parseResponse(res);

        assertEquals("", e.summary());
        assertTrue(e.peopleMentioned().isEmpty());
        assertTrue(e.tags().isEmpty());
    }

    @Test
    void cutOffReplyIsAClearStructuringError() throws Exception {
        JsonNode res = json("""
                {"stop_reason":"max_tokens","content":[{"type":"text","text":"{\\"summary\\":\\"You spent"}]}""");

        PipelineException ex = assertThrows(PipelineException.class,
                () -> service("low", "between_tools").parseResponse(res));
        assertTrue(ex.getMessage().contains("max_tokens"));
        assertEquals(PipelineException.Stage.STRUCTURING, ex.stage());
    }

    @Test
    void refusalIsAClearStructuringError() throws Exception {
        JsonNode res = json("""
                {"stop_reason":"refusal","content":[]}""");

        PipelineException ex = assertThrows(PipelineException.class,
                () -> service("low", "between_tools").parseResponse(res));
        assertTrue(ex.getMessage().contains("declined"));
        assertEquals(PipelineException.Stage.STRUCTURING, ex.stage());
    }

    @Test
    void replyWithoutJsonIsAClearStructuringError() throws Exception {
        JsonNode res = json("""
                {"stop_reason":"end_turn","content":[{"type":"text","text":"Sorry, I can't."}]}""");

        PipelineException ex = assertThrows(PipelineException.class,
                () -> service("low", "between_tools").parseResponse(res));
        assertTrue(ex.getMessage().contains("no JSON"));
    }

    @Test
    void extractJsonFindsTheOutermostObject() {
        assertEquals("{\"a\":{\"b\":1}}", ClaudeStructuringService.extractJson("Here: {\"a\":{\"b\":1}} done"));
        assertNull(ClaudeStructuringService.extractJson("no braces"));
        assertNull(ClaudeStructuringService.extractJson(null));
    }
}
