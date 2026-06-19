package com.sellm.assessment;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AssessmentRepository {

    private final AssessmentMapper mapper;

    public AssessmentRepository(AssessmentMapper mapper) {
        this.mapper = mapper;
    }

    public Assessment save(Assessment a) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", a.getChildId());
        row.put("scaleId", a.getScaleId());
        row.put("totalScore", a.getTotalScore());
        row.put("bandLabel", a.getBandLabel());
        row.put("interpretation", a.getInterpretation());
        mapper.insert(row);
        a.setId(((Number) row.get("id")).longValue());
        return a;
    }

    public Assessment findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new Assessment(((Number) row.get("id")).longValue(),
            ((Number) row.get("childId")).longValue(),
            (String) row.get("scaleId"),
            ((Number) row.get("totalScore")).doubleValue(),
            (String) row.get("bandLabel"),
            (String) row.get("interpretation"));
    }

    public List<Assessment> listByChild(Long childId) {
        List<Assessment> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByChildId(childId)) {
            out.add(new Assessment(((Number) row.get("id")).longValue(),
                ((Number) row.get("childId")).longValue(),
                (String) row.get("scaleId"),
                ((Number) row.get("totalScore")).doubleValue(),
                (String) row.get("bandLabel"),
                (String) row.get("interpretation")));
        }
        return out;
    }
}
