package com.sellm.scale;

public class ScoreBand {
    private final double lower;        // 含
    private final double upper;        // 含
    private final String label;        // 如 "轻-中度"
    private final String interpretation;

    public ScoreBand(double lower, double upper, String label, String interpretation) {
        this.lower = lower;
        this.upper = upper;
        this.label = label;
        this.interpretation = interpretation;
    }

    public boolean contains(double score) {
        return score >= lower && score <= upper;
    }

    public double getLower() { return lower; }
    public double getUpper() { return upper; }
    public String getLabel() { return label; }
    public String getInterpretation() { return interpretation; }
}
