package com.yragent.domain.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PreferenceSerializer {

    private static final Logger log = LoggerFactory.getLogger(PreferenceSerializer.class);

    private final ObjectMapper objectMapper;

    public PreferenceSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String serialize(UserPreference preference) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("riskTolerance", preference.getRiskTolerance());
            root.put("confirmationMode", preference.getConfirmationMode());
            root.put("displayLanguage", preference.getDisplayLanguage());
            root.put("displayDetail", preference.getDisplayDetail());
            root.put("maxToolsPerTask", preference.getMaxToolsPerTask());
            root.put("savedAt", preference.getSavedAt());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize UserPreference", e);
            throw new IllegalStateException("序列化开发者偏好失败", e);
        }
    }

    public UserPreference deserialize(String json) {
        try {
            var root = objectMapper.readTree(json);
            return UserPreference.of(
                    root.path("riskTolerance").asText("balanced"),
                    root.path("confirmationMode").asText("explicit"),
                    root.path("displayLanguage").asText("zh"),
                    root.path("displayDetail").asText("detailed"),
                    root.path("maxToolsPerTask").asInt(5),
                    root.path("savedAt").asText(null)
            );
        } catch (Exception e) {
            log.error("Failed to deserialize UserPreference", e);
            throw new IllegalStateException("反序列化开发者偏好失败", e);
        }
    }
}
