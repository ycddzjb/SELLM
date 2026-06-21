package com.sellm.research;

import java.util.ArrayList;
import java.util.List;

public class ReliabilityResult {
    private Double alpha;         // null = 不可算
    private Double splitHalf;     // null = 不可算
    private double[] itemTotal;   // 每题一个(零方差项按策略处置)
    private int itemCount;
    private int subjectCount;
    private List<String> notes = new ArrayList<>();

    public Double getAlpha() { return alpha; }
    public void setAlpha(Double alpha) { this.alpha = alpha; }
    public Double getSplitHalf() { return splitHalf; }
    public void setSplitHalf(Double splitHalf) { this.splitHalf = splitHalf; }
    public double[] getItemTotal() { return itemTotal; }
    public void setItemTotal(double[] itemTotal) { this.itemTotal = itemTotal; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public int getSubjectCount() { return subjectCount; }
    public void setSubjectCount(int subjectCount) { this.subjectCount = subjectCount; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }
}
