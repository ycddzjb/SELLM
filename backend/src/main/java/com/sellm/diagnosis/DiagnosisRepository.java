package com.sellm.diagnosis;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DiagnosisRepository {

    private final DiagnosisMapper mapper;

    public DiagnosisRepository(DiagnosisMapper mapper) {
        this.mapper = mapper;
    }

    public Diagnosis save(Diagnosis d) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", d.getChildId());
        row.put("ownerId", d.getOwnerId());
        row.put("scaleId", d.getScaleId());
        row.put("inputSummary", d.getInputSummary());
        row.put("dimensions", d.getDimensions());
        row.put("draft", d.getDraft());
        row.put("finalizedContent", d.getFinalizedContent());
        row.put("status", d.getStatus());
        mapper.insert(row);
        d.setId(((Number) row.get("id")).longValue());
        return d;
    }

    public Diagnosis findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : toDiagnosis(row);
    }

    public List<Diagnosis> listByChild(Long childId) {
        List<Diagnosis> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChildId(childId)) {
            out.add(toDiagnosis(row));
        }
        return out;
    }

    /** 回写 AI 产出的结构化维度 + 报告草案。 */
    public void updateResult(Long id, String dimensions, String draft) {
        mapper.updateResult(id, dimensions, draft);
    }

    /** 教师编辑草案(仅 DRAFT)。 */
    public void updateDraft(Long id, String draft) {
        mapper.updateDraftEdited(id, draft);
    }

    /** 定稿(仅 DRAFT → FINALIZED)。 */
    public boolean finalizeDiagnosis(Long id, String content) {
        if (mapper.findById(id) == null) return false;
        mapper.updateFinalized(id, content);
        return true;
    }

    private Diagnosis toDiagnosis(Map<String, Object> row) {
        return new Diagnosis(
            ((Number) row.get("id")).longValue(),
            ((Number) row.get("childId")).longValue(),
            row.get("ownerId") == null ? null : ((Number) row.get("ownerId")).longValue(),
            (String) row.get("scaleId"),
            (String) row.get("inputSummary"),
            (String) row.get("dimensions"),
            (String) row.get("draft"),
            (String) row.get("finalizedContent"),
            (String) row.get("status"));
    }
}
