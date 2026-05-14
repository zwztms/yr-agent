package com.yragent.domain.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FakeMemoryRepository implements MemoryRepository {

    private final Map<String, MemoryFragment> store = new ConcurrentHashMap<>();

    @Override
    public void save(MemoryFragment fragment) {
        store.put(fragment.getId(), fragment);
    }

    @Override
    public Optional<MemoryFragment> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void update(MemoryFragment fragment) {
        store.put(fragment.getId(), fragment);
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public List<MemoryFragment> findByType(MemoryType type, int limit) {
        return store.values().stream()
                .filter(f -> f.getType() == type)
                .sorted(Comparator.comparingDouble(MemoryFragment::getPriority).reversed()
                        .thenComparing(Comparator.comparing(MemoryFragment::getCreatedAt).reversed()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryFragment> findByTaskId(String taskId) {
        return store.values().stream()
                .filter(f -> Objects.equals(f.getTaskId(), taskId))
                .sorted(Comparator.comparing(MemoryFragment::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryFragment> findByTypeAndTaskId(MemoryType type, String taskId) {
        return store.values().stream()
                .filter(f -> f.getType() == type && Objects.equals(f.getTaskId(), taskId))
                .sorted(Comparator.comparing(MemoryFragment::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryFragment> searchByKeyword(String keyword, MemoryType type, int limit) {
        String lowerKeyword = keyword.toLowerCase();
        return store.values().stream()
                .filter(f -> {
                    if (type != null && f.getType() != type) return false;
                    return f.getTitle().toLowerCase().contains(lowerKeyword)
                            || f.getContent().toLowerCase().contains(lowerKeyword)
                            || f.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lowerKeyword));
                })
                .sorted(Comparator.comparingDouble(MemoryFragment::getPriority).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public int deleteOlderThan(int days) {
        return 0;
    }

    @Override
    public List<MemoryFragment> findByZone(MemoryZone zone, int limit) {
        return store.values().stream()
                .filter(f -> f.getZone() == zone)
                .sorted(Comparator.comparingDouble(MemoryFragment::getPriority).reversed()
                        .thenComparing(Comparator.comparing(MemoryFragment::getCreatedAt).reversed()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryFragment> findByZoneAndTaskId(MemoryZone zone, String taskId) {
        return store.values().stream()
                .filter(f -> f.getZone() == zone && Objects.equals(f.getTaskId(), taskId))
                .sorted(Comparator.comparing(MemoryFragment::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryFragment> searchFts(String query, MemoryZone zone, int limit) {
        return searchByKeyword(query, null, limit).stream()
                .filter(f -> zone == null || f.getZone() == zone)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryFragment> searchFts(String query, int limit) {
        return searchFts(query, null, limit);
    }
}
