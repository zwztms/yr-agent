package com.yragent.infrastructure.repository;

import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryRepository;
import com.yragent.domain.memory.MemoryType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class SqliteMemoryRepository implements MemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final MemoryRowMapper rowMapper = new MemoryRowMapper();

    public SqliteMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(MemoryFragment fragment) {
        jdbcTemplate.update(
                "INSERT INTO memory_fragment (id, type, title, content, priority, created_at, updated_at, task_id, stage, tags) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                fragment.getId(),
                fragment.getType().name(),
                fragment.getTitle(),
                fragment.getContent(),
                fragment.getPriority(),
                fragment.getCreatedAt().toString(),
                fragment.getUpdatedAt().toString(),
                fragment.getTaskId(),
                fragment.getStage(),
                tagsToString(fragment.getTags())
        );
    }

    @Override
    public Optional<MemoryFragment> findById(String id) {
        List<MemoryFragment> results = jdbcTemplate.query(
                "SELECT * FROM memory_fragment WHERE id = ?",
                rowMapper,
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void update(MemoryFragment fragment) {
        jdbcTemplate.update(
                "UPDATE memory_fragment SET title=?, content=?, priority=?, updated_at=?, stage=?, tags=? WHERE id=?",
                fragment.getTitle(),
                fragment.getContent(),
                fragment.getPriority(),
                fragment.getUpdatedAt().toString(),
                fragment.getStage(),
                tagsToString(fragment.getTags()),
                fragment.getId()
        );
    }

    @Override
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM memory_fragment WHERE id = ?", id);
    }

    @Override
    public List<MemoryFragment> findByType(MemoryType type, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_fragment WHERE type = ? ORDER BY priority DESC, created_at DESC LIMIT ?",
                rowMapper,
                type.name(),
                limit
        );
    }

    @Override
    public List<MemoryFragment> findByTaskId(String taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_fragment WHERE task_id = ? ORDER BY created_at ASC",
                rowMapper,
                taskId
        );
    }

    @Override
    public List<MemoryFragment> findByTypeAndTaskId(MemoryType type, String taskId) {
        return jdbcTemplate.query(
                "SELECT * FROM memory_fragment WHERE type = ? AND task_id = ? ORDER BY created_at ASC",
                rowMapper,
                type.name(),
                taskId
        );
    }

    @Override
    public List<MemoryFragment> searchByKeyword(String keyword, MemoryType type, int limit) {
        String likePattern = "%" + keyword + "%";
        if (type != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM memory_fragment WHERE type = ? "
                            + "AND (title LIKE ? OR content LIKE ? OR tags LIKE ?) "
                            + "ORDER BY priority DESC LIMIT ?",
                    rowMapper,
                    type.name(),
                    likePattern,
                    likePattern,
                    likePattern,
                    limit
            );
        } else {
            return jdbcTemplate.query(
                    "SELECT * FROM memory_fragment WHERE (title LIKE ? OR content LIKE ? OR tags LIKE ?) "
                            + "ORDER BY priority DESC LIMIT ?",
                    rowMapper,
                    likePattern,
                    likePattern,
                    likePattern,
                    limit
            );
        }
    }

    @Override
    public int deleteOlderThan(int days) {
        return jdbcTemplate.update(
                "DELETE FROM memory_fragment WHERE created_at < datetime('now', '-' || ? || ' days')",
                String.valueOf(days)
        );
    }

    private String tagsToString(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return String.join(",", tags);
    }

    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private class MemoryRowMapper implements RowMapper<MemoryFragment> {

        @Override
        public MemoryFragment mapRow(ResultSet rs, int rowNum) throws SQLException {
            return MemoryFragment.restore(
                    rs.getString("id"),
                    MemoryType.valueOf(rs.getString("type")),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getDouble("priority"),
                    parseInstant(rs.getString("created_at")),
                    parseInstant(rs.getString("updated_at")),
                    rs.getString("task_id"),
                    rs.getString("stage"),
                    parseTags(rs.getString("tags"))
            );
        }

        private Instant parseInstant(String value) {
            if (value == null || value.isBlank()) {
                return Instant.EPOCH;
            }
            try {
                return Instant.parse(value);
            } catch (Exception e) {
                return Instant.EPOCH;
            }
        }
    }
}
