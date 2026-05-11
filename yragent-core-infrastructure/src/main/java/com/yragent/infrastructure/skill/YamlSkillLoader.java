package com.yragent.infrastructure.skill;

import com.yragent.domain.skill.SkillDefinition;
import com.yragent.domain.skill.SkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class YamlSkillLoader implements SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlSkillLoader.class);
    private static final String SKILLS_PATH = "agent/skills/*/SKILL.md";
    private final List<SkillDefinition> cache = new ArrayList<>();
    private volatile boolean loaded = false;

    @Override
    public List<SkillDefinition> loadAll() {
        if (loaded) return List.copyOf(cache);
        synchronized (this) {
            if (loaded) return List.copyOf(cache);
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources("classpath:" + SKILLS_PATH);
                for (Resource resource : resources) {
                    try {
                        String content = readResource(resource);
                        SkillDefinition skill = parseSkill(content);
                        if (skill != null) {
                            cache.add(skill);
                            log.info("Loaded skill: {}", skill.getName());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to load skill from: {}", resource.getFilename(), e);
                    }
                }
                loaded = true;
            } catch (Exception e) {
                log.warn("No skills found at classpath:{}", SKILLS_PATH, e);
                loaded = true;
            }
            return List.copyOf(cache);
        }
    }

    @Override
    public SkillDefinition load(String skillName) {
        if (!loaded) loadAll();
        return cache.stream().filter(s -> s.getName().equals(skillName)).findFirst().orElse(null);
    }

    private String readResource(Resource resource) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read skill resource: " + resource, e);
        }
        return sb.toString();
    }

    SkillDefinition parseSkill(String content) {
        String[] parts = content.split("---", 3);
        if (parts.length < 3) {
            log.warn("SKILL.md has no YAML frontmatter");
            return null;
        }
        Map<String, Object> frontmatter = parseSimpleYaml(parts[1]);
        String instruction = parts[2].trim();

        String name = stringVal(frontmatter, "name", "unknown");
        String description = stringVal(frontmatter, "description", "");
        String riskLevel = stringVal(frontmatter, "riskLevel", "MUTATING");
        List<String> requiredTools = parseStringList(frontmatter, "requiredTools");

        return new SkillDefinition(name, description, riskLevel, requiredTools, instruction);
    }

    // Simple YAML parser for flat key: value pairs and simple arrays
    private Map<String, Object> parseSimpleYaml(String yaml) {
        Map<String, Object> map = new LinkedHashMap<>();
        String currentKey = null;
        List<String> currentList = null;
        for (String line : yaml.split("\n")) {
            if (line.isBlank()) continue;
            if (line.contains(":") && !line.trim().startsWith("-")) {
                // Flush previous list
                if (currentKey != null && currentList != null) {
                    map.put(currentKey, List.copyOf(currentList));
                }
                currentKey = null;
                currentList = null;
                int colonIdx = line.indexOf(':');
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                if (value.isEmpty()) {
                    currentKey = key;
                    currentList = new ArrayList<>();
                } else {
                    // Remove surrounding brackets if array
                    if (value.startsWith("[") && value.endsWith("]")) {
                        String inner = value.substring(1, value.length() - 1).trim();
                        if (inner.isEmpty()) {
                            map.put(key, List.of());
                        } else {
                            map.put(key, Arrays.stream(inner.split(","))
                                    .map(String::trim).filter(s -> !s.isEmpty()).toList());
                        }
                    } else {
                        map.put(key, value);
                    }
                }
            } else if (line.trim().startsWith("- ") && currentKey != null) {
                if (currentList == null) currentList = new ArrayList<>();
                currentList.add(line.trim().substring(2).trim());
            }
        }
        if (currentKey != null && currentList != null) {
            map.put(currentKey, List.copyOf(currentList));
        }
        return map;
    }

    private String stringVal(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (val instanceof String str && !str.isBlank()) {
            return List.of(str);
        }
        return List.of();
    }
}
