package com.sellm.training.dto;

public class CreateCycleRequest {
    private Long childId;
    private Long diagnosisId;
    private Long iepId;
    private String title;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(Long diagnosisId) { this.diagnosisId = diagnosisId; }
    public Long getIepId() { return iepId; }
    public void setIepId(Long iepId) { this.iepId = iepId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
