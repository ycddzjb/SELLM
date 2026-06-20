package com.sellm.scale;

public class ScaleItem {
    private final String itemId;
    private final String stem;     // 题干
    private final String dimension; // 维度
    private final int sortOrder;   // 渲染顺序
    private final double maxScore; // 每题最高分(前端 el-rate :max)

    public ScaleItem(String itemId, String stem, String dimension) {
        this(itemId, stem, dimension, 0, 4);
    }

    public ScaleItem(String itemId, String stem, String dimension, int sortOrder, double maxScore) {
        this.itemId = itemId;
        this.stem = stem;
        this.dimension = dimension;
        this.sortOrder = sortOrder;
        this.maxScore = maxScore;
    }

    public String getItemId() { return itemId; }
    public String getStem() { return stem; }
    public String getDimension() { return dimension; }
    public int getSortOrder() { return sortOrder; }
    public double getMaxScore() { return maxScore; }
}
