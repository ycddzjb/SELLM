package com.sellm.training;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Map;

@Repository
public class StageEvalRepository {

    private final StageEvalMapper mapper;

    public StageEvalRepository(StageEvalMapper mapper) {
        this.mapper = mapper;
    }

    public StageEval save(StageEval e) {
        Map<String, Object> row = new HashMap<>();
        row.put("cycleId", e.getCycleId());
        row.put("childId", e.getChildId());
        row.put("scoresSummary", e.getScoresSummary());
        row.put("deltaSummary", e.getDeltaSummary());
        row.put("draft", e.getDraft());
        row.put("finalizedContent", e.getFinalizedContent());
        row.put("status", e.getStatus());
        mapper.insert(row);
        e.setId(((Number) row.get("id")).longValue());
        return e;
    }

    public StageEval findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : toEval(row);
    }

    /** 取某周期最新一份阶段评估(对比上一周期得分用)。 */
    public StageEval findByCycle(Long cycleId) {
        Map<String, Object> row = mapper.findByCycleId(cycleId);
        return row == null ? null : toEval(row);
    }

    public void updateDraft(Long id, String draft) {
        mapper.updateDraftEdited(id, draft);
    }

    public boolean finalizeEval(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }

    private StageEval toEval(Map<String, Object> row) {
        return new StageEval(
            ((Number) row.get("id")).longValue(),
            ((Number) row.get("cycleId")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("scoresSummary"),
            (String) row.get("deltaSummary"),
            (String) row.get("draft"),
            (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }
}
