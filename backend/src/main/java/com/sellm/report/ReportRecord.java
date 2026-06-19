package com.sellm.report;

public class ReportRecord {
    private Long id;
    private Long assessmentId;
    private Long childId;
    private String draft;
    private String finalizedContent;
    private String status;   // DRAFT / FINALIZED

    public ReportRecord() {}

    public ReportRecord(Long id, Long assessmentId, Long childId, String draft,
                        String finalizedContent, String status) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.childId = childId;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
