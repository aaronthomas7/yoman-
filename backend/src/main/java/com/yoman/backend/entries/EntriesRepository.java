package com.yoman.backend.entries;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EntriesRepository {

    private static final RowMapper<Entry> ROW_MAPPER = (rs, rowNum) -> new Entry(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("entry_date", LocalDate.class),
            rs.getString("transcript"),
            rs.getString("summary"),
            rs.getString("work_section"),
            rs.getString("personal_section"),
            rs.getString("mood"),
            readStringArray(rs, "people_mentioned"),
            readStringArray(rs, "tags"),
            (Integer) rs.getObject("duration_seconds"));

    private static List<String> readStringArray(ResultSet rs, String col) throws SQLException {
        Array array = rs.getArray(col);
        if (array == null) return List.of();
        String[] values = (String[]) array.getArray();
        return values == null ? List.of() : List.of(values);
    }

    private final NamedParameterJdbcTemplate jdbc;

    public EntriesRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Entry insert(NewEntry newEntry) {
        String sql = """
                INSERT INTO entries (
                    user_id, entry_date, transcript, summary, work_section,
                    personal_section, mood, people_mentioned, tags, duration_seconds
                ) VALUES (
                    :user_id, COALESCE(:entry_date, CURRENT_DATE), :transcript, :summary,
                    :work_section, :personal_section, :mood, :people_mentioned, :tags,
                    :duration_seconds
                )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", newEntry.userId())
                .addValue("entry_date", newEntry.entryDate())
                .addValue("transcript", newEntry.transcript())
                .addValue("summary", newEntry.summary())
                .addValue("work_section", newEntry.workSection())
                .addValue("personal_section", newEntry.personalSection())
                .addValue("mood", newEntry.mood())
                .addValue("people_mentioned", newEntry.peopleMentioned().toArray(String[]::new))
                .addValue("tags", newEntry.tags().toArray(String[]::new))
                .addValue("duration_seconds", newEntry.durationSeconds());

        return jdbc.queryForObject(sql, params, ROW_MAPPER);
    }

    public List<Entry> listForUser(UUID userId, int limit) {
        String sql = """
                SELECT * FROM entries
                WHERE user_id = :user_id
                ORDER BY entry_date DESC, created_at DESC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    public Optional<Entry> findById(UUID id, UUID userId) {
        String sql = "SELECT * FROM entries WHERE id = :id AND user_id = :user_id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("user_id", userId);
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, ROW_MAPPER));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
