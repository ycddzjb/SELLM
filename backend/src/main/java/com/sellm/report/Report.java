package com.sellm.report;

public class Report {
    private final String childName;
    private final String draft;       // AI 生成草稿(已还原身份信息)
    private String finalizedContent;  // 老师定稿内容
    private ReportStatus status;

    public Report(String childName, String draft) {
        this.childName = childName;
        this.draft = draft;
        this.status = ReportStatus.DRAFT;
    }

    public void finalizeReport(String content) {
        this.finalizedContent = content;
        this.status = ReportStatus.FINALIZED;
    }

    public String getChildName() { return childName; }
    public String getDraft() { return draft; }
    public String getFinalizedContent() { return finalizedContent; }
    public ReportStatus getStatus() { return status; }
}
