package com.yragent.domain.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicySerializerTest {

    private final PolicySerializer serializer = new PolicySerializer();

    @Test
    void shouldSerializeAndDeserializeFullPolicy() {
        ProjectPolicy original = ProjectPolicy.of(
                "java",
                List.of("node_modules", ".git", "target"),
                "mvn clean package",
                "mvn test",
                List.of("使用 Google Java Style"),
                true,
                "2026-05-05T12:00:00Z"
        );

        String json = serializer.serialize(original);
        ProjectPolicy restored = serializer.deserialize(json);

        assertEquals("java", restored.getProjectType());
        assertEquals(3, restored.getDirectoryExclusions().size());
        assertTrue(restored.getDirectoryExclusions().contains("node_modules"));
        assertEquals("mvn clean package", restored.getBuildCommand());
        assertEquals("mvn test", restored.getTestCommand());
        assertEquals(1, restored.getCodeStyleNotes().size());
        assertTrue(restored.isAllowNetworkAccess());
    }

    @Test
    void shouldSerializeAndDeserializeDefaults() {
        ProjectPolicy original = ProjectPolicy.defaults();

        String json = serializer.serialize(original);
        ProjectPolicy restored = serializer.deserialize(json);

        assertEquals("generic", restored.getProjectType());
        assertTrue(restored.getDirectoryExclusions().isEmpty());
        assertEquals(null, restored.getBuildCommand());
        assertEquals(null, restored.getTestCommand());
        assertTrue(restored.getCodeStyleNotes().isEmpty());
        assertFalse(restored.isAllowNetworkAccess());
    }

    @Test
    void shouldUseDefaultsWhenFieldsAreMissing() {
        String json = "{}";

        ProjectPolicy restored = serializer.deserialize(json);

        assertEquals("generic", restored.getProjectType());
        assertTrue(restored.getDirectoryExclusions().isEmpty());
        assertFalse(restored.isAllowNetworkAccess());
    }
}
