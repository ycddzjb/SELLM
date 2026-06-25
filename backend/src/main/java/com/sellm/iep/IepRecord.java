package com.sellm.iep;

public class IepRecord {
    private Long id;
    private Long reportId;
    private Long diagnosisId;
    private Long childId;
    private String draft;
    private String finalizedContent;
    private String status;

    public IepRecord() {}

    public IepRecord(Long id, Long reportId, Long childId, String draft,
                     String finalizedContent, String status) {
        this(id, reportId, null, childId, draft, finalizedContent, status);
    }

    public IepRecord(Long id, Long reportId, Long diagnosisId, Long childId, String draft,
                     String finalizedContent, String status) {
        this.id = id;
        this.reportId = reportId;
        this.diagnosisId = diagnosisId;
        this.childId = childId;
        this.draft = draft;
        this.finalizedContent = finalizedContent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(Long diagnosisId) { this.diagnosisId = diagnosisId; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getDraft() { return draft; }
    public void setDraft(String draft) { this.draft = draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public void setFinalizedContent(String finalizedContent) { this.finalizedContent = finalizedContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
