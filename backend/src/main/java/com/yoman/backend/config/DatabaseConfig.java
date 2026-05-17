package com.yoman.backend.config;

import java.net.URI;
import java.net.URISyntaxException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Parses a single Heroku/Supabase-style DATABASE_URL into a JDBC DataSource.
 *
 * Supabase hands us connection strings of the form
 *   postgresql://user:password@host:port/db
 * which Spring Boot's auto-config can't consume directly. Splitting into three
 * SPRING_DATASOURCE_* env vars would work but is more fragile to copy/paste.
 * Keeping one URL line in .env and parsing it here is the lower-friction path.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(@Value("${DATABASE_URL}") String databaseUrl) throws URISyntaxException {
        URI uri = new URI(databaseUrl);
        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            throw new IllegalStateException(
                "DATABASE_URL must include user:password (got: " + maskUrl(databaseUrl) + ")");
        }
        String[] parts = userInfo.split(":", 2);
        String username = parts[0];
        String password = parts[1];

        // Supabase requires SSL. pgjdbc defaults to non-SSL, so make it
        // explicit. Honour any user-supplied query string already on the URL.
        String existingQuery = uri.getRawQuery();
        String query = (existingQuery == null || existingQuery.isBlank())
                ? "sslmode=require"
                : existingQuery + "&sslmode=require";
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort()
                + uri.getPath() + "?" + query;

        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private static String maskUrl(String url) {
        // Hide the password before logging — leave structure visible for debugging.
        return url.replaceAll("(://[^:]+:)[^@]+(@)", "$1***$2");
    }
}
