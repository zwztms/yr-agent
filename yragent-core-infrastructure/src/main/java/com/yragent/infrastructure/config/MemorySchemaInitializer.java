package com.yragent.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MemorySchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MemorySchemaInitializer.class);

    private static final List<String> MIGRATIONS = List.of(
            "sql/01-memory-schema.sql",
            "sql/03-fts5-setup.sql",
            "sql/04-zone-column.sql"
    );

    private final JdbcTemplate jdbcTemplate;

    public MemorySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String migration : MIGRATIONS) {
            executeMigration(migration);
        }
        backfillZoneColumn();
        log.info("Memory schema initialized successfully ({} migrations)", MIGRATIONS.size());
    }

    private void executeMigration(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                }
            }
            log.debug("Migration executed: {}", path);
        } catch (Exception e) {
            log.warn("Migration {} skipped: {}", path, e.getMessage());
        }
    }

    private void backfillZoneColumn() {
        try {
            jdbcTemplate.update(
                    "UPDATE memory_fragment SET zone = 'PREFERENCE' WHERE type IN ('USER_PREFERENCE', 'PROJECT_POLICY') AND zone IS NULL");
            jdbcTemplate.update(
                    "UPDATE memory_fragment SET zone = 'EXPERIENCE' WHERE type = 'FAILURE_PATTERN' AND zone IS NULL");
            jdbcTemplate.update(
                    "UPDATE memory_fragment SET zone = 'DECISION' WHERE type IN ('DECISION', 'GATE_ATTEMPT') AND zone IS NULL");
            jdbcTemplate.update(
                    "UPDATE memory_fragment SET zone = 'ENTITY' WHERE type = 'TASK_STATE' AND zone IS NULL");
            log.debug("Zone column backfill completed");
        } catch (Exception e) {
            log.debug("Zone column backfill skipped (column may not exist yet)");
        }
    }
}
