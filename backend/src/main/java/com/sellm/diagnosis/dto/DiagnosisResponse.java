package com.sellm.diagnosis.dto;

import com.sellm.diagnosis.Diagnosis;

public class DiagnosisResponse {
    private Long id;
    private Long childId;
    private String scaleId;
    private String inputSummary;
    private String dimensions;
    private String draft;
    private String finalizedContent;
    private String status;

    public static DiagnosisResponse of(Diagnosis d) {
        DiagnosisResponse r = new DiagnosisResponse();
        r.id = d.getId();
        r.childId = d.getChildId();
        r.scaleId = d.getScaleId();
        r.inputSummary = d.getInputSummary();
        r.dimensions = d.getDimensions();
        r.draft = d.getDraft();
        r.finalizedContent = d.getFinalizedContent();
        r.status = d.getStatus();
        return r;
    }

    public Long getId() { return id; }
    public Long getChildId() { return childId; }
    public String getScaleId() { return scaleId; }
    public String getInputSummary() { return inputSummary; }
    public String getDimensions() { return dimensions; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public String getStatus() { return status; }
}
