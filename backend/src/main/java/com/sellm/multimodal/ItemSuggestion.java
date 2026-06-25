package com.sellm.multimodal;

/** 多模态模型对单个量表指标的评分建议(仅建议,需老师确认)。 */
public class ItemSuggestion {
    private final String itemId;
    private final double suggestedScore;
    private final String reason;

    public ItemSuggestion(String itemId, double suggestedScore, String reason) {
        this.itemId = itemId;
        this.suggestedScore = suggestedScore;
        this.reason = reason;
    }

    public String getItemId() { return itemId; }
    public double getSuggestedScore() { return suggestedScore; }
    public String getReason() { return reason; }
}
