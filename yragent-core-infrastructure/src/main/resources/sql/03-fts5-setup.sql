CREATE VIRTUAL TABLE IF NOT EXISTS memory_fts USING fts5(
    id UNINDEXED,
    zone,
    title,
    content,
    tags,
    tokenize='unicode61'
);

CREATE TRIGGER IF NOT EXISTS memory_fts_insert AFTER INSERT ON memory_fragment BEGIN
    INSERT INTO memory_fts(id, zone, title, content, tags)
    VALUES (new.id, COALESCE(new.zone, new.type), new.title, new.content, new.tags);
END;

CREATE TRIGGER IF NOT EXISTS memory_fts_delete AFTER DELETE ON memory_fragment BEGIN
    DELETE FROM memory_fts WHERE id = old.id;
END;

CREATE TRIGGER IF NOT EXISTS memory_fts_update AFTER UPDATE ON memory_fragment BEGIN
    DELETE FROM memory_fts WHERE id = old.id;
    INSERT INTO memory_fts(id, zone, title, content, tags)
    VALUES (new.id, COALESCE(new.zone, new.type), new.title, new.content, new.tags);
END;
