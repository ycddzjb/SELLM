package com.sellm.diagnosis;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DiagnosisMediaRepository {

    private final DiagnosisMediaMapper mapper;

    public DiagnosisMediaRepository(DiagnosisMediaMapper mapper) {
        this.mapper = mapper;
    }

    public DiagnosisMedia save(DiagnosisMedia m) {
        Map<String, Object> row = new HashMap<>();
        row.put("diagnosisId", m.getDiagnosisId());
        row.put("mediaType", m.getMediaType());
        row.put("objectKey", m.getObjectKey());
        row.put("transcript", m.getTranscript());
        row.put("noteText", m.getNoteText());
        mapper.insert(row);
        m.setId(((Number) row.get("id")).longValue());
        return m;
    }

    public List<DiagnosisMedia> listByDiagnosis(Long diagnosisId) {
        List<DiagnosisMedia> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByDiagnosisId(diagnosisId)) {
            out.add(new DiagnosisMedia(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("diagnosisId")).longValue(),
                (String) row.get("mediaType"),
                (String) row.get("objectKey"),
                (String) row.get("transcript"),
                (String) row.get("noteText")));
        }
        return out;
    }
}
