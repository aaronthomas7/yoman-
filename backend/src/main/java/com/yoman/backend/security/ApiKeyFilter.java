package com.yoman.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Temporary lock for the API until Supabase Auth lands: every request except
 * the health checks must carry the shared key in the X-Yoman-Key header.
 *
 * Deny by default. Only the exact paths in OPEN_PATHS skip the check, so any
 * endpoint added later is protected without remembering to list it.
 *
 * If YOMAN_API_KEY isn't configured, protected requests get 503 rather than
 * being let through, so a missing variable can't silently reopen the API.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Yoman-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private static final Set<String> OPEN_PATHS = Set.of("/health", "/actuator/health");

    private final byte[] expectedKey;

    public ApiKeyFilter(@Value("${yoman.api-key:}") String apiKey) {
        String key = apiKey == null ? "" : apiKey.trim();
        this.expectedKey = key.isEmpty() ? null : key.getBytes(StandardCharsets.UTF_8);
        if (expectedKey == null) {
            log.error("YOMAN_API_KEY is not set: every request except /health will get 503 until it is.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return OPEN_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expectedKey == null) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is missing YOMAN_API_KEY");
            return;
        }

        String provided = request.getHeader(HEADER);
        // MessageDigest.isEqual compares in constant time for equal-length input.
        if (provided == null
                || !MessageDigest.isEqual(expectedKey, provided.trim().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected {} {}: missing or wrong {}", request.getMethod(), request.getRequestURI(), HEADER);
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid app key");
            return;
        }

        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
