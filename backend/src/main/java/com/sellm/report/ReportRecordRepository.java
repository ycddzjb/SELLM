package com.sellm.report;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportRecordRepository {

    private final ReportRecordMapper mapper;

    public ReportRecordRepository(ReportRecordMapper mapper) {
        this.mapper = mapper;
    }

    public ReportRecord save(ReportRecord r) {
        Map<String, Object> row = new HashMap<>();
        row.put("assessmentId", r.getAssessmentId());
        row.put("childId", r.getChildId());
        row.put("draft", r.getDraft());
        row.put("finalizedContent", r.getFinalizedContent());
        row.put("status", r.getStatus());
        mapper.insert(row);
        r.setId(((Number) row.get("id")).longValue());
        return r;
    }

    public ReportRecord findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new ReportRecord(((Number) row.get("id")).longValue(),
            ((Number) row.get("assessmentId")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("draft"), (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }

    public List<ReportRecord> listByChild(Long childId) {
        List<ReportRecord> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChildId(childId)) {
            out.add(new ReportRecord(((Number) row.get("id")).longValue(),
                ((Number) row.get("assessmentId")).longValue(),
                ((Number) row.get("childId")).longValue(),
                (String) row.get("draft"), (String) row.get("finalizedContent"),
                (String) row.get("status")));
        }
        return out;
    }

    public boolean finalizeReport(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }
}
