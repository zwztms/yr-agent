package com.yragent.repository;

import com.yragent.domain.memory.MemoryFragment;
import com.yragent.domain.memory.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRepositoryTest {

    private MemoryRepository repository;

    @BeforeEach
    void setUp() {
        // 用临时文件数据库，避免 :memory: 每次连接创建独立库的问题。
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/test-memory-repo-" + System.nanoTime() + ".db");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // 手工建表，不走 MemorySchemaInitializer
        jdbcTemplate.execute("""
                CREATE TABLE memory_fragment (
                    id TEXT PRIMARY KEY NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT DEFAULT '',
                    content TEXT NOT NULL,
                    priority REAL DEFAULT 0.5,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    task_id TEXT DEFAULT NULL,
                    stage TEXT DEFAULT NULL,
                    tags TEXT DEFAULT ''
                )
                """);
        repository = new MemoryRepository(jdbcTemplate);
    }

    @Test
    void shouldSaveAndRetrieveMemoryFragment() {
        MemoryFragment fragment = MemoryFragment.create(
                MemoryType.GATE_ATTEMPT,
                "第1轮门禁",
                "{\"status\":\"BLOCKED\"}",
                0.8,
                "task-001",
                "GATE_CONFIRM",
                List.of("门禁", "阻断")
        );
        repository.save(fragment);

        Optional<MemoryFragment> retrieved = repository.findById(fragment.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(MemoryType.GATE_ATTEMPT, retrieved.get().getType());
        assertEquals("第1轮门禁", retrieved.get().getTitle());
        assertEquals("task-001", retrieved.get().getTaskId());
        assertEquals(2, retrieved.get().getTags().size());
    }

    @Test
    void shouldFindByTypeAndSortByPriority() {
        MemoryFragment low = MemoryFragment.create(
                MemoryType.USER_PREFERENCE, "低优先级", "{}", 0.3, null, null, List.of());
        MemoryFragment high = MemoryFragment.create(
                MemoryType.USER_PREFERENCE, "高优先级", "{}", 0.9, null, null, List.of());
        repository.save(low);
        repository.save(high);

        List<MemoryFragment> results = repository.findByType(MemoryType.USER_PREFERENCE, 10);

        assertEquals(2, results.size());
        // 高优先级排前面
        assertEquals("高优先级", results.get(0).getTitle());
        assertEquals("低优先级", results.get(1).getTitle());
    }

    @Test
    void shouldFindByTaskId() {
        MemoryFragment f1 = MemoryFragment.create(
                MemoryType.TASK_STATE, "任务1状态", "{}", 0.5, "task-A", null, List.of());
        MemoryFragment f2 = MemoryFragment.create(
                MemoryType.TASK_STATE, "任务2状态", "{}", 0.5, "task-B", null, List.of());
        repository.save(f1);
        repository.save(f2);

        List<MemoryFragment> results = repository.findByTaskId("task-A");

        assertEquals(1, results.size());
        assertEquals("task-A", results.get(0).getTaskId());
    }

    @Test
    void shouldFindByTypeAndTaskId() {
        MemoryFragment f1 = MemoryFragment.create(
                MemoryType.GATE_ATTEMPT, "门禁第1轮", "{}", 0.5, "task-001", "GATE_CONFIRM", List.of());
        MemoryFragment f2 = MemoryFragment.create(
                MemoryType.TASK_STATE, "任务状态", "{}", 0.5, "task-001", null, List.of());
        repository.save(f1);
        repository.save(f2);

        List<MemoryFragment> results = repository.findByTypeAndTaskId(MemoryType.GATE_ATTEMPT, "task-001");

        assertEquals(1, results.size());
        assertEquals(MemoryType.GATE_ATTEMPT, results.get(0).getType());
    }

    @Test
    void shouldSearchByKeyword() {
        MemoryFragment f1 = MemoryFragment.create(
                MemoryType.DECISION, "日志框架选型", "使用 Logback + SLF4J", 0.8, null, null, List.of("框架"));
        MemoryFragment f2 = MemoryFragment.create(
                MemoryType.DECISION, "数据库选型", "使用 SQLite", 0.6, null, null, List.of("存储"));
        repository.save(f1);
        repository.save(f2);

        List<MemoryFragment> results = repository.searchByKeyword("Logback", MemoryType.DECISION, 10);

        assertEquals(1, results.size());
        assertEquals("日志框架选型", results.get(0).getTitle());
    }

    @Test
    void shouldSearchByTag() {
        MemoryFragment f1 = MemoryFragment.create(
                MemoryType.PROJECT_POLICY, "禁止目录", "不要动 production 配置", 0.9, null, null, List.of("安全", "禁区"));
        repository.save(f1);

        List<MemoryFragment> results = repository.searchByKeyword("禁区", null, 10);

        assertEquals(1, results.size());
        assertTrue(results.get(0).getTags().contains("禁区"));
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        List<MemoryFragment> results = repository.findByType(MemoryType.USER_PREFERENCE, 10);
        assertTrue(results.isEmpty());

        Optional<MemoryFragment> notFound = repository.findById("nonexistent");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void shouldUpdateMemoryFragment() {
        MemoryFragment fragment = MemoryFragment.create(
                MemoryType.DECISION, "原始标题", "{}", 0.5, null, null, List.of());
        repository.save(fragment);

        // 用 restore 模拟更新（保持原 id 和 createdAt，改 title 和 updatedAt）
        MemoryFragment updated = MemoryFragment.restore(
                fragment.getId(),
                fragment.getType(),
                "更新后的标题",
                fragment.getContent(),
                0.7,
                fragment.getCreatedAt(),
                java.time.Instant.now(),
                fragment.getTaskId(),
                fragment.getStage(),
                List.of("新标签")
        );
        repository.update(updated);

        Optional<MemoryFragment> retrieved = repository.findById(fragment.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("更新后的标题", retrieved.get().getTitle());
        assertEquals(0.7, retrieved.get().getPriority(), 0.001);
        assertEquals(1, retrieved.get().getTags().size());
    }

    @Test
    void shouldDeleteMemoryFragment() {
        MemoryFragment fragment = MemoryFragment.create(
                MemoryType.DECISION, "待删除", "{}", 0.5, null, null, List.of());
        repository.save(fragment);
        assertTrue(repository.findById(fragment.getId()).isPresent());

        repository.deleteById(fragment.getId());

        assertTrue(repository.findById(fragment.getId()).isEmpty());
    }
}
