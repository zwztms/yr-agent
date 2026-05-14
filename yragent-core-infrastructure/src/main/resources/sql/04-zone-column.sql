ALTER TABLE memory_fragment ADD COLUMN zone TEXT DEFAULT NULL;

UPDATE memory_fragment SET zone = 'PREFERENCE' WHERE type IN ('USER_PREFERENCE', 'PROJECT_POLICY') AND zone IS NULL;
UPDATE memory_fragment SET zone = 'EXPERIENCE' WHERE type = 'FAILURE_PATTERN' AND zone IS NULL;
UPDATE memory_fragment SET zone = 'DECISION' WHERE type IN ('DECISION', 'GATE_ATTEMPT') AND zone IS NULL;
UPDATE memory_fragment SET zone = 'ENTITY' WHERE type = 'TASK_STATE' AND zone IS NULL;
