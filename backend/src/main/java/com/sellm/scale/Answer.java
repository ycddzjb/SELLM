package com.sellm.scale;

public class Answer {
    private final String itemId;
    private final double score;

    public Answer(String itemId, double score) {
        this.itemId = itemId;
        this.score = score;
    }

    public String getItemId() { return itemId; }
    public double getScore() { return score; }
}
