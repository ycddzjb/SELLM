package com.sellm.scale.dto;

/** 量表题目 DTO(创建/更新与详情共用)。 */
public class ScaleItemDto {
    private String itemId;
    private String stem;
    private String dimension;
    private int sortOrder;
    private double maxScore = 4;

    public ScaleItemDto() {}

    public ScaleItemDto(String itemId, String stem, String dimension, int sortOrder, double maxScore) {
        this.itemId = itemId;
        this.stem = stem;
        this.dimension = dimension;
        this.sortOrder = sortOrder;
        this.maxScore = maxScore;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public double getMaxScore() { return maxScore; }
    public void setMaxScore(double maxScore) { this.maxScore = maxScore; }
}
