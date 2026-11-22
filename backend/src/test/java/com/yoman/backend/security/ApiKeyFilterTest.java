package com.yoman.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    private static final String KEY = "test-key-123";

    private static MockHttpServletResponse run(ApiKeyFilter filter, MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void healthIsOpenWithoutKey() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(new ApiKeyFilter(KEY), new MockHttpServletRequest("GET", "/health"), chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void entriesWithoutKeyIs401() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(new ApiKeyFilter(KEY), new MockHttpServletRequest("GET", "/entries"), chain);

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("Missing or invalid app key"));
        assertNull(chain.getRequest());
    }

    @Test
    void wrongKeyIs401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/entries");
        req.addHeader(ApiKeyFilter.HEADER, "nope");
        MockFilterChain chain = new MockFilterChain();

        assertEquals(401, run(new ApiKeyFilter(KEY), req, chain).getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void rightKeyPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/entries/7a7e7c1e-0000-0000-0000-000000000001");
        req.addHeader(ApiKeyFilter.HEADER, KEY);
        MockFilterChain chain = new MockFilterChain();

        assertEquals(200, run(new ApiKeyFilter(KEY), req, chain).getStatus());
        assertSame(req, chain.getRequest());
    }

    @Test
    void unknownPathsAreProtectedToo() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res =
                run(new ApiKeyFilter(KEY), new MockHttpServletRequest("GET", "/actuator/env"), chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void missingServerKeyFailsClosedWith503() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/entries");
        req.addHeader(ApiKeyFilter.HEADER, "anything");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse res = run(new ApiKeyFilter("  "), req, chain);

        assertEquals(503, res.getStatus());
        assertNull(chain.getRequest());
    }
}
