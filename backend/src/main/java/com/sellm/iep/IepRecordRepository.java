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
        row.put("diagnosisId", r.getDiagnosisId());
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
        return row == null ? null : toRecord(row);
    }

    public List<IepRecord> listByChild(Long childId) {
        List<IepRecord> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChildId(childId)) {
            out.add(toRecord(row));
        }
        return out;
    }

    /** row→IepRecord;report_id/diagnosis_id 均可空(新旧链路二选一),null 安全转换。 */
    private IepRecord toRecord(Map<String, Object> row) {
        return new IepRecord(
            ((Number) row.get("id")).longValue(),
            asLong(row.get("reportId")),
            asLong(row.get("diagnosisId")),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("draft"), (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }

    private static Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    public boolean finalizePlan(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }
}
