package com.sellm.scale.dto;

/** 分段 DTO(创建/更新与详情共用)。 */
public class ScoreBandDto {
    private double lowerBound;
    private double upperBound;
    private String label;
    private String interpretation;

    public ScoreBandDto() {}

    public ScoreBandDto(double lowerBound, double upperBound, String label, String interpretation) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.label = label;
        this.interpretation = interpretation;
    }

    public double getLowerBound() { return lowerBound; }
    public void setLowerBound(double lowerBound) { this.lowerBound = lowerBound; }
    public double getUpperBound() { return upperBound; }
    public void setUpperBound(double upperBound) { this.upperBound = upperBound; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
}
