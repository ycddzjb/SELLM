package com.sellm.research;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReliabilityCalcRepository {
    private final ReliabilityCalcMapper mapper;
    public ReliabilityCalcRepository(ReliabilityCalcMapper mapper) { this.mapper = mapper; }

    public ReliabilityCalc save(ReliabilityCalc c) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", c.getOwnerId());
        row.put("dataset", c.getDataset());
        row.put("method", c.getMethod());
        row.put("result", c.getResult());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public ReliabilityCalc findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<ReliabilityCalc> listByOwner(Long ownerId) {
        List<ReliabilityCalc> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    private ReliabilityCalc fromRow(Map<String, Object> row) {
        ReliabilityCalc c = new ReliabilityCalc();
        c.setId(((Number) row.get("id")).longValue());
        c.setOwnerId(((Number) row.get("ownerId")).longValue());
        c.setDataset((String) row.get("dataset"));
        c.setMethod((String) row.get("method"));
        c.setResult((String) row.get("result"));
        return c;
    }
}
