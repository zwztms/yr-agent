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

// 应用启动时自动执行建表脚本，IF NOT EXISTS 保证重复启动不会报错。
@Component
public class MemorySchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MemorySchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public MemorySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ClassPathResource resource = new ClassPathResource("sql/01-memory-schema.sql");
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            // 按分号拆开逐条执行
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                }
            }
            log.info("Memory schema initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize memory schema", e);
        }
    }
}
