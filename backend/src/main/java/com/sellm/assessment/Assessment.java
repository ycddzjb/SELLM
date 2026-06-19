package com.sellm.assessment;

public class Assessment {
    private Long id;
    private Long childId;
    private String scaleId;
    private double totalScore;
    private String bandLabel;
    private String interpretation;

    public Assessment() {}

    public Assessment(Long id, Long childId, String scaleId, double totalScore,
                      String bandLabel, String interpretation) {
        this.id = id;
        this.childId = childId;
        this.scaleId = scaleId;
        this.totalScore = totalScore;
        this.bandLabel = bandLabel;
        this.interpretation = interpretation;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public double getTotalScore() { return totalScore; }
    public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
    public String getBandLabel() { return bandLabel; }
    public void setBandLabel(String bandLabel) { this.bandLabel = bandLabel; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
}
