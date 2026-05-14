-- ============================================================
-- yragent 记忆系统 —— 数据库建表脚本
-- 数据库：SQLite
-- 创建时间：2026-05-05
-- 说明：本脚本由 MemorySchemaInitializer 在应用首次启动时自动执行，
--       不需要手动跑。此文件为参考文档，方便后续手动建表或迁移。
-- ============================================================

-- -----------------------------------------------------------
-- 表：memory_fragment（记忆主表）
-- 说明：统一存储所有类型的记忆（开发者偏好、项目策略、任务状态、
--       决策记录、失败模式、门禁尝试历史）。
--       不同类型通过 type 字段区分，具体内容以 JSON 存入 content。
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS memory_fragment (
    id          TEXT PRIMARY KEY NOT NULL,   -- UUID，唯一标识
    type        TEXT NOT NULL,              -- 记忆类型：USER_PREFERENCE | PROJECT_POLICY | TASK_STATE | DECISION | FAILURE_PATTERN | GATE_ATTEMPT
    title       TEXT DEFAULT '',            -- 记忆标题，用于列表展示和搜索
    content     TEXT NOT NULL,              -- 记忆主体内容（JSON 格式），不同类型字段不同
    priority    REAL DEFAULT 0.5,           -- 优先级 0.0 ~ 1.0，越高越优先注入上下文
    created_at  TEXT NOT NULL,              -- 创建时间 ISO 8601 格式，如 2026-05-05T19:00:00+08:00
    updated_at  TEXT NOT NULL,              -- 最后更新时间
    task_id     TEXT DEFAULT NULL,          -- 关联任务 ID（可为空，偏好/策略类记忆不关联任务）
    stage       TEXT DEFAULT NULL,          -- 关联阶段类型（可为空），如 GATE_CONFIRM
    tags        TEXT DEFAULT '',            -- 标签，逗号分隔，用于快速筛选，如 "授权,高风险,工具"
    zone        TEXT DEFAULT NULL           -- 记忆分区：PREFERENCE | EXPERIENCE | DECISION | ENTITY
);

-- 按类型查询索引（最常用）
CREATE INDEX IF NOT EXISTS idx_memory_type
    ON memory_fragment(type);

-- 按任务 ID 查询索引（查某任务的所有记忆）
CREATE INDEX IF NOT EXISTS idx_memory_task
    ON memory_fragment(task_id);

-- 按优先级排序索引（注入上下文时取高优先级）
CREATE INDEX IF NOT EXISTS idx_memory_priority
    ON memory_fragment(priority DESC);

-- 按创建时间排序索引（查最新记忆）
CREATE INDEX IF NOT EXISTS idx_memory_created
    ON memory_fragment(created_at DESC);

-- 按类型+任务组合查询索引
CREATE INDEX IF NOT EXISTS idx_memory_type_task
    ON memory_fragment(type, task_id);

-- 按分区查询索引
CREATE INDEX IF NOT EXISTS idx_memory_zone
    ON memory_fragment(zone);
