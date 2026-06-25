package com.sellm.teaching;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class CoursewareRepository {
    private final CoursewareMapper mapper;
    public CoursewareRepository(CoursewareMapper mapper) { this.mapper = mapper; }

    public Courseware save(Courseware c) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", c.getOwnerId());
        row.put("lessonPlanId", c.getLessonPlanId());
        row.put("disorderType", c.getDisorderType());
        row.put("aiDraft", c.getAiDraft());
        row.put("content", c.getContent());
        row.put("storageKey", c.getStorageKey());
        row.put("format", c.getFormat());
        row.put("status", c.getStatus());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public Courseware findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        Courseware c = new Courseware();
        c.setId(((Number) row.get("id")).longValue());
        c.setOwnerId(((Number) row.get("ownerId")).longValue());
        c.setLessonPlanId(((Number) row.get("lessonPlanId")).longValue());
        c.setDisorderType((String) row.get("disorderType"));
        c.setAiDraft((String) row.get("aiDraft"));
        c.setContent((String) row.get("content"));
        c.setStorageKey((String) row.get("storageKey"));
        c.setFormat((String) row.get("format"));
        c.setStatus((String) row.get("status"));
        return c;
    }

    public void update(Courseware c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", c.getId());
        row.put("content", c.getContent());
        row.put("aiDraft", c.getAiDraft());
        row.put("storageKey", c.getStorageKey());
        row.put("status", c.getStatus());
        mapper.update(row);
    }
}
