package com.sellm.aids;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class GeneratedAssetRepository {
    private final GeneratedAssetMapper mapper;
    public GeneratedAssetRepository(GeneratedAssetMapper mapper) { this.mapper = mapper; }

    public GeneratedAsset save(GeneratedAsset a) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", a.getOwnerId());
        row.put("type", a.getType());
        row.put("prompt", a.getPrompt());
        row.put("storageKey", a.getStorageKey());
        row.put("taskId", a.getTaskId());
        row.put("status", a.getStatus());
        row.put("error", a.getError());
        mapper.insert(row);
        a.setId(((Number) row.get("id")).longValue());
        return a;
    }

    public GeneratedAsset findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<GeneratedAsset> listByOwner(Long ownerId) {
        List<GeneratedAsset> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    public void update(GeneratedAsset a) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", a.getId());
        row.put("storageKey", a.getStorageKey());
        row.put("taskId", a.getTaskId());
        row.put("status", a.getStatus());
        row.put("error", a.getError());
        mapper.update(row);
    }

    private GeneratedAsset fromRow(Map<String, Object> row) {
        GeneratedAsset a = new GeneratedAsset();
        a.setId(((Number) row.get("id")).longValue());
        a.setOwnerId(((Number) row.get("ownerId")).longValue());
        a.setType((String) row.get("type"));
        a.setPrompt((String) row.get("prompt"));
        a.setStorageKey((String) row.get("storageKey"));
        a.setTaskId((String) row.get("taskId"));
        a.setStatus((String) row.get("status"));
        a.setError((String) row.get("error"));
        return a;
    }
}
