package com.sellm.training;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TrainingRecordRepository {

    private final TrainingRecordMapper mapper;

    public TrainingRecordRepository(TrainingRecordMapper mapper) {
        this.mapper = mapper;
    }

    public TrainingRecord save(TrainingRecord r) {
        Map<String, Object> row = new HashMap<>();
        row.put("cycleId", r.getCycleId());
        row.put("mediaType", r.getMediaType());
        row.put("objectKey", r.getObjectKey());
        row.put("transcript", r.getTranscript());
        row.put("noteText", r.getNoteText());
        row.put("scores", r.getScores());
        mapper.insert(row);
        r.setId(((Number) row.get("id")).longValue());
        return r;
    }

    public List<TrainingRecord> listByCycle(Long cycleId) {
        List<TrainingRecord> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByCycleId(cycleId)) {
            out.add(new TrainingRecord(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("cycleId")).longValue(),
                (String) row.get("mediaType"),
                (String) row.get("objectKey"),
                (String) row.get("transcript"),
                (String) row.get("noteText"),
                (String) row.get("scores")));
        }
        return out;
    }
}
