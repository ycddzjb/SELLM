package com.sellm.training;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TrainingCycleRepository {

    private final TrainingCycleMapper mapper;

    public TrainingCycleRepository(TrainingCycleMapper mapper) {
        this.mapper = mapper;
    }

    public TrainingCycle save(TrainingCycle c) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", c.getChildId());
        row.put("ownerId", c.getOwnerId());
        row.put("diagnosisId", c.getDiagnosisId());
        row.put("iepId", c.getIepId());
        row.put("seq", c.getSeq());
        row.put("title", c.getTitle());
        row.put("status", c.getStatus());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public TrainingCycle findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : toCycle(row);
    }

    public List<TrainingCycle> listByChild(Long childId) {
        List<TrainingCycle> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChildId(childId)) {
            out.add(toCycle(row));
        }
        return out;
    }

    /** 下一个 seq:该 child 现有最大 seq + 1(无则 1)。 */
    public int nextSeq(Long childId) {
        Integer max = mapper.findMaxSeqByChild(childId);
        return max == null ? 1 : max + 1;
    }

    /** 取该 child 指定 seq 的周期(对比上一周期用)。 */
    public TrainingCycle findByChildAndSeq(Long childId, int seq) {
        Map<String, Object> row = mapper.findByChildAndSeq(childId, seq);
        return row == null ? null : toCycle(row);
    }

    public void updateStatus(Long id, String status) {
        mapper.updateStatus(id, status);
    }

    public void updateIepId(Long id, Long iepId) {
        mapper.updateIepId(id, iepId);
    }

    private TrainingCycle toCycle(Map<String, Object> row) {
        return new TrainingCycle(
            ((Number) row.get("id")).longValue(),
            ((Number) row.get("childId")).longValue(),
            row.get("ownerId") == null ? null : ((Number) row.get("ownerId")).longValue(),
            row.get("diagnosisId") == null ? null : ((Number) row.get("diagnosisId")).longValue(),
            row.get("iepId") == null ? null : ((Number) row.get("iepId")).longValue(),
            ((Number) row.get("seq")).intValue(),
            (String) row.get("title"),
            (String) row.get("status"));
    }
}
