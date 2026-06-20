package com.sellm.scale;

import com.sellm.common.DisorderType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** 列表用:仅头信息(不含 items/bands)。 */
    public List<Scale> listAll() {
        return toHeadList(mapper.findAllScales());
    }

    /** 按品类筛选,仅头信息。 */
    public List<Scale> listByDisorderType(String disorderType) {
        return toHeadList(mapper.findScalesByDisorderType(disorderType));
    }

    @Transactional
    public void save(Scale scale) {
        DisorderType.validateCsv(scale.getDisorderType()); // 单值也按 csv 校验(空/合法码通过)
        Map<String, Object> head = new HashMap<>();
        head.put("scaleId", scale.getScaleId());
        head.put("name", scale.getName());
        head.put("version", scale.getVersion());
        head.put("disorderType", scale.getDisorderType());
        head.put("description", scale.getDescription());
        mapper.insertScale(head);
        insertItemsAndBands(scale);
    }

    @Transactional
    public void update(Scale scale) {
        DisorderType.validateCsv(scale.getDisorderType());
        Map<String, Object> head = new HashMap<>();
        head.put("scaleId", scale.getScaleId());
        head.put("name", scale.getName());
        head.put("version", scale.getVersion());
        head.put("disorderType", scale.getDisorderType());
        head.put("description", scale.getDescription());
        mapper.updateScale(head);
        // 整体替换 items/bands
        mapper.deleteItemsByScale(scale.getScaleId());
        mapper.deleteBandsByScale(scale.getScaleId());
        insertItemsAndBands(scale);
    }

    @Transactional
    public void deleteById(String scaleId) {
        mapper.deleteItemsByScale(scaleId);
        mapper.deleteBandsByScale(scaleId);
        mapper.deleteScale(scaleId);
    }

    private void insertItemsAndBands(Scale scale) {
        if (scale.getItems() != null) {
            for (ScaleItem item : scale.getItems()) {
                Map<String, Object> row = new HashMap<>();
                row.put("scaleId", scale.getScaleId());
                row.put("itemId", item.getItemId());
                row.put("stem", item.getStem());
                row.put("dimension", item.getDimension());
                row.put("sortOrder", item.getSortOrder());
                row.put("maxScore", item.getMaxScore());
                mapper.insertItem(row);
            }
        }
        if (scale.getScoringRule() != null) {
            for (ScoreBand band : scale.getScoringRule().getBands()) {
                Map<String, Object> row = new HashMap<>();
                row.put("scaleId", scale.getScaleId());
                row.put("lowerBound", band.getLower());
                row.put("upperBound", band.getUpper());
                row.put("label", band.getLabel());
                row.put("interpretation", band.getInterpretation());
                mapper.insertBand(row);
            }
        }
    }

    private List<Scale> toHeadList(List<Map<String, Object>> rows) {
        List<Scale> list = new ArrayList<>();
        for (Map<String, Object> head : rows) {
            list.add(new Scale(
                (String) head.get("scaleId"),
                (String) head.get("name"),
                (String) head.get("version"),
                (String) head.get("disorderType"),
                (String) head.get("description"),
                new ArrayList<>(), null));
        }
        return list;
    }
}
