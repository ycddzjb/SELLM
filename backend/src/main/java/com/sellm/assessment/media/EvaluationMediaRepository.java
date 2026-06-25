package com.sellm.assessment.media;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class EvaluationMediaRepository {

    private final EvaluationMediaMapper mapper;

    public EvaluationMediaRepository(EvaluationMediaMapper mapper) {
        this.mapper = mapper;
    }

    public EvaluationMedia save(EvaluationMedia m) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", m.getChildId());
        row.put("scaleId", m.getScaleId());
        row.put("mediaType", m.getMediaType());
        row.put("objectKey", m.getObjectKey());
        row.put("noteText", m.getNoteText());
        row.put("uploaderUserId", m.getUploaderUserId());
        row.put("status", m.getStatus());
        mapper.insert(row);
        m.setId(((Number) row.get("id")).longValue());
        return m;
    }

    public EvaluationMedia findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : toEntity(row);
    }

    public List<EvaluationMedia> listByChild(Long childId) {
        return toList(mapper.findByChild(childId));
    }

    public List<EvaluationMedia> listByChildAndType(Long childId, String mediaType) {
        return toList(mapper.findByChildAndType(childId, mediaType));
    }

    public void updateStatus(Long id, String status) {
        mapper.updateStatus(id, status);
    }

    private List<EvaluationMedia> toList(List<Map<String, Object>> rows) {
        List<EvaluationMedia> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(toEntity(r));
        }
        return out;
    }

    private EvaluationMedia toEntity(Map<String, Object> row) {
        Long childId = row.get("childId") == null ? null : ((Number) row.get("childId")).longValue();
        Long uploader = row.get("uploaderUserId") == null ? null : ((Number) row.get("uploaderUserId")).longValue();
        return new EvaluationMedia(
            ((Number) row.get("id")).longValue(), childId,
            (String) row.get("scaleId"), (String) row.get("mediaType"),
            (String) row.get("objectKey"), (String) row.get("noteText"),
            uploader, (String) row.get("status"));
    }
}
