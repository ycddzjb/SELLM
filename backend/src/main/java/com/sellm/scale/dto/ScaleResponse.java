package com.sellm.scale.dto;

import com.sellm.scale.Scale;
import com.sellm.scale.ScaleItem;
import com.sellm.scale.ScoreBand;

import java.util.ArrayList;
import java.util.List;

/** 量表详情响应(含题目 + 分段)。列表场景 items/bands 为空。 */
public class ScaleResponse {
    private String scaleId;
    private String name;
    private String version;
    private String disorderType;
    private String description;
    private List<ScaleItemDto> items;
    private List<ScoreBandDto> bands;

    public static ScaleResponse of(Scale scale, boolean withDetail) {
        ScaleResponse r = new ScaleResponse();
        r.scaleId = scale.getScaleId();
        r.name = scale.getName();
        r.version = scale.getVersion();
        r.disorderType = scale.getDisorderType();
        r.description = scale.getDescription();
        r.items = new ArrayList<>();
        r.bands = new ArrayList<>();
        if (withDetail) {
            for (ScaleItem it : scale.getItems()) {
                r.items.add(new ScaleItemDto(it.getItemId(), it.getStem(), it.getDimension(),
                    it.getSortOrder(), it.getMaxScore()));
            }
            if (scale.getScoringRule() != null) {
                for (ScoreBand b : scale.getScoringRule().getBands()) {
                    r.bands.add(new ScoreBandDto(b.getLower(), b.getUpper(), b.getLabel(), b.getInterpretation()));
                }
            }
        }
        return r;
    }

    public String getScaleId() { return scaleId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDisorderType() { return disorderType; }
    public String getDescription() { return description; }
    public List<ScaleItemDto> getItems() { return items; }
    public List<ScoreBandDto> getBands() { return bands; }
}
