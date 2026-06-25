package com.sellm.assessment.media.dto;

/** 媒体识别建议项(对应 ItemSuggestion,出网层)。 */
public class SuggestionResponse {
    private final String itemId;
    private final double suggestedScore;
    private final String reason;

    public SuggestionResponse(String itemId, double suggestedScore, String reason) {
        this.itemId = itemId;
        this.suggestedScore = suggestedScore;
        this.reason = reason;
    }

    public String getItemId() { return itemId; }
    public double getSuggestedScore() { return suggestedScore; }
    public String getReason() { return reason; }
}
