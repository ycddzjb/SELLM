package com.sellm.scale;

import java.util.List;

public class Scale {
    private final String scaleId;
    private final String name;     // 如 CARS
    private final String version;
    private final String disorderType; // 障碍品类(对应 DisorderType 枚举名),可为 null
    private final String description;  // 量表简介,可为 null
    private final List<ScaleItem> items;
    private final ScoringRule scoringRule; // 可为 null,表示规则缺失

    public Scale(String scaleId, String name, String version,
                 List<ScaleItem> items, ScoringRule scoringRule) {
        this(scaleId, name, version, null, null, items, scoringRule);
    }

    public Scale(String scaleId, String name, String version,
                 String disorderType, String description,
                 List<ScaleItem> items, ScoringRule scoringRule) {
        this.scaleId = scaleId;
        this.name = name;
        this.version = version;
        this.disorderType = disorderType;
        this.description = description;
        this.items = items;
        this.scoringRule = scoringRule;
    }

    public String getScaleId() { return scaleId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDisorderType() { return disorderType; }
    public String getDescription() { return description; }
    public List<ScaleItem> getItems() { return items; }
    public ScoringRule getScoringRule() { return scoringRule; }
}
