package com.sellm.scale;

public class AssessmentResult {
    private final double totalScore;
    private final String bandLabel;
    private final String interpretation;

    public AssessmentResult(double totalScore, String bandLabel, String interpretation) {
        this.totalScore = totalScore;
        this.bandLabel = bandLabel;
        this.interpretation = interpretation;
    }

    public double getTotalScore() { return totalScore; }
    public String getBandLabel() { return bandLabel; }
    public String getInterpretation() { return interpretation; }
}
