package com.sellm.research.dto;

public class ReliabilityRequest {
    private String method;     // 可空
    private double[][] scores;
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public double[][] getScores() { return scores; }
    public void setScores(double[][] scores) { this.scores = scores; }
}
