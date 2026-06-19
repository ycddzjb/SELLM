package com.sellm.iep;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class IepRecordRepository {

    private final IepRecordMapper mapper;

    public IepRecordRepository(IepRecordMapper mapper) {
        this.mapper = mapper;
    }

    public IepRecord save(IepRecord r) {
        Map<String, Object> row = new HashMap<>();
        row.put("reportId", r.getReportId());
        row.put("childId", r.getChildId());
        row.put("draft", r.getDraft());
        row.put("finalizedContent", r.getFinalizedContent());
        row.put("status", r.getStatus());
        mapper.insert(row);
        r.setId(((Number) row.get("id")).longValue());
        return r;
    }

    public IepRecord findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new IepRecord(((Number) row.get("id")).longValue(),
            ((Number) row.get("reportId")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("draft"), (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }

    public List<IepRecord> listByChild(Long childId) {
        List<IepRecord> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChildId(childId)) {
            out.add(new IepRecord(((Number) row.get("id")).longValue(),
                ((Number) row.get("reportId")).longValue(),
                ((Number) row.get("childId")).longValue(),
                (String) row.get("draft"), (String) row.get("finalizedContent"),
                (String) row.get("status")));
        }
        return out;
    }

    public boolean finalizePlan(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }
}
