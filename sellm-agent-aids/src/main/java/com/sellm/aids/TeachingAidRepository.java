package com.sellm.aids;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class TeachingAidRepository {
    private final TeachingAidMapper mapper;
    private final ObjectMapper json = new ObjectMapper();

    public TeachingAidRepository(TeachingAidMapper mapper) { this.mapper = mapper; }

    /** disorderType 为 null/空 → 全部教具;否则按障碍类型过滤。 */
    public List<TeachingAid> findByDisorderType(String disorderType) {
        List<TeachingAid> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByDisorderType(disorderType)) out.add(fromRow(row));
        return out;
    }

    private TeachingAid fromRow(Map<String, Object> row) {
        TeachingAid a = new TeachingAid();
        a.setId(((Number) row.get("id")).longValue());
        a.setName((String) row.get("name"));
        a.setDisorderTypes(parseTypes((String) row.get("disorderTypes")));
        a.setCategory((String) row.get("category"));
        a.setUsageGuide((String) row.get("usageGuide"));
        return a;
    }

    private List<String> parseTypes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
