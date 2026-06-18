package com.sellm.scale;

public class ScaleItem {
    private final String itemId;
    private final String stem;     // 题干
    private final String dimension; // 维度

    public ScaleItem(String itemId, String stem, String dimension) {
        this.itemId = itemId;
        this.stem = stem;
        this.dimension = dimension;
    }

    public String getItemId() { return itemId; }
    public String getStem() { return stem; }
    public String getDimension() { return dimension; }
}
