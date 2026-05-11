package com.yragent.domain.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreferenceSerializerTest {

    private final PreferenceSerializer serializer = new PreferenceSerializer();

    @Test
    void shouldSerializeAndDeserializeFullPreference() {
        UserPreference original = UserPreference.of(
                "aggressive", "batch", "en", "brief", 10, "2026-05-05T12:00:00Z"
        );

        String json = serializer.serialize(original);
        UserPreference restored = serializer.deserialize(json);

        assertEquals("aggressive", restored.getRiskTolerance());
        assertEquals("batch", restored.getConfirmationMode());
        assertEquals("en", restored.getDisplayLanguage());
        assertEquals("brief", restored.getDisplayDetail());
        assertEquals(10, restored.getMaxToolsPerTask());
    }

    @Test
    void shouldSerializeAndDeserializeDefaults() {
        UserPreference original = UserPreference.defaults();

        String json = serializer.serialize(original);
        UserPreference restored = serializer.deserialize(json);

        assertEquals("balanced", restored.getRiskTolerance());
        assertEquals("explicit", restored.getConfirmationMode());
        assertEquals("zh", restored.getDisplayLanguage());
        assertEquals("detailed", restored.getDisplayDetail());
        assertEquals(5, restored.getMaxToolsPerTask());
    }

    @Test
    void shouldUseDefaultsWhenFieldsAreMissing() {
        String json = "{}";

        UserPreference restored = serializer.deserialize(json);

        // 所有字段缺失时应使用默认值。
        assertEquals("balanced", restored.getRiskTolerance());
        assertEquals("explicit", restored.getConfirmationMode());
        assertEquals("zh", restored.getDisplayLanguage());
        assertEquals("detailed", restored.getDisplayDetail());
        assertEquals(5, restored.getMaxToolsPerTask());
    }
}
