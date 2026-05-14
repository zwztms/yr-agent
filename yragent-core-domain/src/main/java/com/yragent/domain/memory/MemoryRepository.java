package com.yragent.domain.memory;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository {

    void save(MemoryFragment fragment);

    Optional<MemoryFragment> findById(String id);

    void update(MemoryFragment fragment);

    void deleteById(String id);

    List<MemoryFragment> findByType(MemoryType type, int limit);

    List<MemoryFragment> findByTaskId(String taskId);

    List<MemoryFragment> findByTypeAndTaskId(MemoryType type, String taskId);

    List<MemoryFragment> searchByKeyword(String keyword, MemoryType type, int limit);

    int deleteOlderThan(int days);

    List<MemoryFragment> findByZone(MemoryZone zone, int limit);

    List<MemoryFragment> findByZoneAndTaskId(MemoryZone zone, String taskId);

    List<MemoryFragment> searchFts(String query, MemoryZone zone, int limit);

    List<MemoryFragment> searchFts(String query, int limit);
}
