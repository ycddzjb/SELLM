package com.sellm.assessment.dto;

public class AssessmentResponse {
    private final Long id;
    private final double totalScore;
    private final String bandLabel;
    private final String interpretation;

    public AssessmentResponse(Long id, double totalScore, String bandLabel, String interpretation) {
        this.id = id;
        this.totalScore = totalScore;
        this.bandLabel = bandLabel;
        this.interpretation = interpretation;
    }

    public Long getId() { return id; }
    public double getTotalScore() { return totalScore; }
    public String getBandLabel() { return bandLabel; }
    public String getInterpretation() { return interpretation; }
}
