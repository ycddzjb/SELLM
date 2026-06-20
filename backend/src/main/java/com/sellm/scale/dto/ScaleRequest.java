package com.sellm.scale.dto;

import java.util.List;

/** 创建/更新量表请求(整体提交:头 + 题目 + 分段)。 */
public class ScaleRequest {
    private String scaleId;   // 创建时必填;更新时以路径 {scaleId} 为准
    private String name;
    private String version;
    private String disorderType;
    private String description;
    private List<ScaleItemDto> items;
    private List<ScoreBandDto> bands;

    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ScaleItemDto> getItems() { return items; }
    public void setItems(List<ScaleItemDto> items) { this.items = items; }
    public List<ScoreBandDto> getBands() { return bands; }
    public void setBands(List<ScoreBandDto> bands) { this.bands = bands; }
}
