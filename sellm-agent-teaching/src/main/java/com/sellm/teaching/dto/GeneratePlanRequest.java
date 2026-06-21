package com.sellm.teaching.dto;

public class GeneratePlanRequest {
    private Long childId;
    private Long classId;
    private Long sourceIepId;
    private String scene;
    private String mode;
    private String disorderType;
    private String iepContent;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getSourceIepId() { return sourceIepId; }
    public void setSourceIepId(Long sourceIepId) { this.sourceIepId = sourceIepId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDisorderType() { return disorderType; }
    public void setDisorderType(String disorderType) { this.disorderType = disorderType; }
    public String getIepContent() { return iepContent; }
    public void setIepContent(String iepContent) { this.iepContent = iepContent; }
}
