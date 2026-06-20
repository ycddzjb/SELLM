package com.sellm.scale;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ScaleRepository {

    private final ScaleMapper mapper;

    public ScaleRepository(ScaleMapper mapper) {
        this.mapper = mapper;
    }

    public Scale findById(String scaleId) {
        Map<String, Object> head = mapper.findScaleById(scaleId);
        if (head == null) {
            return null;
        }

        List<ScaleItem> items = new ArrayList<>();
        for (Map<String, Object> row : mapper.findItems(scaleId)) {
            items.add(new ScaleItem(
                (String) row.get("itemId"),
                (String) row.get("stem"),
                (String) row.get("dimension"),
                row.get("sortOrder") == null ? 0 : ((Number) row.get("sortOrder")).intValue(),
                row.get("maxScore") == null ? 4 : ((Number) row.get("maxScore")).doubleValue()));
        }

        List<ScoreBand> bands = new ArrayList<>();
        for (Map<String, Object> row : mapper.findBands(scaleId)) {
            bands.add(new ScoreBand(
                ((Number) row.get("lowerBound")).doubleValue(),
                ((Number) row.get("upperBound")).doubleValue(),
                (String) row.get("label"),
                (String) row.get("interpretation")));
        }
        ScoringRule rule = bands.isEmpty() ? null : new ScoringRule(bands);

        return new Scale(
            (String) head.get("scaleId"),
            (String) head.get("name"),
            (String) head.get("version"),
            (String) head.get("disorderType"),
            (String) head.get("description"),
            items, rule);
    }
}
