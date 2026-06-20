package com.sellm.iep;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FamilyIepRepository {

    private final FamilyIepMapper mapper;

    public FamilyIepRepository(FamilyIepMapper mapper) {
        this.mapper = mapper;
    }

    public FamilyIep save(FamilyIep r) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", r.getChildId());
        row.put("parentUserId", r.getParentUserId());
        row.put("parentGoal", r.getParentGoal());
        row.put("draft", r.getDraft());
        row.put("finalizedContent", r.getFinalizedContent());
        row.put("status", r.getStatus());
        mapper.insert(row);
        r.setId(((Number) row.get("id")).longValue());
        return r;
    }

    public FamilyIep findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : toEntity(row);
    }

    public List<FamilyIep> listByChild(Long childId) {
        List<FamilyIep> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChild(childId)) {
            out.add(toEntity(row));
        }
        return out;
    }

    public void finalizePlan(Long id, String content) {
        mapper.updateFinalized(id, content);
    }

    private FamilyIep toEntity(Map<String, Object> row) {
        return new FamilyIep(
            ((Number) row.get("id")).longValue(),
            ((Number) row.get("childId")).longValue(),
            ((Number) row.get("parentUserId")).longValue(),
            (String) row.get("parentGoal"),
            (String) row.get("draft"),
            (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }
}
