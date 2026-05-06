package com.yragent.domain.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicySerializer {

    private static final Logger log = LoggerFactory.getLogger(PolicySerializer.class);

    private final ObjectMapper objectMapper;

    public PolicySerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String serialize(ProjectPolicy policy) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("projectType", policy.getProjectType());
            root.set("directoryExclusions", objectMapper.valueToTree(policy.getDirectoryExclusions()));
            root.put("buildCommand", policy.getBuildCommand());
            root.put("testCommand", policy.getTestCommand());
            root.set("codeStyleNotes", objectMapper.valueToTree(policy.getCodeStyleNotes()));
            root.put("allowNetworkAccess", policy.isAllowNetworkAccess());
            root.put("savedAt", policy.getSavedAt());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize ProjectPolicy", e);
            throw new IllegalStateException("序列化项目策略失败", e);
        }
    }

    public ProjectPolicy deserialize(String json) {
        try {
            var root = objectMapper.readTree(json);
            return ProjectPolicy.of(
                    root.path("projectType").asText("generic"),
                    parseStringList(root.path("directoryExclusions")),
                    root.path("buildCommand").asText(null),
                    root.path("testCommand").asText(null),
                    parseStringList(root.path("codeStyleNotes")),
                    root.path("allowNetworkAccess").asBoolean(false),
                    root.path("savedAt").asText(null)
            );
        } catch (Exception e) {
            log.error("Failed to deserialize ProjectPolicy", e);
            throw new IllegalStateException("反序列化项目策略失败", e);
        }
    }

    private List<String> parseStringList(com.fasterxml.jackson.databind.JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : arrayNode) {
                if (!item.isNull()) {
                    values.add(item.asText(""));
                }
            }
        }
        return values;
    }
}
