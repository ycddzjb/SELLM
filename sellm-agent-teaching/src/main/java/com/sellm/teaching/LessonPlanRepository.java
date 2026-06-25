package com.sellm.teaching;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class LessonPlanRepository {
    private final LessonPlanMapper mapper;
    public LessonPlanRepository(LessonPlanMapper mapper) { this.mapper = mapper; }

    public LessonPlan save(LessonPlan p) {
        Map<String, Object> row = toRow(p);
        mapper.insert(row);
        p.setId(((Number) row.get("id")).longValue());
        return p;
    }

    public LessonPlan findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<LessonPlan> listByOwner(Long ownerId) {
        List<LessonPlan> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    public void update(LessonPlan p) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", p.getId());
        row.put("content", p.getContent());
        row.put("aiDraft", p.getAiDraft());
        row.put("status", p.getStatus());
        mapper.update(row);
    }

    private Map<String, Object> toRow(LessonPlan p) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", p.getOwnerId());
        row.put("childId", p.getChildId());
        row.put("classId", p.getClassId());
        row.put("sourceIepId", p.getSourceIepId());
        row.put("scene", p.getScene());
        row.put("mode", p.getMode());
        row.put("disorderType", p.getDisorderType());
        row.put("aiDraft", p.getAiDraft());
        row.put("content", p.getContent());
        row.put("status", p.getStatus());
        return row;
    }

    private LessonPlan fromRow(Map<String, Object> row) {
        LessonPlan p = new LessonPlan();
        p.setId(((Number) row.get("id")).longValue());
        p.setOwnerId(((Number) row.get("ownerId")).longValue());
        p.setChildId(row.get("childId") == null ? null : ((Number) row.get("childId")).longValue());
        p.setClassId(row.get("classId") == null ? null : ((Number) row.get("classId")).longValue());
        p.setSourceIepId(row.get("sourceIepId") == null ? null : ((Number) row.get("sourceIepId")).longValue());
        p.setScene((String) row.get("scene"));
        p.setMode((String) row.get("mode"));
        p.setDisorderType((String) row.get("disorderType"));
        p.setAiDraft((String) row.get("aiDraft"));
        p.setContent((String) row.get("content"));
        p.setStatus((String) row.get("status"));
        return p;
    }
}
